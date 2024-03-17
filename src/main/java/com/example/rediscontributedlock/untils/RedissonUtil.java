package com.example.rediscontributedlock.untils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author vanliou
 */
@Slf4j
public class RedissonUtil {
    public static boolean lock(RedissonClient redissonClient, String key, Long expireTimeInSeconds){

        try {
            // 取得 RBucket 實例，並設定鍵值對
            RBucket<String> bucket = redissonClient.getBucket(key);
            // 取得目前執行緒的ID
            long threadId = Thread.currentThread().getId();
            String uuid = UUID.randomUUID().toString();
            String value = String.format("%s%s", uuid, threadId);
            System.out.println("value:"+value);
            bucket.set(value);
            bucket.expire(expireTimeInSeconds, TimeUnit.SECONDS);
            // 取得鎖定對象
            RLock lock = redissonClient.getLock(key);
            // 鎖定
            lock.lock();
        } catch (Exception ex) {
            log.error("設定鎖定值失敗: {}", ex.getMessage());
            return false;
        }
        return true;
    }
}
