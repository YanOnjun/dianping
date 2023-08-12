package com.hmdp.common.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.common.constant.RedisConstants;
import com.hmdp.entity.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.common.constant.RedisConstants.LOGIN_USER_TTL;

/**
 * 重新设置用户token过期时间
 *
 * @author : 上春
 * @create 2023/7/20 10:33
 */
public class ResetTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    public ResetTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取session中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 根据token获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            // 如果没有用户了 清除threadLocal中的user
            UserHolder.removeUser();
            return true;
        }
        // 有用户 将用户转成UserDTO 存在 treadLocal 中
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 更新过期时间
        redisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 放行
        return true;
    }
}
