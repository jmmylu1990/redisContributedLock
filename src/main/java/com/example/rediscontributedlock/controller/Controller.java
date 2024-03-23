package com.example.rediscontributedlock.controller;


import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author vanliou
 */
@Slf4j
@RestController
@RequestMapping("/rediscontributedlock/api")
public class Controller<T> {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final String KEY = "ContributeLock";

    /**
     * @author vanliou
     * @method 分散式鎖，V1。
     * @description 多個並發執行緒都去 Redis 中申請鎖，也就是執行 setnx 指令，假設執行緒 A 執行成功，表示當前執行緒 A 獲得了。
     * 其他執行緒執行 setnx 指令都會是失敗的，所以需要等待執行緒 A 釋放鎖定。
     * 執行緒 A 執行完自己的業務後，刪除鎖定。
     * 其他執行緒繼續搶佔鎖，也就是執行 setnx 指令。 因為執行緒 A 已經刪除了鎖，所以又有其他執行緒可以搶占到鎖了。
     * 缺點：從技術的角度看：setnx 佔鎖成功，業務代碼出現異常或伺服器宕機，沒有執行刪除鎖的邏輯，就造成了死鎖。
     */
    @GetMapping("/getLockV1")
    public void getLockV1() throws Exception {
        try {
            //Redis SETNX,SETNX 是 set If not exist 的簡寫。 意思是當 key 不存在時，設定 key 的值，存在時，什麼都不做。
            Boolean lock = redisTemplate.opsForValue().setIfAbsent(KEY, 123);

            if (Boolean.TRUE.equals(lock)) {
                log.info("用戶{}順利取到鎖", Thread.currentThread().getId());
                //假設業務邏輯三秒
                log.info("{} 取到鎖，執行業務邏輯", Thread.currentThread().getId());
                Thread.sleep(3000);
                log.info("{} 業務邏輯執行完成", Thread.currentThread().getId());
                redisTemplate.delete(KEY);
                log.info("{}釋放鎖", Thread.currentThread().getId());
            } else {
                //因為該程式存在遞歸調用，可能會導致棧stack溢位。故此處理。
                Thread.sleep(1000);
                this.getLockV1();
                log.info("{} 嘗試取鎖", Thread.currentThread().getId());
            }
        } catch (Exception ex) {
            log.error("執行失敗: {}", ex.getMessage());
        }
    }

    /**
     * @author vanliou
     * @method 分散式鎖，V2。
     * @description V1方案看似解決了線程異常或伺服器宕機造成的鎖未釋放的問題，但還是有其他問題：
     * 因為佔鎖和設定過期時間是分兩步驟執行的，所以如果在這兩步之間發生了異常，則鎖的過期時間根本就沒有設定成功。
     * 所以和V1有一樣的問題：鎖永遠不能過期。
     * 缺點：沒有原子性：多條指令要嘛都成功執行，要嘛都不執行。
     */
    @GetMapping("/getLockV2")
    public void getLockV2() throws Exception {

        //Redis SETNX,SETNX 是 set If not exist 的簡寫。 意思是當 key 不存在時，設定 key 的值，存在時，什麼都不做。
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(KEY, 123);
        if (Boolean.TRUE.equals(lock)) {
            log.info("用戶{}順利取到鎖", Thread.currentThread().getId());
            //在 10s 以後，自動清理 lock
            redisTemplate.expire(KEY, 10, TimeUnit.SECONDS);
            Thread.sleep(3000);
            log.info("{}執行完業務邏輯", Thread.currentThread().getId());
            redisTemplate.delete(KEY);
        } else {
            //因為該程式存在遞歸調用，可能會導致棧stack溢位。故此處理。
            Thread.sleep(1000);
            this.getLockV2();
            log.info("{}嘗試取鎖", Thread.currentThread().getId());
        }

    }

