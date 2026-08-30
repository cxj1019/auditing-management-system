package com.accounting.firm.notify.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.notify.entity.SysNotification;
import com.accounting.firm.notify.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通知接口
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    /** 当前用户通知列表（最新在前）与未读数 */
    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(defaultValue = "20") long limit) {
        List<SysNotification> list = notifyService.listMine(limit);
        return ApiResult.success(Map.of(
                "list", list,
                "unread", notifyService.unreadCount()));
    }

    /** 未读数（顶栏角标轮询） */
    @GetMapping("/unread-count")
    public ApiResult<Long> unreadCount() {
        return ApiResult.success(notifyService.unreadCount());
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public ApiResult<Void> read(@PathVariable Long id) {
        notifyService.markRead(id);
        return ApiResult.success();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public ApiResult<Void> readAll() {
        notifyService.markAllRead();
        return ApiResult.success();
    }

    /** 手动触发每日提醒扫描（定时任务每天 08:00 自动执行） */
    @PostMapping("/generate")
    public ApiResult<Integer> generate() {
        return ApiResult.success(notifyService.generateDailyReminders());
    }
}
