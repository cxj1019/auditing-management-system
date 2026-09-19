package com.accounting.firm.auth.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录失败锁定：同一账号 10 分钟内连续失败 5 次则锁定 10 分钟（内存实现，单实例部署）
 */
@Service
public class LoginLockService {

    private static final int MAX_FAILS = 5;
    private static final long LOCK_MILLIS = 10 * 60 * 1000L;

    private static final class Attempt {
        final AtomicInteger fails = new AtomicInteger(0);
        volatile long lockUntil = 0L;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 校验是否处于锁定中；是则抛出业务异常 */
    public void checkLocked(String username) {
        Attempt a = attempts.get(username);
        if (a != null && a.lockUntil > System.currentTimeMillis()) {
            long remainSec = (a.lockUntil - System.currentTimeMillis()) / 1000;
            throw new com.accounting.firm.common.exception.BusinessException(
                    "账号已锁定（登录失败过多），请 " + Math.max(remainSec / 60 + 1, 1) + " 分钟后再试");
        }
    }

    /** 记录一次失败，达到阈值则锁定 */
    public void recordFail(String username) {
        Attempt a = attempts.computeIfAbsent(username, k -> new Attempt());
        int n = a.fails.incrementAndGet();
        if (n >= MAX_FAILS) {
            Attempt fresh = new Attempt();
            fresh.lockUntil = System.currentTimeMillis() + LOCK_MILLIS;
            attempts.put(username, fresh);
        }
    }

    /** 登录成功后清除计数 */
    public void clear(String username) {
        attempts.remove(username);
    }
}
