package com.hmdp.exception;

/**
 * @author : 上春
 * @create 2023/7/17 21:57
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
