package com.hmdp.utils.reiis;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author : 上春
 * @create 2023/8/6 18:36
 */
public class RedisLock implements ILock{

    private final String key;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String ID_PREFIX = UUID.randomUUID().toString(true);

    public RedisLock(String key, StringRedisTemplate stringRedisTemplate) {
        this.key = key;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeout) {
        String threadId = getThreadId();
        // 互斥设置值
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeout, TimeUnit.SECONDS);
        // 存在直接返回
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
        String threadId = getThreadId();
        // TODO 下面两步非原子
        String s = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.equals(threadId, s)){
            // 从 redis 中获取数据
            stringRedisTemplate.delete(key);
        }
        // 不是当前线程的锁 可能已经超时了
    }

    private String getThreadId() {
        // since 19
        //return String.valueOf(Thread.currentThread().threadId());
        return ID_PREFIX + Thread.currentThread().getId();
    }
}
