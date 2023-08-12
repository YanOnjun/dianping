package com.hmdp.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局id生成器
 * 使用 Redis String 自增实现
 * 存储Key: incr:(keyPrefix):year:month:day
 * 这种存储形式可以通过最后日期的层级进行统计年月日的生成数，并且防止自增数量超过限制
 * ID格式: 二进制格式 0(符号位)- 31bit(时间戳由当前时间减去规定时间) - 32bit(自增字段)
 *
 * @author : 上春
 * @create 2023/7/28 10:41
 */
@Component
public class GlobalIDGenerator {

    private static final long BEGIN_TIME = 1690502400L;

    private static final String INCR = "incr:";

    private static final int INCR_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long generate(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long time = epochSecond - BEGIN_TIME;
        // 2. 获取自增字段
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = INCR + keyPrefix + date;
        // 不可能为空 自增会自动创建字段
        long increment = stringRedisTemplate.opsForValue().increment(key);
        return time << INCR_BITS | increment;
    }

}
