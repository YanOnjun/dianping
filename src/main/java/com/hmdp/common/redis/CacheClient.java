package com.hmdp.common.redis;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.common.constant.RedisConstants;
import com.hmdp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * redis设值取值工具类，封装了通过逻辑过期和互斥锁的获取防止缓存击穿缓存穿透的方法
 * 目前只是redis的string类型的操作
 *
 * @author : 上春
 * @create 2023/7/26 14:36
 */
@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 逻辑过期
     *
     * @param key
     * @param value
     * @param time  过期时间
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 获取数据 防止缓存穿透的方法
     * @param id
     * @param type
     * @return
     * @param <T> 返回值类型
     */
    public <T, ID> T queryWithPassThrough(
            String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 从 redis 中获取数据
        String json = stringRedisTemplate.opsForValue().get(key);
        // 存在直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中的是否是空值
        if (!Objects.isNull(json)) {
            return null;
        }
        // 不存在 查询数据库
        T result = dbFallback.apply(id);
        // 数据库不存在 错误
        if (Objects.isNull(result)) {
            // 不存在写入空数据 防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            throw new BusinessException("数据不存在");
        }
        // 数据库存在 返回 并写入redis
        this.set(key, result, time, timeUnit);
        return result;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(5, 10, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    /**
     * 使用逻辑过期解决击穿
     *
     * @param id
     * @return
     */
    public <T> T queryByIdWithLogicExpire(
            String keyPrefix, Long id, Class<T> type, Function<Long, T> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 从 redis 中获取数据
        String json = stringRedisTemplate.opsForValue().get(key);
        // 不存在
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 存在判断是否过期
        RedisData bean = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = bean.getExpireTime();
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        RedisLock redisLock = new RedisLock(lockKey, stringRedisTemplate);
        if (LocalDateTime.now().isAfter(expireTime)) {
            // 过期了 获取到锁了 进行更新
            if (redisLock.tryLock(RedisConstants.LOCK_SHOP_TTL)) {
                // doubleCheck
                json = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json)) {
                    bean = JSONUtil.toBean(json, RedisData.class);
                    expireTime = bean.getExpireTime();
                    if (LocalDateTime.now().isBefore(expireTime)) {
                        RedisData data = JSONUtil.toBean(json, RedisData.class);
                        return JSONUtil.toBean((JSONObject) data.getData(), type);
                    }
                }
                // 开启一个线程更新
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 查询数据库
                        T result = dbFallback.apply(id);
                        // 写入redis
                        this.setWithLogicalExpire(key, result, time, timeUnit);
                    } catch (Exception e) {
                        throw new BusinessException(e.getMessage());
                    } finally {
                        // 释放锁
                        redisLock.unlock();
                    }
                });
            }
        }
        // 返回旧数据
        return JSONUtil.toBean((JSONObject) bean.getData(), type);
    }

    /**
     * 通过互斥锁方式防止缓存击穿
     *
     * @param id 商铺id
     * @return 商铺
     */
    public <T> T queryByIdWithMutex( String keyPrefix, Long id, Class<T> type, Function<Long, T> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 从 redis 中获取数据
        String jsonShop = stringRedisTemplate.opsForValue().get(key);
        // 存在直接返回
        if (StrUtil.isNotBlank(jsonShop)) {
            return JSONUtil.toBean(jsonShop, type);
        }
        // jsonShop 为 "" 的情况
        if (!Objects.isNull(jsonShop)) {
           return null;
        }
        // 不存在 嘗試獲取鎖
        T result = null;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        RedisLock lock = new RedisLock(lockKey, stringRedisTemplate);
        try {
            // 通过互斥锁方式防止缓存击穿
            boolean isLock = lock.tryLock(RedisConstants.LOCK_SHOP_TTL);
            if (!isLock) {
                // 沒獲取到 重試 会造成线程的阻塞
                Thread.sleep(50);
                return queryByIdWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }
            // 獲取到了再尝试获取一次
            jsonShop = stringRedisTemplate.opsForValue().get(key);
            // 存在返回
            if (StrUtil.isNotBlank(jsonShop)) {
                return JSONUtil.toBean(jsonShop, type);
            }
            // 不存在 查询数据库
            result = dbFallback.apply(id);
            // 数据库不存在 错误
            if (Objects.isNull(result)) {
                // 不存在写入空数据 防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
                return null;
            }
            // 数据库存在 返回 并写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(result), time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            lock.unlock();
        }
        return result;
    }


}
