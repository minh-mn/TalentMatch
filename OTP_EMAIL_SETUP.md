# TalentMatch – OTP thật qua Gmail (5 phút)

1. Trước tiên thu hồi mọi Gmail App Password đã từng hiển thị trong ảnh hoặc chat. Tạo App Password mới.
2. IntelliJ -> Run -> Edit Configurations -> cấu hình thực sự đang chạy (Spring Boot TalentMatchApplication hoặc Maven) -> Modify options -> Environment variables:
   APP_MAIL_ENABLED=true
   APP_BASE_URL=http://localhost:8080
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=<Gmail gửi thư>
   MAIL_PASSWORD=<App Password mới>
3. Không nhập App Password vào application.properties, không commit lên GitHub.
4. Dừng hoàn toàn các tiến trình Java cũ chiếm cổng 8080. Chạy lại bằng cấu hình trên.
5. /register -> điền form -> nhận OTP 6 số trong email -> /verify-otp -> nhập đúng mã trước 5 phút -> mới tạo user trong `users` và được login.
6. /forgot-password -> nhận OTP 6 số (chỉ tài khoản enabled) -> /reset-password -> nhập OTP và mật khẩu mới -> login.
7. Nếu gửi thư thất bại, kiểm tra Run log SMTP exception; kiểm tra Gmail app password, 2-step verification, network, tài khoản sender. Nếu không thấy thư, kiểm tra Spam.
8. Các mã OTP giới hạn 5 lần nhập sai, chỉ dùng một lần. Đăng ký yêu cầu mới ít nhất 60 giây sau lần gửi trước.
9. Với database dùng spring.jpa.hibernate.ddl-auto=update, cần có quyền ALTER TABLE để bổ sung cột failed_attempts/sent_at trong pending_registrations và failed_attempts trong password_reset_tokens. Hãy sao lưu trước khi đổi schema.
10. Bản V8 và trước dùng link xác minh; bản này dùng OTP, các yêu cầu xác minh cũ cần đăng ký lại. User cũ đã xác minh vẫn có thể đăng nhập nếu enabled=true và password đúng.
