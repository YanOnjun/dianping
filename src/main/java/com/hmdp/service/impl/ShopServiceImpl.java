package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.common.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Shop queryById(Long id) {
        Shop shop = queryByIdWithMutex(id);
        if (Objects.isNull(shop)) {
            throw new BusinessException("商铺不存在");
        }
        return shop;
    }

    public Shop queryByIdWith(Long id) {
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
            throw new BusinessException("商铺不存在");
        }
        // 数据库存在 返回 并写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
        return shop;
    }

    public Shop queryByIdWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从 redis 中获取数据
        String jsonShop = stringRedisTemplate.opsForValue().get(key);
        // 存在直接返回
        if (StrUtil.isNotBlank(jsonShop)) {
            return JSONUtil.toBean(jsonShop, Shop.class);
        }
        // 不存在 嘗試獲取鎖
        Shop shop = null;
        try {
            boolean notLock = tryLock(id);
            if (!notLock) {
                // 沒獲取到 重試
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
