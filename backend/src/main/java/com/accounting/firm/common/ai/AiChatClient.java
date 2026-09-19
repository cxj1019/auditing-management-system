package com.accounting.firm.common.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 客户端（支持视觉：图片以 base64 data-url 传入）。
 * 兼容 OpenAI / 通义 DashScope 兼容模式 / DeepSeek / 本地 vLLM 等。
 */
@Service
@RequiredArgsConstructor
public class AiChatClient {

    private final AppSettingService appSettingService;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /**
     * 发送多模态请求：textPrompt + 每页一张图片（JPEG base64）。
     * 返回模型文本回复（通常要求模型输出 JSON）。
     */
    public String chatWithImages(String textPrompt, List<byte[]> jpegPages) {
        String baseUrl = appSettingService.get(AppSettingService.KEY_BASE_URL);
        String apiKey = appSettingService.get(AppSettingService.KEY_API_KEY);
        String model = appSettingService.get(AppSettingService.KEY_MODEL);
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()
                || model == null || model.isBlank()) {
            throw new com.accounting.firm.common.exception.BusinessException(
                    "AI 接口未配置，请先在【系统管理 → AI 设置】填写接口地址、API Key 和模型");
        }
        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", textPrompt));
        for (byte[] jpeg : jpegPages) {
            String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", content)),
                "temperature", 0);

        StringBuilder payload = new StringBuilder();
        buildJson(payload, body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(180))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new com.accounting.firm.common.exception.BusinessException(
                        "AI 接口返回 " + response.statusCode() + "：" + truncate(response.body(), 300));
            }
            String reply = extractContent(response.body());
            if (reply == null || reply.isBlank()) {
                throw new com.accounting.firm.common.exception.BusinessException("AI 返回内容为空");
            }
            return reply;
        } catch (com.accounting.firm.common.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new com.accounting.firm.common.exception.BusinessException("调用 AI 接口失败：" + e.getMessage());
        }
    }

    private String extractContent(String responseBody) {
        // 轻量解析 choices[0].message.content，避免引入 JSON 绑定差异
        int i = responseBody.indexOf("\"content\"");
        if (i < 0) return null;
        int colon = responseBody.indexOf(':', i);
        int q1 = responseBody.indexOf('"', colon);
        if (q1 < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int k = q1 + 1; k < responseBody.length(); k++) {
            char c = responseBody.charAt(k);
            if (c == '\\') {
                k++;
                if (k >= responseBody.length()) break;
                char e = responseBody.charAt(k);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (k + 4 < responseBody.length()) {
                            sb.append((char) Integer.parseInt(responseBody.substring(k + 1, k + 5), 16));
                            k += 4;
                        }
                    }
                    default -> sb.append(e);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void buildJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            sb.append('"').append(str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (var entry : ((Map<Object, Object>) map).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                buildJson(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                buildJson(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                buildJson(sb, item);
            }
            sb.append(']');
        }
    }

    private String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
