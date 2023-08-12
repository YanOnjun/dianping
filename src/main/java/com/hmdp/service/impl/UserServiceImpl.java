package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.dto.LoginFormDTO;
import com.hmdp.entity.dto.Result;
import com.hmdp.entity.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.common.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.common.constant.RedisConstants.*;
import static com.hmdp.common.constant.SystemConstants.USER_NICK_NAME_PREFIX;
import static com.hmdp.common.constant.UserConstant.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        checkPhone(phone);
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.SECONDS);
        // 发送验证码
        log.info("发送验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        checkPhone(loginForm.getPhone());
        // 验证验证码
        checkCode(loginForm.getCode(), loginForm.getPhone());
        // 根据手机号查询用户
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq(STR_PHONE, loginForm.getPhone());
        User user = userMapper.selectOne(qw);
        // 用户是否存在
        if (Objects.isNull(user)) {
            // 3.1 不存在 注册用户
            user = createNewUser(loginForm.getPhone());
        }
        // 3.2 存在 保存到redis
        // 3.2.1 生成token
        String token = UUID.randomUUID().toString(true);
        // 3.2.2 转成hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> hashUser = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().
                        ignoreNullValue().
                        setFieldValueEditor((field, value) -> value.toString()));
        // TODO 非原子
        // 3.2.3 保存到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, hashUser);
        // 3.2.4 设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 3.3 返回token
        return Result.ok(token);
    }

    /**
     * 校验手机号
     */
    private void checkPhone(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new BusinessException("手机号格式不正确");
        }
    }

    private void checkCode(String code, String phone) {
        // 从redis中获取code
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("验证码不能为空");
        }
        if (!Objects.equals(code, cacheCode)) {
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
