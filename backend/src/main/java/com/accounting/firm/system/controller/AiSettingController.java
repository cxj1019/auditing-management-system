package com.accounting.firm.system.controller;

import com.accounting.firm.common.ai.AppSettingService;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.security.SecurityUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** AI 接口设置（OpenAI 兼容），仅管理员 */
@RestController
@RequestMapping("/api/system/ai-settings")
@PreAuthorize("hasAuthority('system:ai:list')")
public class AiSettingController {

    private final AppSettingService appSettingService;

    public AiSettingController(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    @GetMapping
    public ApiResult<Map<String, Object>> get() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseUrl", appSettingService.get(AppSettingService.KEY_BASE_URL));
        data.put("apiKey", appSettingService.get(AppSettingService.KEY_API_KEY));
        data.put("model", appSettingService.get(AppSettingService.KEY_MODEL));
        data.put("configured", appSettingService.aiConfigured());
        return ApiResult.success(data);
    }

    @PutMapping
    public ApiResult<Void> save(@RequestBody Map<String, String> body,
                                @AuthenticationPrincipal SecurityUser currentUser) {
        appSettingService.save(AppSettingService.KEY_BASE_URL, trim(body.get("baseUrl")), currentUser.getUsername());
        appSettingService.save(AppSettingService.KEY_API_KEY, trim(body.get("apiKey")), currentUser.getUsername());
        appSettingService.save(AppSettingService.KEY_MODEL, trim(body.get("model")), currentUser.getUsername());
        return ApiResult.success();
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
