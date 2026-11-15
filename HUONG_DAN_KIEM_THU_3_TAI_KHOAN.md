# TalentMatch – dữ liệu kiểm thử tập trung (03 tài khoản)

## Phạm vi
- 01 Admin (cần giữ để thử quản trị), 01 Client, 01 Freelancer.
- Các tình huống hiện có trong dump: báo giá chờ / đàm phán / từ chối / rút, chưa báo giá; hợp đồng chờ ký / đã ký chờ thanh toán; dự án thực hiện theo mốc / chờ nghiệm thu / đã hoàn thành / tranh chấp / thanh toán thất bại; rút tiền nhiều trạng thái.
- Hai bộ A/B cho mỗi tình huống gốc, sử dụng mã công việc khác nhau. Mục đích là test thao tác độc lập.
- Đã Việt hóa các trạng thái dạng enum thô ở các trường th:text trực tiếp và phần lớn mã thô còn lại qua trình duyệt. Không đổi mã trạng thái database, vì Java enum phụ thuộc chúng.

## Tài khoản
| Vai trò | Email | Mật khẩu test |
|---|---|---|
| Admin | admin@talentmatch.vn | 123456 |
| Client | client@talentmatch.vn | 123456 |
| Freelancer | freelancer@talentmatch.vn | 123456 |

Cài mật khẩu mới đủ mạnh trước khi triển khai công khai. Mật khẩu chung chỉ phục vụ môi trường demo cục bộ.

## Cài đặt (SẼ XÓA dữ liệu cũ trong database)
1. Sao lưu database `talentmatch_pro` hiện tại.
2. Dừng Spring Boot.
3. Trong MySQL Workbench, import file `TalentMatch_Database_3_TaiKhoan.sql` vào MySQL, đúng schema `talentmatch_pro` (dump gốc có các lệnh DROP TABLE). **Không import lên database sản xuất.**
4. Trong `application.properties`, đảm bảo ứng dụng kết nối `talentmatch_pro` và `app.demo.seed=false` (bản source này đã đổi mặc định). Kiểm tra biến môi trường `APP_DEMO_SEED` không được đặt thành `true`.
5. Chạy ứng dụng và đăng nhập 3 tài khoản.
6. Dùng đoạn kiểm tra cuối SQL để đối chiếu số lượng bản ghi.

**Lưu ý**: `TEST_DATA_3_TAI_KHOAN.sql` trong source là phần chuyển đổi *sau khi import đúng dump cũ*. Nếu đã dùng file SQL đầy đủ, không chạy thêm phần chuyển đổi lần nữa vì khóa ID bộ B sẽ trùng. `TalentMatch_Database_3_TaiKhoan.sql` là file SQL đầy đủ đi kèm ngoài ZIP.

## Cơ chế dữ liệu
- Giữ nguyên ID admin 1; Client 20; Freelancer 27 để giữ liên kết từ code và các dự án mẫu.
- Giữ các công việc kiểm thử ID 385–396 và công việc 60, cùng các ứng tuyển / dự án liên quan.
- Nhân bản dữ liệu cùng tình huống sang ID + 1000: ví dụ project 11 và 1011.
- Chuyển các quan hệ người dùng của những bản ghi giữ lại sang ID 20/27.
- Giữ giao dịch, milestone, báo giá, bản bàn giao liên quan; loại bỏ dữ liệu mẫu khác.

## Giới hạn kiểm thử
- Dữ liệu nhân bản là **mẫu cùng trạng thái**, không phải bằng chứng mọi luồng chuyển trạng thái đã được chạy và xác nhận.
- Các tình huống cần nhiều actor Freelancer khác nhau (chẳng hạn cạnh tranh ứng tuyển cùng một công việc) không thể mô phỏng hoàn chỉnh chỉ bằng 01 Freelancer.
- Không chuyển tiền thật; dữ liệu VNPAY/rút tiền trong SQL chỉ phục vụ kiểm thử.
- Chưa xác minh thực thi import và chạy Maven trên MySQL thật trong môi trường tạo file; cần chạy test integration tại máy người dùng.
