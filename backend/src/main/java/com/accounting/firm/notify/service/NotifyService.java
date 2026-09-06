package com.accounting.firm.notify.service;

import com.accounting.firm.notify.entity.SysNotification;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 站内通知服务（含每日提醒生成）
 */
public interface NotifyService extends IService<SysNotification> {

    /** 当前用户通知（最新在前） */
    List<SysNotification> listMine(long limit);

    /** 当前用户未读数 */
    long unreadCount();

    /** 标记已读（校验归属） */
    void markRead(Long id);

    /** 全部已读 */
    void markAllRead();

    /**
     * 扫描生成每日提醒（逾期应收/函证逾期/报销滞留/合同即将到期），
     * 按 用户+类型+关联对象+当天 去重。返回本次新增条数。
     */
    int generateDailyReminders();

    /** 业务事件实时推送：给单个用户发站内通知（按 用户+类型+关联对象+当天 去重） */
    void push(Long userId, String type, Long relatedId, String path, String title, String content);

    /** 拥有指定权限标识的全部启用用户 ID */
    List<Long> userIdsWithPermission(String perm);

    /** 拥有指定角色编码的全部启用用户 ID */
    List<Long> userIdsWithRole(String roleCode);
}
