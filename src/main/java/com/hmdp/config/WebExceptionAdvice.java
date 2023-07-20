package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常,请联系管理员");
    }

    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException (BusinessException e) {
        log.error(e.toString(), e);
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}
