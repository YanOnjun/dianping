package com.hmdp.service;

import com.hmdp.entity.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * 
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Shop queryById(Long id);

    Result update(Shop shop);
}
