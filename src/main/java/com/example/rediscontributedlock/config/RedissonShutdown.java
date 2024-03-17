package com.example.rediscontributedlock.config;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Component
public class RedissonShutdown {

    @Autowired
    private RedissonClient redissonClient;
    @PreDestroy
    public void preDestroy() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }
}
