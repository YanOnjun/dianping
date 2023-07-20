package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Objects;

/**
 * @author : 上春
 * @create 2023/7/20 10:33
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取session中的用户
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        // 没有用户拦截
        if (Objects.isNull(user)) {
            // 重定向
            // response.sendRedirect("/login.html");
            return false;
        }
        // 有用户 存在 treadLocal 中
        UserHolder.saveUser((UserDTO) user);
        // 放行
        return true;
    }
}
