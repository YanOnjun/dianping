package com.hmdp.config;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.common.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.common.RedisConstants.LOGIN_USER_TTL;

/**
 * @author : 上春
 * @create 2023/7/20 10:39
 */
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/*",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                );
        // 拦截所有路径 用户如果登录了 更新用户过期时间
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // 获取session中的token
                String token = request.getHeader("authorization");
                if (StrUtil.isNotBlank(token)) {
                    UserDTO user = UserHolder.getUser();
                    // 去redis中查询用户数据是否存在
                    String key = LOGIN_USER_KEY + token;
                    Boolean hasKey = stringRedisTemplate.hasKey(key);
                    // 如果redis中有数据
                    if (Boolean.TRUE.equals(hasKey) && !Objects.isNull(user)) {
                        // 更新过期时间
                        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
                    }
                }
                // 这个拦截器不会进行拦截
                return true;
            }
        }).addPathPatterns("/**");
    }
}
