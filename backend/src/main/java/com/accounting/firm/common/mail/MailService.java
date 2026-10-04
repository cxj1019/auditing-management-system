package com.accounting.firm.common.mail;

import com.accounting.firm.common.ai.AppSettingService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
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

    private final AppSettingService appSettingService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail-notify");
        t.setDaemon(true);
        return t;
    });

    /** 邮件通道是否可用（已启用且配置完整） */
    public boolean ready() {
        return "true".equalsIgnoreCase(appSettingService.get(KEY_ENABLED))
                && notBlank(appSettingService.get(KEY_HOST))
                && notBlank(appSettingService.get(KEY_USERNAME))
                && notBlank(appSettingService.get(KEY_PASSWORD))
                && notBlank(appSettingService.get(KEY_FROM));
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

    private void doSend(String to, String subject, String content) throws Exception {
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

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(appSettingService.get(KEY_FROM));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, false);
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
