package com.accounting.firm.common.mail;

import com.accounting.firm.common.ai.AppSettingService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMTP 邮件通知（设置存 app_setting，异步发送，失败不影响业务流程）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    public static final String KEY_HOST = "mail_host";
    public static final String KEY_PORT = "mail_port";
    public static final String KEY_USERNAME = "mail_username";
    public static final String KEY_PASSWORD = "mail_password";
    public static final String KEY_FROM = "mail_from";
    public static final String KEY_ENABLED = "mail_enabled";
    public static final String KEY_SSL = "mail_ssl";
    /** 发送渠道：smtp（默认）| resend */
    public static final String KEY_CHANNEL = "mail_channel";
    /** 系统对外访问地址（邮件直达链接用），如 https://mytscpa.19851019.xyz */
    public static final String KEY_BASE_URL = "app_base_url";
    public static final String KEY_RESEND_KEY = "mail_resend_key";
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final AppSettingService appSettingService;

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .build();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail-notify");
        t.setDaemon(true);
        return t;
    });

    /** 邮件通道是否可用（已启用且所选渠道配置完整） */
    public boolean ready() {
        if (!"true".equalsIgnoreCase(appSettingService.get(KEY_ENABLED)) || !notBlank(appSettingService.get(KEY_FROM))) {
            return false;
        }
        if (isResendChannel()) {
            return notBlank(appSettingService.get(KEY_RESEND_KEY));
        }
        return notBlank(appSettingService.get(KEY_HOST))
                && notBlank(appSettingService.get(KEY_USERNAME))
                && notBlank(appSettingService.get(KEY_PASSWORD));
    }

    /** 邮件正文里的"处理入口"直达链接；未配置 base url 时返回空串 */
    public String link(String path) {
        String base = appSettingService.get(KEY_BASE_URL);
        if (base == null || base.isBlank() || path == null || path.isBlank()) {
            return "";
        }
        return base.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path);
    }

    private boolean isResendChannel() {
        return "resend".equalsIgnoreCase(appSettingService.get(KEY_CHANNEL));
    }

    /** 异步发送；任何失败只记日志，绝不影响业务流程 */
    public void sendAsync(String to, String subject, String content) {
        if (!ready() || to == null || !to.contains("@")) {
            return;
        }
        executor.execute(() -> {
            try {
                doSend(to.trim(), subject, content);
            } catch (Exception e) {
                log.warn("邮件发送失败 to={}: {}", to, e.getMessage());
            }
        });
    }

    /** 同步发送测试邮件（设置页"发送测试"用） */
    public void sendTest(String to) throws Exception {
        doSend(to, "【审计管理系统】邮件通知测试", "如果您收到这封邮件，说明邮件通知配置成功。");
    }

    /** 按渠道分发：Resend HTTP API 或 SMTP */
    private void doSend(String to, String subject, String content) throws Exception {
        if (isResendChannel()) {
            sendViaResend(to, subject, content);
            return;
        }
        sendViaSmtp(to, subject, content);
    }

    /** 同步发送带单个附件的邮件（备份/月报投递用），失败抛出由调用方处理 */
    public void sendWithAttachment(String to, String subject, String content,
                                   String attachmentName, byte[] attachment) throws Exception {
        if (!ready() || to == null || !to.contains("@")) {
            throw new IllegalStateException("邮件通道未就绪或收件人无效");
        }
        if (isResendChannel()) {
            sendViaResend(to, subject, content, attachmentName, attachment);
        } else {
            sendViaSmtp(to, subject, content, attachmentName, attachment);
        }
    }

    /** Resend HTTP API：https://api.resend.com/emails（无附件） */
    private void sendViaResend(String to, String subject, String content) throws Exception {
        sendViaResend(to, subject, content, null, null);
    }

    /** Resend HTTP API：https://api.resend.com/emails */
    private void sendViaResend(String to, String subject, String content,
                               String attachmentName, byte[] attachment) throws Exception {
        String apiKey = appSettingService.get(KEY_RESEND_KEY);
        String from = appSettingService.get(KEY_FROM);
        StringBuilder json = new StringBuilder();
        json.append("{\"from\":\"").append(escapeJson(from)).append("\",\"to\":[\"")
                .append(escapeJson(to)).append("\"],\"subject\":\"").append(escapeJson(subject))
                .append("\",\"text\":\"").append(escapeJson(content)).append("\"");
        if (attachment != null) {
            json.append(",\"attachments\":[{\"filename\":\"").append(escapeJson(attachmentName))
                    .append("\",\"content\":\"").append(java.util.Base64.getEncoder().encodeToString(attachment))
                    .append("\"}]");
        }
        json.append("}");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Resend 返回 " + response.statusCode() + "：" + truncate(response.body(), 200));
        }
        log.info("邮件已发送(Resend) to={} subject={}", to, subject);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    private void sendViaSmtp(String to, String subject, String content) throws Exception {
        sendViaSmtp(to, subject, content, null, null);
    }

    private void sendViaSmtp(String to, String subject, String content,
                             String attachmentName, byte[] attachment) throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(appSettingService.get(KEY_HOST));
        String port = appSettingService.get(KEY_PORT);
        sender.setPort(port == null || port.isBlank() ? 465 : Integer.parseInt(port.trim()));
        sender.setUsername(appSettingService.get(KEY_USERNAME));
        sender.setPassword(appSettingService.get(KEY_PASSWORD));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", String.valueOf(asBool(appSettingService.get(KEY_SSL))));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");

        boolean multipart = attachment != null;
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, multipart, StandardCharsets.UTF_8.name());
        helper.setFrom(appSettingService.get(KEY_FROM));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, false);
        if (multipart) {
            helper.addAttachment(attachmentName,
                    new org.springframework.core.io.ByteArrayResource(attachment));
        }
        sender.send(message);
        log.info("邮件已发送 to={} subject={}", to, subject);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 未配置时默认启用 SSL（465 直连）；测试/明文场景可显式设为 false */
    private static boolean asBool(String v) {
        if (v == null || v.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(v.trim());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
