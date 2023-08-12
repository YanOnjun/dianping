package com.hmdp.controller;

import cn.hutool.json.JSONUtil;
import com.hmdp.common.constant.RedisConstants;
import com.hmdp.entity.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        // 查询 redis 有结果 返回
        List<String> range = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        if (!CollectionUtils.isEmpty(range)) {
            // 存进去的时候保证有序 就不排序了
            List<ShopType> collect = range.stream().map(member -> JSONUtil.toBean(member, ShopType.class)).
                    collect(Collectors.toList());
            return Result.ok(collect);
        }
        // 查询数据库
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        if (CollectionUtils.isEmpty(typeList)) {
            // todo 不存在缓存空数据
            return Result.fail("商铺分类信息为空，请联系管理员");
        }
        // 存入 redis
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY,
                typeList.stream().map(JSONUtil::toJsonStr).toArray(String[]::new));
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY, RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.SECONDS);
        return Result.ok(typeList);
    }
}
