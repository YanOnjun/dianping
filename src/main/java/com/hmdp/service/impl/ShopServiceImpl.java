package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.common.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zx065
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Shop queryById(Long id) {
        Shop shop = queryByIdWithLogicExpire(id);
        if (Objects.isNull(shop)) {
            throw new BusinessException("商铺不存在");
        }
        return shop;
    }

    /**
     * 会造成击穿的方法
     *
     * @param id
     * @return
     */
    public Shop queryByIdWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从 redis 中获取数据
        String jsonShop = stringRedisTemplate.opsForValue().get(key);
        // 存在直接返回
        if (StrUtil.isNotBlank(jsonShop)) {
            return JSONUtil.toBean(jsonShop, Shop.class);
        }
        // 不存在 查询数据库
        Shop shop = getById(id);
        // 数据库不存在 错误
        if (Objects.isNull(shop)) {
            // 不存在写入空数据 防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            throw new BusinessException("商铺不存在");
        }
        // 数据库存在 返回 并写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(5, 10, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    /**
     * 使用逻辑过期解决击穿
     *
     * @param id
     * @return
     */
    public Shop queryByIdWithLogicExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从 redis 中获取数据
        String jsonShop = stringRedisTemplate.opsForValue().get(key);
        // 不存在
        if (StrUtil.isBlank(jsonShop)) {
            return null;
        }
        // 存在判断是否过期
        RedisData bean = JSONUtil.toBean(jsonShop, RedisData.class);
        LocalDateTime expireTime = bean.getExpireTime();
        if (LocalDateTime.now().isAfter(expireTime)) {
            // 过期了 获取到锁了 进行更新
            if (tryLock(id)) {
                // doubleCheck
                jsonShop = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(jsonShop)) {
                    bean = JSONUtil.toBean(jsonShop, RedisData.class);
                    expireTime = bean.getExpireTime();
                    if (LocalDateTime.now().isBefore(expireTime)) {
                        RedisData data = JSONUtil.toBean(jsonShop, RedisData.class);
                        return JSONUtil.toBean((JSONObject) data.getData(), Shop.class);
                    }
                }
                // 开启一个线程更新
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        save2RedisWithExpire(id, RedisConstants.CACHE_SHOP_TTL);
                    } catch (Exception e) {
                        throw new BusinessException(e.getMessage());
                    } finally {
                        // 释放锁
                        unlock(id);
                    }
                });
            }
        }
        // 返回旧数据
        return JSONUtil.toBean((JSONObject) bean.getData(), Shop.class);
    }

    /**
     * 通过互斥锁方式防止缓存击穿
     *
     * @param id 商铺id
     * @return 商铺
     */
    public Shop queryByIdWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从 redis 中获取数据
        String jsonShop = stringRedisTemplate.opsForValue().get(key);
        // 存在直接返回
        if (StrUtil.isNotBlank(jsonShop)) {
            return JSONUtil.toBean(jsonShop, Shop.class);
        }
        // jsonShop 为 "" 的情况
        if (!Objects.isNull(jsonShop)) {
            throw new BusinessException("商铺不存在");
        }
        // 不存在 嘗試獲取鎖
        Shop shop = null;
        try {
            // 通过互斥锁方式防止缓存击穿
            boolean notLock = tryLock(id);
            if (!notLock) {
                // 沒獲取到 重試 会造成线程的阻塞
                Thread.sleep(50);
                return queryByIdWithMutex(id);
            }
            // 獲取到了再尝试获取一次
            jsonShop = stringRedisTemplate.opsForValue().get(key);
            // 存在返回
            if (StrUtil.isNotBlank(jsonShop)) {
                return JSONUtil.toBean(jsonShop, Shop.class);
            }
            // 不存在 查询数据库
            shop = getById(id);
            // 数据库不存在 错误
            if (Objects.isNull(shop)) {
                // 不存在写入空数据 防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
                throw new BusinessException("商铺不存在");
            }
            // 数据库存在 返回 并写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(id);
        }
        return shop;
    }

    /**
     * 携带过期时间保存到redis中
     *
     * @param id
     */
    public void save2RedisWithExpire(Long id, Long expireTime) throws InterruptedException {
        Shop byId = getById(id);
        if (Objects.isNull(byId)) {
            throw new BusinessException("商铺不存在");
        }
        Thread.sleep(20);
        // 存入redis
        RedisData redisData = new RedisData();
        redisData.setData(byId);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    private boolean tryLock(Long id) {
        String key = RedisConstants.LOCK_SHOP_KEY + id;
        // 从 redis 中获取数据
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 存在直接返回
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(Long id) {
        String key = RedisConstants.LOCK_SHOP_KEY + id;
        // 从 redis 中获取数据
        stringRedisTemplate.delete(key);
    }

    /**
     * 事务更新
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        if (Objects.isNull(shop.getId())) {
            throw new BusinessException("店铺id为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return null;
    }
}
