package com.hmdp.config;

import com.hmdp.entity.dto.UserDTO;
import com.hmdp.common.utils.ResetTokenInterceptor;
import com.hmdp.common.utils.UserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
 * @author : 上春
 * @create 2023/7/20 10:39
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截所有路径 用户如果登录了 更新用户过期时间
        registry.addInterceptor(new ResetTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
        // 是否登录拦截
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                UserDTO user = UserHolder.getUser();
                return !Objects.isNull(user);
            }
        }).excludePathPatterns(
                "/shop/**",
                "/voucher/**",
                "/shop-type/**",
                "/upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
        ).order(1);
    }
}