    /**
     * @author vanliou
     * @method 分散式鎖，V3。
     * @description V3方案和V2方案的不同之處：取得鎖的時候，也需要設定鎖的過期時間，這是一個原子操作，要嘛都成功執行，要嘛都不執行。
     * 缺點：
     * 1.用戶 A 搶佔鎖
     * 使用者 A 先搶佔到了鎖，並設定了這個鎖 10 秒以後自動開鎖，鎖的編號是 123。10 秒以後，A 還在執行任務，此時鎖被自動開啟了。
     * <p>
     * 2.用戶 B 搶佔鎖
     * 使用者 B 看到房間的鎖打開了，於是搶佔到了鎖，設定鎖的編號為 123，並設定了過期時間 10 秒。
     * 因房間內只允許一個使用者執行任務，所以使用者 A 和 使用者 B 執行任務 產生了衝突。
     * 使用者 A 在 15 s 後，完成了任務，此時 使用者 B 還在執行任務。
     * 使用者 A 主動打開了編號為 123 的鎖。
     * 使用者 B 還在執行任務，發現鎖已經被打開了。
     * 使用者 B 非常生氣： 我還沒執行完任務呢，鎖怎麼開了？
     * <p>
     * 3.用戶 C 搶佔鎖
     * 使用者 B 的鎖被 A 主動打開後，A 離開房間，B 還在執行任務。
     * 使用者 C 搶占到鎖，C 開始執行任務。
     * 因房間內只允許一個使用者執行任務，所以使用者 B 和 使用者 C 執行任務產生了衝突。
     * 從上面的案例中我們可以知道，因為用戶 A 處理任務所需的時間大於鎖自動清理（開鎖）的時間，所以在自動開鎖後，又有其他用戶搶占到了鎖。 當使用者 A 完成任務後，會把其他使用者搶佔的鎖主動開啟。
     * 這裡為什麼會打開別人的鎖？ 因為鎖的編號都叫做“123”，用戶A 只認鎖編號，看見編號為“123” 的鎖就開，結果把用戶B 的鎖打開了，此時用戶B 還未執行完任務，當然生氣了 。
     */
    @GetMapping("/getLockV3")
    public void getLockV3() throws Exception {
        //Redis SETNX,SETNX 是 set If not exist 的簡寫。 意思是當 key 不存在時，設定 key 的值，存在時，什麼都不做。
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(KEY, "123", 10, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(lock)) {
            log.info("用戶{}順利取到鎖", Thread.currentThread().getId());
            //在執行商業邏輯
            Thread.sleep(3000);
            log.info("用戶{}執行完業務邏輯", Thread.currentThread().getId());
            redisTemplate.delete(KEY);
        } else {
            Thread.sleep(1000);
            log.info("用戶{}嘗試取鎖", Thread.currentThread().getId());
            this.getLockV3();
        }
    }

    /**
     * @author vanliou
     * @method 分散式鎖，V4。
     * @description 上面的V3方案的缺陷也很好解決，給每個鎖設置不同的編號不就好了～
     * B 搶佔的鎖是藍色的，和 A 搶占到綠色鎖不一樣。 這樣就不會被 A 打開了。
     * 設定鎖的過期時間時，還需要設定唯一編號。
     * 主動刪除鎖的時候，需要判斷鎖的編號是否和設定的一致，如果一致，則認為是自己設定的鎖，可以進行主動刪除。
     * 1. 產生隨機唯一 id，為鎖加上唯一值。
     * 2. 搶佔鎖，並設定過期時間為 10 s，且鎖具有隨機唯一 id。
     * 3. 搶佔成功，執行業務。
     * 4. 執行完業務後，取得目前鎖的值。
     * 5. 如果鎖的值和設定的值相等，則清理自己的鎖。
     * <p>
     * 缺點：V4的缺陷
     * 上面的方案看似完美，但還是有問題：第 4 步和第 5 步並不是原子性的。
     * 時刻：0s。 線程 A 搶占到了鎖。
     * 時刻：9.5s。 執行緒 A 向 Redis 查詢目前 key 的值。
     * 時刻：10s。 鎖自動過期。
     * 時刻：11s。 線程 B 搶占到鎖。
     * 時刻：12s。 執行緒 A 在查詢途中耗時長，終於拿多鎖的值。
     * 時刻：13s。 線程 A 還是拿自己設定的鎖的值和回傳的值比較，值是相等的，清理鎖，但是這個鎖其實是線程 B 搶佔的鎖。
     */
    @GetMapping("/getLockV4")
    public void getLockV4() throws Exception {
        // 1.產生唯一 id
        String uuid = UUID.randomUUID().toString();

        Long threadId = Thread.currentThread().getId();
        //Redis SETNX,SETNX 是 set If not exist 的簡寫。 意思是當 key 不存在時，設定 key 的值，存在時，什麼都不做。
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(KEY, uuid, 10, TimeUnit.SECONDS);
        log.info("用戶{}上鎖:{}", threadId, lock);
        if (Boolean.TRUE.equals(lock)) {
            log.info("用戶{}順利取到鎖", threadId);
            //商業邏輯時間
            Thread.sleep(15000);
            log.info("用戶{}執行完業務邏輯", threadId);
            // 取得目前鎖的值
            String lockValue = Objects.requireNonNull(redisTemplate.opsForValue().get(KEY)).toString();
            //如果鎖的值和設定的值相等，則清理自己的鎖
            if (lockValue.equals(uuid)) {
                redisTemplate.delete(KEY);
            }
        } else {
            Thread.sleep(1000);
            log.info("用戶{}嘗試取鎖", threadId);
            this.getLockV4();
        }
    }

