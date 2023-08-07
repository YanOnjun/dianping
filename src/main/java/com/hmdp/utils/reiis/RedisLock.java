package com.hmdp.utils.reiis;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author : 上春
 * @create 2023/8/6 18:36
 */
public class RedisLock implements ILock{

    private final String key;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLock(String key, StringRedisTemplate stringRedisTemplate) {
        this.key = key;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeout) {
        String threadId = String.valueOf(Thread.currentThread().getId());
        // since 19
        //String threadId = String.valueOf(Thread.currentThread().threadId());
        // 互斥设置值
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeout, TimeUnit.SECONDS);
        // 存在直接返回
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
        // 从 redis 中获取数据
        stringRedisTemplate.delete(key);
    }
}
