package com.accounting.firm.common.backup;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 备份管理（仅管理员）：手动触发 + 历史列表 */
@RestController
@RequestMapping("/api/system/backup")
@PreAuthorize("hasAuthority('system:ai:list')")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /** 立即执行一次完整备份 */
    @PostMapping("/run")
    public ApiResult<Map<String, Object>> run(@AuthenticationPrincipal SecurityUser currentUser) throws Exception {
        return ApiResult.success(backupService.runBackup(currentUser.getUsername()));
    }

    /** 最近备份历史 */
    @GetMapping("/history")
    public ApiResult<List<BackupHistory>> history() {
        return ApiResult.success(backupService.history());
    }

    /** 立即生成新备份并把 ZIP 发送到 backup_mail_to（异地容灾兜底） */
    @PostMapping("/mail-latest")
    public ApiResult<String> mailLatest(@AuthenticationPrincipal SecurityUser currentUser) throws Exception {
        return ApiResult.success(backupService.mailLatest(currentUser.getUsername()));
    }
}
