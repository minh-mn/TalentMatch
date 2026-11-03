# TalentMatch — Bản Giai đoạn 3 (Maven)

**Bạn đang sử dụng bản Giai đoạn 3.** Trước khi chạy Spring Boot trên MySQL đang có dữ liệu, hãy mở và chạy `database/TalentMatch_GD3_MIGRATION_SAFE.sql` sau khi sao lưu database. **Không chạy lại SQL Fresh Demo** để tránh mất dữ liệu.

Hướng dẫn chi tiết: [README_GD3.md](README_GD3.md) — thay đổi: [CHANGELOG_GD3.md](CHANGELOG_GD3.md).

---

# TalentMatch – Giai đoạn 1: Đồng bộ Maven + MySQL V33

Bản này được chỉnh trực tiếp từ **`TalentMatch-main (2)(3).zip`** và script **`TalentMatch_database(20261031-110059).sql`**. Toàn bộ giao diện hiện có, Controller, Service, tệp tĩnh và tài nguyên đều được giữ lại trong **một project Maven duy nhất**.

## 1. Môi trường

- Windows 10/11, macOS hoặc Linux; khuyến nghị **JDK 21**.
- **IntelliJ IDEA** có hỗ trợ Maven, hoặc Maven 3.9+ cài ngoài.
- **MySQL Server 8+** và MySQL Workbench.
- Backend Spring Boot 3.4.5; truy cập `http://localhost:8080`.
- **Không cần Docker, Gradle hoặc Node.js** để chạy Spring Boot + Thymeleaf.

## 2. Chọn cách xử lý database – QUAN TRỌNG

Project có hai file SQL **khác mục đích** trong thư mục `database/`:

| Trường hợp | File SQL | Tác động |
|---|---|---|
| **Môi trường demo riêng, database có thể xóa** | `TalentMatch_V33_GD1_FRESH_DEMO.sql` | **XÓA TẤT CẢ BẢNG TRONG `talentmatch_pro` RỒI TẠO LẠI**, nạp dữ liệu mẫu. |
| **MySQL đã có tài khoản/dự án quan trọng** | `TalentMatch_GD1_MIGRATION_SAFE.sql` | Không `DROP TABLE`; bổ sung cột/bảng còn thiếu, chỉnh một số trạng thái không nhất quán. |

**Không bao giờ chạy file `FRESH_DEMO` trên database đang dùng mà chưa backup.** Để sao lưu, trong MySQL Workbench vào *Server → Data Export*, chọn `talentmatch_pro` và xuất dữ liệu + cấu trúc; hoặc dùng `mysqldump` nếu đã cài công cụ MySQL CLI.

### Cài mới bằng dữ liệu mẫu

1. Mở MySQL Workbench, kết nối server MySQL của bạn.
2. Nếu chưa có DB, file fresh sẽ tự tạo `talentmatch_pro`.
3. Mở `database/TalentMatch_V33_GD1_FRESH_DEMO.sql`.
4. **Đảm bảo đây là database demo có thể xóa**, sau đó mới Execute toàn bộ.
5. Xem `SELECT` kiểm tra số lượng bản ghi ở cuối script. Dự kiến có **19 tài khoản, 17 công việc, 9 ứng tuyển, 3 dự án, 5 milestone, 3 bản bàn giao, 3 thanh toán**.

### Giữ nguyên database đang có

1. **Sao lưu dữ liệu trước**.
2. Mở và chạy `database/TalentMatch_GD1_MIGRATION_SAFE.sql`.
3. File này **không thêm tài khoản demo, không xóa bảng**, nhưng có các thay đổi schema và chuẩn hóa dữ liệu:
   - `job_invitations.status`: `PENDING` → `SENT` (chỉ bản ghi đang ở `PENDING`).
   - `payments.status`: `PAID` → `DISBURSED` **chỉ khi `disbursed_at` đã có**.
   - `milestones.status`: `PAID` → `COMPLETED` **chỉ khi `completed_at` đã có**.
   - Bàn giao cũ có link HTTPS trong `file_url` được sao chép vào `external_url`; tài khoản Freelancer từ `projects` được điền vào `submitter_id` khi đang trống.
4. Script sẽ liệt kê những dự án có tranh chấp mở nhưng `project.status` chưa là `DISPUTED`; hãy kiểm tra trước khi tự sửa trạng thái. Tệp migration cố tình không thay đổi hàng loạt trạng thái các dự án đang vận hành.
5. Nếu DB thuộc phiên bản cũ chưa có đủ bảng nền tảng, hãy kiểm tra bằng MySQL Workbench trước khi chạy migration và sao lưu toàn bộ dữ liệu.

**Lưu ý:** Hibernate vẫn đang dùng `spring.jpa.hibernate.ddl-auto=update` giống bản gốc. Sau khi đồng bộ có thể chuyển sang `validate` ở môi trường ổn định, nhưng cần kiểm tra schema đầy đủ trước.

## 3. Chạy bằng IntelliJ IDEA

1. Mở thư mục chứa **`pom.xml`** (không mở nhầm thư mục `src` hoặc `target`).
2. Chọn Project SDK **Java 21** và **Reload Maven Project**.
3. Vào *Run → Edit Configurations → Environment variables*, cấu hình:

