package com.accounting.firm.common.security;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌黑名单服务
 * <p>登出、修改密码时将令牌 jti 加入黑名单，使其立即失效。</p>
 * <p>当前使用内存存储（ConcurrentHashMap），适用于单体部署；
 * 生产环境多实例部署时建议替换为 Redis 实现（接口已预留）。</p>
 */
@Service
public class TokenBlacklistService {

    /** jti -> 过期时间戳（毫秒），过期后条目可清理 */
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    /** 将令牌加入黑名单 */
    public void blacklist(String jti, Date expiration) {
        if (jti != null && expiration != null) {
            blacklist.put(jti, expiration.getTime());
        }
    }

    /** 判断令牌是否在黑名单中 */
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        Long expireAt = blacklist.get(jti);
        if (expireAt == null) {
            return false;
        }
        // 已过期的黑名单条目失去意义，顺手清理
        if (expireAt < System.currentTimeMillis()) {
            blacklist.remove(jti);
            return false;
        }
        return true;
    }

    /** 清理已过期的黑名单条目（可由定时任务调用） */
    public void cleanup() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
