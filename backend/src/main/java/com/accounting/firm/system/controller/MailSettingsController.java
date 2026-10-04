package com.accounting.firm.system.controller;

import com.accounting.firm.common.ai.AppSettingService;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.mail.MailService;
import com.accounting.firm.common.security.SecurityUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 邮件通知设置（SMTP），仅管理员；密码脱敏返回，留空表示不修改 */
@RestController
@RequestMapping("/api/system/mail-settings")
@PreAuthorize("hasAuthority('system:ai:list')")
public class MailSettingsController {

    private final AppSettingService appSettingService;
    private final MailService mailService;

    public MailSettingsController(AppSettingService appSettingService, MailService mailService) {
        this.appSettingService = appSettingService;
        this.mailService = mailService;
    }

    @GetMapping
    public ApiResult<Map<String, Object>> get() {
        String pwd = appSettingService.get(MailService.KEY_PASSWORD);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("host", appSettingService.get(MailService.KEY_HOST));
        data.put("port", appSettingService.get(MailService.KEY_PORT));
        data.put("username", appSettingService.get(MailService.KEY_USERNAME));
        data.put("passwordMasked", pwd == null || pwd.isBlank() ? "" : "******");
        data.put("from", appSettingService.get(MailService.KEY_FROM));
        data.put("enabled", "true".equalsIgnoreCase(appSettingService.get(MailService.KEY_ENABLED)));
        data.put("ready", mailService.ready());
        return ApiResult.success(data);
    }

    @PutMapping
    public ApiResult<Void> save(@RequestBody Map<String, String> body,
                                @AuthenticationPrincipal SecurityUser currentUser) {
        appSettingService.save(MailService.KEY_HOST, trim(body.get("host")), currentUser.getUsername());
        appSettingService.save(MailService.KEY_PORT, trim(body.get("port")), currentUser.getUsername());
        appSettingService.save(MailService.KEY_USERNAME, trim(body.get("username")), currentUser.getUsername());
        String password = trim(body.get("password"));
        if (password != null && !password.isBlank()) {
            appSettingService.save(MailService.KEY_PASSWORD, password, currentUser.getUsername());
        }
        appSettingService.save(MailService.KEY_FROM, trim(body.get("from")), currentUser.getUsername());
        appSettingService.save(MailService.KEY_ENABLED,
                "true".equalsIgnoreCase(trim(body.get("enabled"))) ? "true" : "false", currentUser.getUsername());
        return ApiResult.success();
    }

    /** 发送测试邮件，验证配置 */
    @PostMapping("/test")
    public ApiResult<Void> test(@RequestBody Map<String, String> body) {
        String to = trim(body.get("to"));
        if (to == null || to.isBlank()) {
            return ApiResult.error(400, "请填写收件邮箱");
        }
        if (!mailService.ready()) {
            return ApiResult.error(400, "邮件尚未启用或配置不完整");
        }
        try {
            mailService.sendTest(to);
            return ApiResult.success();
        } catch (Exception e) {
            return ApiResult.error(500, "发送失败：" + e.getMessage());
        }
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
