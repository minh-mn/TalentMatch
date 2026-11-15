# TalentMatch Freelancer V123 - Sửa khu vực Rút tiền

- Đã bỏ dải thông báo màu vàng và nhãn vàng ở trang Rút tiền.
- Cập nhật CSS rút tiền để hiển thị hai cột (form và số dư/quy trình), bảng lịch sử bên dưới.
- Giữ nguyên POST `/freelancer/earnings/withdrawals`, tên tham số `bankId` và `amount`, kiểm tra mức tối thiểu, hạn mức, khóa số dư từ backend.
- Giữ nút gửi ở đầu và cuối form, nút đầu liên kết với `form=tm121-withdraw-form`.
- Chức năng vẫn là mô phỏng, không chuyển tiền thật.

Chạy `mvn clean spring-boot:run`, mở `/freelancer/earnings?tab=withdrawals`, Ctrl+F5.

Cần kiểm thử thủ công sau khi chạy: tài khoản ngân hàng tồn tại/không tồn tại, số dư đủ/không đủ, lọc lịch sử, trạng thái duyệt/từ chối, hai nút submit.
