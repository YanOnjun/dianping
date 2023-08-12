package com.hmdp.common.redis;

/**
 * @author : 上春
 * @create 2023/8/6 18:32
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeout 超时时间
     * @return 是否获取成功 true 成功 false 失败
     */
    boolean tryLock(long timeout);

    /**
     * 释放锁
     */
    void unlock();
}
