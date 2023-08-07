package com.hmdp.utils.reiis;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * redis数据封装类 添加逻辑过期时间
 * @author zx065
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
