# TalentMatch – Email xác minh và khôi phục mật khẩu 5 phút

## Chạy trên IntelliJ

1. Khởi động MySQL với database `talentmatch_pro` (Hibernate `ddl-auto=update` sẽ tự tạo bảng `pending_registrations`).
2. Tạo **Gmail App Password** từ một tài khoản Google đã bật xác minh 2 bước; không dùng mật khẩu đăng nhập Gmail thường.
3. Trong IntelliJ > Run > Edit Configurations > Environment variables, cấu hình:
   - `APP_MAIL_ENABLED=true`
   - `MAIL_USERNAME=your-sender@gmail.com`
   - `MAIL_PASSWORD=your-16-character-app-password`
   - `APP_BASE_URL=http://localhost:8080`
   - `MAIL_HOST=smtp.gmail.com`
   - `MAIL_PORT=587`
4. Chạy `TalentMatchApplication`. Truy cập `http://localhost:8080/register`.

**Không commit App Password vào GitHub.** File application.properties lấy giá trị SMTP từ environment variables.

## Đăng ký

- Họ tên/email/mật khẩu/vai trò được kiểm tra. Mật khẩu mã hóa BCrypt.
- Gửi link qua SMTP. Nếu SMTP ném lỗi, transaction rollback và yêu cầu đăng ký tạm không được lưu.
- Chỉ dữ liệu chờ xác minh nằm trong `pending_registrations`; **chưa có bản ghi `users`**.
- Khi click link trong vòng 5 phút, thêm `users` với `enabled=true` và xóa yêu cầu tạm.
- Quá hạn: không tạo `users`, cần đăng ký lại. Link đã dùng không thể dùng lại.
- Yêu cầu tạm hết hạn vẫn có thể tồn tại cho đến khi cùng email đăng ký lại hoặc được dọn dẹp; chúng không phải tài khoản và không thể đăng nhập.

## Quên mật khẩu

- Chỉ email thuộc tài khoản đã xác minh mới được gửi link reset.
- Link reset hiệu lực 5 phút, sử dụng một lần; phát hành link mới vô hiệu hóa các link cũ chưa sử dụng.
- Mật khẩu mới được mã hóa BCrypt.
- Thông báo ở trang quên mật khẩu không tiết lộ email có tồn tại hay không.

## Kiểm thử đề xuất

- Đăng ký mới nhưng chưa xác minh: bảng `users` không tăng; đăng nhập thất bại.
- Xác minh trong vòng 5 phút: bảng `users` tăng một bản ghi, đăng nhập thành công.
- Mở lại link: thất bại. Mở link sau 5 phút: thất bại.
- Dừng SMTP rồi đăng ký: không tạo `users`, và `pending_registrations` rollback.
- Quên mật khẩu: link hợp lệ đặt lại được một lần, link cũ/đã hết hạn không được phép đổi.

## Lưu ý

Không thể xác minh việc gửi mail ra internet trong môi trường này. Cần kiểm thử với Gmail App Password thật tại máy của bạn. Nếu đã có tài khoản `users` cũ ở trạng thái chưa xác minh trong database từ bản trước, không thể đăng ký trùng email; cần xử lý những dòng này riêng (không tự xóa dữ liệu cũ).
