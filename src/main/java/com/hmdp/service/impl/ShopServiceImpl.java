package com.hmdp.service.impl;

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

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从 redis 中获取数据
        String jsonShop = stringRedisTemplate.opsForValue().get(key);
        // 存在直接返回
        if (StrUtil.isNotBlank(jsonShop)) {
            Shop bean = JSONUtil.toBean(jsonShop, Shop.class);
            return Result.ok(bean);
        }
        // 不存在 查询数据库
        Shop shop = getById(id);
        // 数据库不存在 错误
        if(Objects.isNull(shop)){
            throw new BusinessException("商品不存在");
        }
        // 数据库存在 返回 并写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
        return Result.ok(shop);
    }
}
