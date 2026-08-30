package com.accounting.firm.common.storage;

import com.accounting.firm.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Supabase Storage 存储服务
 * <p>通过 Storage REST API 以 service_role 密钥读写私有桶（仅服务端使用）。
 * 网络请求自动重试一次，应对境内访问 Supabase 的间歇性超时。</p>
 */
@Slf4j
@Service
public class SupabaseStorageService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String serviceKey;
    private final String bucket;
    private boolean bucketReady;

    public SupabaseStorageService(@Value("${supabase.url}") String baseUrl,
                                  @Value("${supabase.service-key}") String serviceKey,
                                  @Value("${supabase.bucket:contract-attachments}") String bucket) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.serviceKey = serviceKey;
        this.bucket = bucket;
    }

    /** 上传对象（不存在桶时自动创建并重试一次） */
    public void upload(String objectPath, byte[] data, String contentType) {
        ensureBucket();
        HttpResponse<String> resp = sendWithRetry("PUT", objectPath, data, contentType, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            throw new BusinessException("附件存储失败（" + resp.statusCode() + "）");
        }
    }

    /** 下载对象内容 */
    public byte[] download(String objectPath) {
        HttpResponse<byte[]> resp = sendWithRetryBytes("GET", objectPath);
        if (resp.statusCode() >= 300) {
            throw new BusinessException("附件读取失败（" + resp.statusCode() + "）");
        }
        return resp.body();
    }

    /** 删除对象（404 视为已删除） */
    public void delete(String objectPath) {
        HttpResponse<String> resp = sendWithRetry("DELETE", objectPath, null, null, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300 && resp.statusCode() != 404) {
            throw new BusinessException("附件删除失败（" + resp.statusCode() + "）");
        }
    }

    /** 生成签名 URL（免鉴权，有效期 1 小时，用于前端预览/新窗口打开） */
    public String createSignedUrl(String objectPath) {
        try {
            String body = "{\"expiresIn\":3600}";
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/storage/v1/object/sign/" + bucket + "/" + objectPath))
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("apikey", serviceKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new BusinessException("生成预览地址失败（" + resp.statusCode() + "）");
            }
            // 解析 JSON 提取 signedURL
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(resp.body());
            String signedUrl = node.get("signedURL").asText();
            // Supabase 返回相对路径 /object/sign/...，需补全 /storage/v1 前缀
            if (signedUrl.startsWith("/")) {
                signedUrl = baseUrl + "/storage/v1" + signedUrl;
            }
            return signedUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成签名 URL 失败: {}", objectPath, e);
            throw new BusinessException("生成预览地址失败，请稍后重试");
        }
    }

    /** 确保存储桶存在（best-effort：失败不中断，桶大概率已存在；上传本身有重试） */
    private synchronized void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            String body = "{\"name\":\"" + bucket + "\",\"public\":false}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/storage/v1/bucket"))
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("apikey", serviceKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Supabase bucket ensure [{}]: {} {}", bucket, resp.statusCode(), resp.body());
        } catch (Exception e) {
            // 网络超时等异常不中断：桶大概率已存在，上传本身有重试兜底
            log.warn("Supabase bucket ensure skipped (network issue, bucket likely exists): {}", e.getMessage());
        }
        bucketReady = true;
    }

    /** 带重试的请求（最多 3 次尝试，应对境内访问 Supabase 的间歇性连接超时） */
    private <T> HttpResponse<T> sendWithRetry(String method, String objectPath, byte[] data,
                                               String contentType, HttpResponse.BodyHandler<T> handler) {
        Exception lastError = null;
        for (int attempt = 0; attempt <= 2; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(objectUrl(objectPath)))
                        .header("Authorization", "Bearer " + serviceKey)
                        .header("apikey", serviceKey)
                        .timeout(Duration.ofSeconds(60));
                if ("DELETE".equals(method)) {
                    builder.DELETE();
                } else if ("GET".equals(method)) {
                    builder.GET();
                } else {
                    builder.header("Content-Type", contentType == null ? "application/octet-stream" : contentType)
                            .header("x-upsert", "true")
                            .method(method, HttpRequest.BodyPublishers.ofByteArray(data == null ? new byte[0] : data));
                }
                return httpClient.send(builder.build(), handler);
            } catch (Exception e) {
                lastError = e;
                if (attempt < 2) {
                    log.warn("Supabase Storage 请求第 {} 次失败，0.5 秒后重试: {}", attempt + 1, objectPath);
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
        }
        log.error("Supabase Storage 请求 3 次重试均失败: {}", objectPath, lastError);
        throw new BusinessException("附件存储服务暂时不可用，请稍后重试");
    }

    /** 带重试的字节数组下载 */
    private HttpResponse<byte[]> sendWithRetryBytes(String method, String objectPath) {
        return sendWithRetry(method, objectPath, null, null, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String objectUrl(String objectPath) {
        return baseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;
    }
}
