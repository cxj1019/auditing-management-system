package com.accounting.firm.common.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 全局键值设置：AI 接口配置（ai_base_url / ai_api_key / ai_model）
 */
@Service
@RequiredArgsConstructor
public class AppSettingService {

    public static final String KEY_BASE_URL = "ai_base_url";
    public static final String KEY_API_KEY = "ai_api_key";
    public static final String KEY_MODEL = "ai_model";

    private final AppSettingMapper appSettingMapper;

    public String get(String key) {
        AppSetting setting = appSettingMapper.selectOne(new LambdaQueryWrapper<AppSetting>()
                .eq(AppSetting::getSettingKey, key));
        return setting == null ? null : setting.getSettingValue();
    }

    public void save(String key, String value, String operator) {
        AppSetting existing = appSettingMapper.selectOne(new LambdaQueryWrapper<AppSetting>()
                .eq(AppSetting::getSettingKey, key));
        if (existing == null) {
            AppSetting fresh = new AppSetting();
            fresh.setSettingKey(key);
            fresh.setSettingValue(value);
            fresh.setUpdateBy(operator);
            fresh.setUpdateTime(java.time.LocalDateTime.now());
            appSettingMapper.insert(fresh);
        } else {
            existing.setSettingValue(value);
            existing.setUpdateBy(operator);
            existing.setUpdateTime(java.time.LocalDateTime.now());
            appSettingMapper.updateById(existing);
        }
    }

    public void saveAll(Map<String, String> values, String operator) {
        values.forEach((k, v) -> save(k, v, operator));
    }

    /** AI 是否已配置 */
    public boolean aiConfigured() {
        String base = get(KEY_BASE_URL);
        String key = get(KEY_API_KEY);
        String model = get(KEY_MODEL);
        return base != null && !base.isBlank() && key != null && !key.isBlank() && model != null && !model.isBlank();
    }
}