    /**
     * @author vanliou
     * @method 分散式鎖，V5。
     * @description V5方案 A 查詢鎖和刪除鎖的邏輯不是原子性的，所以將查詢鎖和刪除鎖這兩個步驟作為原子指令操作就可以了。
     * <p>
     * 這是 Lua 腳本，用於在 Redis 中執行一系列操作。讓我們逐步解釋這段腳本的作用：
     * redis.call("get",KEYS[1])：這一行代表著從 Redis 中根據給定的鍵（key）獲取值。KEYS[1] 是一個 Lua 表（table），
     * 它包含了在調用這個 Lua 腳本時傳遞的所有鍵的列表，而 KEYS[1] 則是列表中的第一個鍵。
     * == ARGV[1]：這部分是一個條件判斷，它用於檢查從 Redis 中獲取的值是否等於傳遞給腳本的第一個參數（ARGV[1]）。
     * then：如果條件成立，即 Redis 中的值等於腳本的第一個參數，則執行接下來的操作。
     * redis.call("del",KEYS[1])：這一行代表著從 Redis 中刪除指定的鍵（KEYS[1]）。如果條件成立，則這一行的目的是刪除與腳本中給定鍵相對應的值。
     * else：如果條件不成立，即 Redis 中的值與腳本的第一個參數不相等，則執行接下來的操作。
     * return 0：這一行代表著在條件不成立時返回結果為 0。
     * 總的來說，這段 Lua 腳本的作用是檢查 Redis 中指定鍵的值是否與腳本的第一個參數相等，如果相等則刪除該鍵，否則返回 0。
     * <p>
     * 先定義腳本；用 redisTemplate.execute 方法執行腳本。
     * <p>
     * 缺點：一旦商業邏輯運作時間超過鎖的失效時間，會造成競爭物件的影響
     * A 用戶的商業邏輯實行完畢之前鎖的KEY失效，結果 B 用戶已經成立新的鎖，導致 A 用戶比對值出現失敗(值不對，或是沒有該KEY可以比對)。
     * 這種情況A的鎖需要續期過期時間。
     * V5是非專業的分散式鎖方案，使用Redisson 分散式鎖可解決這個部分。
     */
    @GetMapping("/getLockV5")
    public void getLockV5() throws Exception {
        // 1.產生唯一 id
        String uuid = UUID.randomUUID().toString();
        Long threadId = Thread.currentThread().getId();
        //Redis SETNX,SETNX 是 set If not exist 的簡寫。 意思是當 key 不存在時，設定 key 的值，存在時，什麼都不做。
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(KEY, uuid, 10, TimeUnit.SECONDS);
        log.info("用戶{}上鎖:{}", threadId, lock);
        if (Boolean.TRUE.equals(lock)) {
            log.info("用戶{}順利取到鎖", threadId);
            //商業邏輯時間
            Thread.sleep(5000);
            log.info("用戶{}執行完業務邏輯", threadId);
            // 取得目前鎖的值
            String lockValue = Objects.requireNonNull(redisTemplate.opsForValue().get(KEY)).toString();
            //如果鎖的值和設定的值相等，則清理自己的鎖
            if (lockValue.equals(uuid)) {
                // 腳本解鎖
                String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
                redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), List.of(KEY), uuid);
            }
        } else {
            Thread.sleep(1000);
            log.info("用戶{}嘗試取鎖", threadId);
            this.getLockV5();
        }
    }


    /**
     * @author vanliou
     * @method 分散式鎖，Redisson V1。
     * @description V1方案 如果 A 在等待的過程中，服務突然停了，那麼鎖會釋放嗎？ 如果不釋放的話，就會成為死鎖，阻塞了其他執行緒取得鎖。
     * 看門狗原理:
     * 如果負責儲存這個分散式鎖的 Redisson 節點宕機以後，而且這個鎖正好處於鎖住的狀態時，這個鎖會出現鎖死的狀態。
     * 為了避免這種情況的發生，Redisson 內部提供了一個監控鎖的看門狗，它的作用是在 Redisson 實例被關閉前，不斷的延長鎖的有效期。
     * 預設情況下，看門狗的檢查鎖的逾時時間是 30 秒鐘，也可以透過修改Config.lockWatchdogTimeout來另行指定。
     * 如果我們未制定 lock 的超時時間，就使用 30 秒作為看門狗的預設時間。 只要佔鎖成功，
     * 就會啟動定時任務：每隔 10 秒重新給鎖設定過期的時間，過期時間為 30 秒。
     * 當伺服器當機後，因為鎖的有效期是 30 秒，所以會在 30 秒內自動解鎖。 （30 秒等於宕機之前的鎖佔用時間+後續鎖佔用的時間）。
     * <p>
     * 我們也可以透過給鎖設定過期時間，讓其自動解鎖。
     * 如下所示，設定鎖 8 秒後自動過期。
     * lock.lock(8, TimeUnit.SECONDS);
     * 如果業務執行時間超過 8 秒，手動釋放鎖定將會報錯。
     * 所以我們如果設定了鎖的自動過期時間，執行業務的時間一定要小於鎖的自動過期時間，否則就會報錯。
     * 根據watchdog機制：假設lock.lock();尚未設定失效時間，redisson會開啟watchddog機制，目前redisson底層預設時間為30秒，
     * 商業邏輯為40秒，該lock的KEY會消失。但是因為watchdoh機制，只要該lock的邏輯還在執行。會每30/3=10秒，去延長該lock的key為ttl30秒。
     * 直到該lock的商業邏輯結束，自行解鎖或者該lock的KEY緩存時間結束。
     */
    @GetMapping("/getLockByRedissonV1")
    public void getLockByRedissonV1() throws InterruptedException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Long threadId = Thread.currentThread().getId();
        log.info("用户{}已進入", threadId);
        RLock lock = redissonClient.getLock(KEY);
        try {
            lock.lock();
            LocalDateTime currentDateTime = LocalDateTime.now();
            String startTime = currentDateTime.format(formatter);
            log.info("用户{}取得鎖{}", threadId, startTime);

            // 進行業務邏輯處理
            Thread.sleep(35000);
            LocalDateTime currentDateTime2 = LocalDateTime.now();
            String logicTime = currentDateTime2.format(formatter);
            log.info("用户{}完成業務邏輯:{}", threadId, logicTime);

        } finally {
            //確認redis上的鎖是否為該執行緒的鎖
            if (lock.isHeldByCurrentThread()) {
                LocalDateTime currentDateTime3 = LocalDateTime.now();
                String endTime = currentDateTime3.format(formatter);
                //確保鎖被當前執行緒持有時才釋放
                lock.unlock();
                log.info("用户{}成功釋放鎖{}", threadId, endTime);
            }
        }
    }


}
