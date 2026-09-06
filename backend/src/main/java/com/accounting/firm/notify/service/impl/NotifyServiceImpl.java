package com.accounting.firm.notify.service.impl;

import com.accounting.firm.collection.entity.ContractPayment;
import com.accounting.firm.collection.mapper.ContractPaymentMapper;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.entity.ContractStatus;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.entity.InvoiceStatus;
import com.accounting.firm.invoice.mapper.InvoiceMapper;
import com.accounting.firm.notify.entity.SysNotification;
import com.accounting.firm.notify.mapper.SysNotificationMapper;
import com.accounting.firm.notify.service.NotifyService;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.system.entity.SysMenu;
import com.accounting.firm.system.entity.SysRole;
import com.accounting.firm.system.entity.SysUser;
import com.accounting.firm.system.mapper.SysMenuMapper;
import com.accounting.firm.system.mapper.SysRoleMapper;
import com.accounting.firm.system.mapper.SysRoleMenuMapper;
import com.accounting.firm.system.mapper.SysUserMapper;
import com.accounting.firm.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内通知服务实现
 * <p>每日定时扫描四类提醒：逾期应收（开票超期未核销）、函证逾期、报销审批滞留、合同即将到期。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl extends ServiceImpl<SysNotificationMapper, SysNotification>
        implements NotifyService {

    public static final String TYPE_RECEIVABLE = "receivable";
    public static final String TYPE_CONFIRMATION = "confirmation";
    public static final String TYPE_REIMBURSEMENT = "reimbursement";
    public static final String TYPE_CONTRACT = "contract";

    /** 应收账款逾期天数 */
    @Value("${notify.receivable-overdue-days:30}")
    private int receivableOverdueDays;

    /** 报销审批滞留天数 */
    @Value("${notify.reimbursement-stagnant-days:3}")
    private int reimbursementStagnantDays;

    /** 合同到期预警天数 */
    @Value("${notify.contract-expiring-days:30}")
    private int contractExpiringDays;

    private final InvoiceMapper invoiceMapper;
    private final ContractPaymentMapper paymentMapper;
    private final ConfirmationMapper confirmationMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final ContractMapper contractMapper;
    private final SysUserMapper sysUserMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public List<SysNotification> listMine(long limit) {
        return list(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, currentUserId())
                .orderByDesc(SysNotification::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
    }

    @Override
    public long unreadCount() {
        return count(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, currentUserId())
                .eq(SysNotification::getIsRead, 0));
    }

    @Override
    public void markRead(Long id) {
        SysNotification notification = getById(id);
        if (notification == null || !notification.getUserId().equals(currentUserId())) {
            throw new BusinessException("通知不存在");
        }
        notification.setIsRead(1);
        updateById(notification);
    }

    @Override
    public void markAllRead() {
        SysNotification patch = new SysNotification();
        patch.setIsRead(1);
        update(patch, new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, currentUserId())
                .eq(SysNotification::getIsRead, 0));
    }

    /** 每日 08:00 自动扫描（亦可通过接口手动触发） */
    @Scheduled(cron = "${notify.cron:0 0 8 * * ?}")
    @Override
    public int generateDailyReminders() {
        int created = generateReceivableReminders()
                + generateConfirmationReminders()
                + generateReimbursementReminders()
                + generateContractReminders();
        log.info("[提醒] 每日提醒扫描完成，新增 {} 条", created);
        return created;
    }

    /** 逾期应收：已开票超期且未全额核销 */
    private int generateReceivableReminders() {
        LocalDate deadline = LocalDate.now().minusDays(receivableOverdueDays);
        List<Invoice> invoices = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getStatus, InvoiceStatus.ISSUED.getCode())
                .le(Invoice::getInvoiceDate, deadline));
        int created = 0;
        for (Invoice invoice : invoices) {
            BigDecimal collected = paymentMapper.selectList(new LambdaQueryWrapper<ContractPayment>()
                            .eq(ContractPayment::getInvoiceId, invoice.getId()))
                    .stream().map(ContractPayment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outstanding = invoice.getAmount().subtract(collected);
            if (outstanding.signum() <= 0) {
                continue;
            }
            String content = "发票 %s 已开票超过 %d 天，仍有 %s 元未回款".formatted(
                    invoice.getInvoiceNo() == null ? "(待补号)" : invoice.getInvoiceNo(),
                    receivableOverdueDays, outstanding.toPlainString());
            created += notifyCreator(invoice.getCreateBy(), TYPE_RECEIVABLE, invoice.getId(),
                    "/business/invoice", "逾期应收提醒", content);
        }
        return created;
    }

    /** 函证逾期：已发出超期未回函 */
    private int generateConfirmationReminders() {
        LocalDate deadline = LocalDate.now().minusDays(receivableOverdueDays);
        List<Confirmation> list = confirmationMapper.selectList(new LambdaQueryWrapper<Confirmation>()
                .eq(Confirmation::getStatus, 1)
                .le(Confirmation::getSentDate, deadline));
        int created = 0;
        for (Confirmation confirmation : list) {
            String content = "函证 %s（%s）已发出超过 %d 天尚未回函".formatted(
                    confirmation.getConfirmationNo(), confirmation.getTargetUnit(), receivableOverdueDays);
            created += notifyCreator(confirmation.getCreateBy(), TYPE_CONFIRMATION, confirmation.getId(),
                    "/business/confirmation", "函证逾期提醒", content);
        }
        return created;
    }

    /** 报销滞留：待审批超过 N 天 */
    private int generateReimbursementReminders() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(reimbursementStagnantDays);
        List<Reimbursement> list = reimbursementMapper.selectList(new LambdaQueryWrapper<Reimbursement>()
                .eq(Reimbursement::getStatus, 1)
                .le(Reimbursement::getCreateTime, deadline));
        int created = 0;
        for (Reimbursement reimbursement : list) {
            String content = "报销单 %s（%s 元）已提交超过 %d 天仍未审批完成".formatted(
                    reimbursement.getReimbursementNo(), reimbursement.getTotalAmount(), reimbursementStagnantDays);
            created += notifyCreator(reimbursement.getCreateBy(), TYPE_REIMBURSEMENT, reimbursement.getId(),
                    "/business/reimbursement", "报销审批滞留", content);
        }
        return created;
    }

    /** 合同即将到期：执行中且服务期 N 天内到期 */
    private int generateContractReminders() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(contractExpiringDays);
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, ContractStatus.RUNNING.getCode())
                .ge(Contract::getServiceEnd, today)
                .le(Contract::getServiceEnd, deadline));
        int created = 0;
        for (Contract contract : list) {
            String content = "合同 %s（%s）将于 %s 到期，请及时跟进续签或收尾".formatted(
                    contract.getContractNo(), contract.getName(), contract.getServiceEnd());
            created += notifyCreator(contract.getCreateBy(), TYPE_CONTRACT, contract.getId(),
                    "/business/contract", "合同即将到期", content);
        }
        return created;
    }

    @Override
    public void push(Long userId, String type, Long relatedId, String path, String title, String content) {
        if (userId == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        boolean exists = count(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getType, type)
                .eq(SysNotification::getRelatedId, relatedId)
                .eq(SysNotification::getDedupDate, today)) > 0;
        if (exists) {
            return;
        }
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedPath(path);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setDedupDate(today);
        notification.setCreateTime(LocalDateTime.now());
        save(notification);
    }

    @Override
    public List<Long> userIdsWithPermission(String perm) {
        SysMenu menu = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPerm, perm).last("LIMIT 1"));
        if (menu == null) {
            return List.of();
        }
        List<Long> roleIds = sysRoleMenuMapper.selectList(new LambdaQueryWrapper<com.accounting.firm.system.entity.SysRoleMenu>()
                        .eq(com.accounting.firm.system.entity.SysRoleMenu::getMenuId, menu.getId()))
                .stream().map(com.accounting.firm.system.entity.SysRoleMenu::getRoleId).toList();
        return usersOfRoles(roleIds);
    }

    @Override
    public List<Long> userIdsWithRole(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode).last("LIMIT 1"));
        if (role == null) {
            return List.of();
        }
        return usersOfRoles(List.of(role.getId()));
    }

    /** 按角色集合查出启用状态的用户 ID（去重） */
    private List<Long> usersOfRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<com.accounting.firm.system.entity.SysUserRole>()
                        .in(com.accounting.firm.system.entity.SysUserRole::getRoleId, roleIds))
                .stream().map(com.accounting.firm.system.entity.SysUserRole::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds).eq(SysUser::getStatus, 1))
                .stream().map(SysUser::getId).toList();
    }

    /** 给业务创建人发通知；按 用户+类型+关联对象+当天 去重。返回 0/1 */
    private int notifyCreator(String username, String type, Long relatedId,
                              String path, String title, String content) {
        if (username == null) {
            return 0;
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("LIMIT 1"));
        if (user == null) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        boolean exists = count(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, user.getId())
                .eq(SysNotification::getType, type)
                .eq(SysNotification::getRelatedId, relatedId)
                .eq(SysNotification::getDedupDate, today)) > 0;
        if (exists) {
            return 0;
        }
        SysNotification notification = new SysNotification();
        notification.setUserId(user.getId());
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedPath(path);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setDedupDate(today);
        notification.setCreateTime(LocalDateTime.now());
        save(notification);
        return 1;
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityUser user) {
            return user.getUserId();
        }
        throw new BusinessException("未登录");
    }
}
