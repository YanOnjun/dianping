package com.hmdp.utils.reiis;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author : 上春
 * @create 2023/8/6 18:36
 */
public class RedisLock implements ILock{

    private final String key;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    }

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
        // 两步操作采用lua脚本保证原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), getThreadId());
    }

    private String getThreadId() {
        // since 19
        //return String.valueOf(Thread.currentThread().threadId());
        return ID_PREFIX + Thread.currentThread().getId();
    }
}
