package com.example.rediscontributedlock.controller;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/redEnvelope/api")
public class RedEnvelopesController {

    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    private final String RED_POCKET_KEY = "redEnvelope:list";
    private final String KEY = "ContributeLock";

    @Autowired
    private RedissonClient redissonClient;

    @GetMapping("/installRedEnvelopes")
    public void installRedEnvelopes() throws Exception {

        int size = 10;
        BigDecimal totalAmount = new BigDecimal(10000); // 红包总金额
        List<String> redEnvelopesList = generateRedEnvelopes(size, totalAmount);

        for (int i = 0; i < size; i++) {
            System.out.println("红包" + (i + 1) + ": " + redEnvelopesList.get(i) + "元");
        }

        // 将红包列表存储到 Redis 的 List 结构中
        redisTemplate.opsForList().rightPushAll(RED_POCKET_KEY, redEnvelopesList.toArray(new String[0]));
    }

    @GetMapping("/grabRedEnvelope")
    public void grabRedEnvelope() throws Exception {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        long threadId = Thread.currentThread().getId();
        log.info("用户{}已進入", threadId);
        redissonClient.getFairLock(KEY);
        RLock lock = redissonClient.getLock(KEY);
        try {
            lock.lock();
            Long listSize = redisTemplate.opsForList().size(RED_POCKET_KEY);

            // 進行業務邏輯處理
            if (listSize != null){
                if(listSize.compareTo(0L) > 0){
                    String stringValue = (String)redisTemplate.opsForList().index(RED_POCKET_KEY, -1);
                    if(stringValue != null){
                        BigDecimal lastElement = new BigDecimal(stringValue);
                        redisTemplate.opsForList().trim(RED_POCKET_KEY, 0, -2);
                        listSize--;
                        log.info("用戶{}取得紅包{}元，剩餘紅包數{}",threadId,lastElement,listSize);
                    }else {
                        log.info("用戶{}==無法==取得紅包，該紅包沒有值",threadId);
                    }

                }else {
                    log.info("用戶{}==無法==取得紅包，因為沒有紅包",threadId);
                }

            }else{
                log.info("redis無紅包數的緩存");
            }

        }catch (Exception ex){
           log.error("系統錯誤：{}",ex.getMessage());
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




    private static List<String> generateRedEnvelopes(int size, BigDecimal totalAmount) {
        Random random = new Random();
        List<String> redEnvelopeList = new ArrayList<>(size);

        BigDecimal remainingAmount = totalAmount;

        for (int i = 0; i < size - 1; i++) {
            // 產生一個 0 到剩餘總金額 / 剩餘紅包數量的隨機數作為當前紅包的金額
            BigDecimal amount = generateRandomAmount(remainingAmount, size - i, random);
            redEnvelopeList.add(amount.toString());
            remainingAmount = remainingAmount.subtract(amount);
        }

        // 最後一個紅包的金額為剩餘總金額
        redEnvelopeList.add(remainingAmount.toString());

        return redEnvelopeList;
    }

    private static BigDecimal generateRandomAmount(BigDecimal remainingAmount, int remainingPackets, Random random) {
        BigDecimal maxAmount = remainingAmount.divide(BigDecimal.valueOf(remainingPackets), 2, RoundingMode.DOWN);
        // 最小金額為1元
        BigDecimal minAmount = BigDecimal.ONE;

        // 產生一個介於最小金額和最大金額之間的隨機數字作為當前紅包的金額
        BigDecimal amount = minAmount.add(BigDecimal.valueOf(Math.random()).multiply(maxAmount.subtract(minAmount)));
        return amount.setScale(2, RoundingMode.DOWN); // 精确到小数点后两位
    }
}
