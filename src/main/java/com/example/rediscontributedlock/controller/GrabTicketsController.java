package com.example.rediscontributedlock.controller;

import io.netty.util.internal.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/grabTickets/api")
public class GrabTicketsController {
    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private final String TICKET_KEY = "tickets:quantity";

    private final String KEY = "ContributeLock";

    @GetMapping("/setTicketsQuantity")
    public void setTicketsQuantity() throws Exception {

        int quantity = 10;

        redisTemplate.opsForValue().set(TICKET_KEY, String.valueOf(quantity));

    }

    @GetMapping("/getTicket")
    public void getTicket() throws Exception {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        long threadId = Thread.currentThread().getId();
        log.info("用户{}已進入", threadId);
        redissonClient.getFairLock(KEY);
        RLock lock = redissonClient.getLock(KEY);
        try {
            lock.lock();
            String valueStr = (String) redisTemplate.opsForValue().get(TICKET_KEY);
            LocalDateTime currentDateTime = LocalDateTime.now();
            String startTime = currentDateTime.format(formatter);
           // log.info("用户{}取得鎖{}", threadId, startTime);

            // 進行業務邏輯處理
            if (valueStr != null){
                int value = Integer.parseInt(valueStr);
                if(value > 0){
                    value--;
                    valueStr = String.valueOf(value);
                    redisTemplate.opsForValue().set(TICKET_KEY,valueStr);
                    log.info("用戶{}取得票，目前票數{}，剩餘票數{}",threadId,value,valueStr);
                }else {
                    log.info("用戶{}==無法==取得票，剩餘票數{}",threadId,value);
                }

            }else{
                log.info("redis無票數的緩存");
            }

            LocalDateTime currentDateTime2 = LocalDateTime.now();
            String logicTime = currentDateTime2.format(formatter);
          //  log.info("用户{}完成業務邏輯:{}", threadId, logicTime);

        }catch (Exception ex){
            log.error("系統錯誤:{}",ex.getMessage());
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