```text
DB_USERNAME=root
DB_PASSWORD=MAT_KHAU_MYSQL_CUA_BAN
APP_DEMO_SEED=false
APP_MAIL_ENABLED=false
```

Nếu MySQL không chạy trên máy hoặc không dùng port 3306, đặt thêm `DB_URL` cho phù hợp.

4. Chạy `com.talentmatch.TalentMatchApplication` bằng nút Run của IntelliJ.
5. Mở trình duyệt: `http://localhost:8080`.

`APP_MAIL_ENABLED=false` giúp chạy demo khi chưa có SMTP. **Khi tắt mail, không thể kiểm thử nhận OTP Gmail thực tế**; để dùng đăng ký OTP cần cấu hình Gmail App Password và bật lại mail theo `OTP_EMAIL_SETUP.md`.

### Chạy bằng Terminal

```bash
mvn clean test
mvn spring-boot:run
```

Maven sẽ tải dependencies lần đầu qua mạng. Đối với IntelliJ, có thể chạy bằng Maven tích hợp mà không cần cài Maven hệ thống.

## 4. Khác biệt chính so với bản gốc

- Loại bỏ các bản sao `src/src/...`, file build `target/`, cấu hình `.idea/` không cần thiết. **Chỉ giữ mã nguồn Maven chuẩn và tài nguyên trang web.**
- Đồng bộ bảng `project_deliveries` với Java: `submitter_id`, `version`, `storage_key`, `external_url` và `file_name` nullable.
- Chuyển 3 lời mời demo sang trạng thái `SENT`; Java lưu `responded_at` khi Freelancer phản hồi.
- Đồng bộ seed: hai payment có `disbursed_at` thành `DISBURSED`; milestone đã hoàn thành thành `COMPLETED`.
- Thêm `Dispute.java` và `DisputeRepository.java`; yêu cầu hủy dự án đã có tiền demo tạo hồ sơ tranh chấp, Admin xử lý cập nhật hồ sơ đó.
- Gắn lại ứng tuyển #9 với đúng công việc banner #17; đóng các job đã có hợp đồng và gia hạn các job demo `OPEN` bị quá hạn tại thời điểm import.
- Đồng bộ thêm các bảng Java dùng nhưng SQL V33 còn thiếu: OTP pending, chat, cộng đồng, tài liệu doanh nghiệp, lưu Freelancer.
- **Mặc định `APP_DEMO_SEED=false`** để tránh `DataInitializer` tạo thêm hàng trăm công việc khi đã import SQL demo.
- Bổ sung kiểm tra cấu trúc:

```bash
python scripts/verify_stage1.py
```

## 5. Phạm vi chưa hoàn thiện trong giai đoạn 1

- Chưa triển khai thương lượng báo giá nhiều phiên bản, hợp đồng nâng cao, nhật ký tiến độ, rút tiền thật hoặc cổng ký quỹ thật.
- Các bảng `freelancer_portfolios`, `user_bank_accounts`, `withdrawal_requests`, `vnpay_transactions` có trong database V33, nhưng **chưa có đầy đủ UI/Service Java tương ứng**; giữ nguyên để phát triển ở các giai đoạn sau.
- Giải ngân, hoàn tiền trong phần nghiệp vụ hiện tại vẫn là **mô phỏng**, không phải chuyển khoản ngân hàng thực tế.
- `app.demo.seed=false` không sinh thêm tài khoản nếu bạn chạy với database trống; để dùng demo nên import file SQL fresh trong database demo.
- Bản này đã được **kiểm tra tĩnh schema/Entity bằng script**. Chưa thực hiện `mvn clean test` và chưa chạy MySQL thực tế trong môi trường tạo tệp, nên cần kiểm thử sau khi tải về.

## 6. Kiểm thử thủ công nên thực hiện

1. Đăng nhập tài khoản Freelancer mẫu đã nạp qua SQL.
2. Mở Tìm dự án → kiểm tra job OPEN và deadline mới.
3. Vào Lời mời → Chấp nhận / Từ chối các lời mời chưa xử lý.
4. Vào Dự án đang làm → kiểm tra milestone đã nghiệm thu và số tiền giải ngân.
5. Truy cập bàn giao cũ: liên kết HTTPS cần hiển thị, không yêu cầu file vật lý chưa có.
6. Với Admin, xem dự án số 1 ở phần Tranh chấp; chọn **Tiếp tục** để kiểm tra đồng bộ `disputes` và `projects`.
7. Chạy `mvn clean test` rồi thử ứng tuyển, bàn giao, nghiệm thu trên **database thử nghiệm**, không thao tác vào dữ liệu thật.

## 7. Bảo mật

Không công khai `DB_PASSWORD`, `MAIL_PASSWORD`, App Password Gmail hoặc thông tin thanh toán thật trên GitHub. Dữ liệu demo trong SQL là dữ liệu kiểm thử, **không dùng cho sản phẩm vận hành thực tế**. Hãy thay mật khẩu mặc định của các tài khoản mẫu trước khi triển khai public.
