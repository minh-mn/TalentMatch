package com.talentmatch.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String baseUrl;
    private final String fromAddress;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${app.mail.enabled:true}") boolean enabled,
                       @Value("${app.base-url:http://localhost:8080}") String baseUrl,
                       @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    }

    public String sendVerification(String email, String token) {
        String link = baseUrl + "/verify?token=" + token;
        String body = "Xin chào,\n\n"
                + "Bạn vừa đăng ký tài khoản TalentMatch. Nhấn liên kết dưới đây để xác thực email:\n"
                + link + "\n\n"
                + "Liên kết có hiệu lực trong 24 giờ. Nếu bạn không thực hiện đăng ký này, hãy bỏ qua email.\n\n"
                + "TalentMatch";
        send(email, "Xác nhận tài khoản TalentMatch", body);
        return link;
    }

    public String sendResetPassword(String email, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        String body = "Xin chào,\n\n"
                + "TalentMatch nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Nhấn liên kết dưới đây:\n"
                + link + "\n\n"
                + "Liên kết có hiệu lực trong 30 phút và chỉ sử dụng được một lần.\n"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.\n\n"
                + "TalentMatch";
        send(email, "Đặt lại mật khẩu TalentMatch", body);
        return link;
    }

    private void send(String to, String subject, String body) {
        if (!enabled) {
            throw new IllegalStateException("Gửi email đang bị tắt. Hãy bật APP_MAIL_ENABLED=true và cấu hình SMTP.");
        }
        if (fromAddress.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình MAIL_USERNAME. Hãy nhập email SMTP thật trước khi đăng ký hoặc quên mật khẩu.");
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP chưa được cấu hình.");
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
