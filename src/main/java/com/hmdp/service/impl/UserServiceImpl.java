package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Objects;

import static com.hmdp.common.SystemConstants.USER_NICK_NAME_PREFIX;
import static com.hmdp.common.UserConstant.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * 
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        checkPhone(phone);
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到session
        session.setAttribute(STR_CODE, code);
        // 发送验证码
        log.info("发送验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        checkPhone(loginForm.getPhone());
        // 验证验证码
        checkCode(loginForm.getCode(), session);
        // 根据手机号查询用户
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq(STR_PHONE, loginForm.getPhone());
        User user = userMapper.selectOne(qw);
        // 用户是否存在
        if (Objects.isNull(user)) {
            // 3.1 不存在 注册用户
            user = createNewUser(loginForm.getPhone());
        }
        // 3.2 存在 保存到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    /**
     * 校验手机号
     */
    private void checkPhone (String phone) {
        if (RegexUtils.isPhoneInvalid(phone) ) {
            throw new BusinessException("手机号格式不正确");
        }
    }

    private void checkCode (String code, HttpSession session) {
        Object cacheCode = session.getAttribute(STR_CODE);
        if (Objects.isNull(code)) {
            throw new BusinessException("验证码不能为空");
        }
        if (Objects.isNull(cacheCode) || !Objects.equals(code, cacheCode)) {
            throw new BusinessException("验证码错误");
        }
    }

    private User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
