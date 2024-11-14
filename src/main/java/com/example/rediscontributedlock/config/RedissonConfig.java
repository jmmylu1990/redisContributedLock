package com.example.rediscontributedlock.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.redisson.spring.transaction.RedissonTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class RedissonConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 設置 JSON 編碼器
        config.setCodec(new JsonJacksonCodec(new ObjectMapper()));

        // 配置 Redis 單節點和連接池設置
        config.useSingleServer()
            .setAddress("redis://127.0.0.1:6379")
            .setConnectionPoolSize(64)              // 設置連接池大小
            .setConnectionMinimumIdleSize(16)       // 設置最小閒置連接數
            .setTimeout(3000);                      // 設置連接超時時間（毫秒）

        // 設置 Redisson 執行緒配置
        config.setThreads(16);
        config.setNettyThreads(32);

        return Redisson.create(config);
    }

    @Bean
    @Primary
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean
    public RedissonTransactionManager redissonTransactionManager(RedissonClient redissonClient) {
        return new RedissonTransactionManager(redissonClient);
    }


}
