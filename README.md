# TalentMatch — Bản Giai đoạn 3 (Maven)

**Bạn đang sử dụng bản Giai đoạn 3.** Trước khi chạy Spring Boot trên MySQL đang có dữ liệu, hãy mở và chạy `database/TalentMatch_GD3_MIGRATION_SAFE.sql` sau khi sao lưu database. **Không chạy lại SQL Fresh Demo** để tránh mất dữ liệu.

Hướng dẫn chi tiết: [README_GD3.md](README_GD3.md) — thay đổi: [CHANGELOG_GD3.md](CHANGELOG_GD3.md).

## Các luồng hiện được hỗ trợ

- Đăng nhập trả người dùng về trang họ yêu cầu trước đó; nếu không có trang chờ, chuyển theo vai trò.
- Thanh toán VNPay Sandbox chỉ ghi nhận giao dịch sau IPN hợp lệ. Giao dịch thử bị bỏ dở được mở lại sau 15 phút; callback trình duyệt không tự xác nhận thanh toán.
- Gợi ý Freelancer dùng TF-IDF cosine trên tập hồ sơ cùng các yếu tố lĩnh vực, kinh nghiệm, ngân sách ước tính và trạng thái sẵn sàng. Đây là thuật toán khai phá dữ liệu có giải thích, không phải mô hình ngôn ngữ đã huấn luyện.

### Cách tính gợi ý Freelancer

Điểm gợi ý là điểm đối chiếu quy tắc từ 0–100, không phải xác suất được tuyển. Hệ thống chuẩn hóa văn bản tiếng Việt (bỏ dấu), loại một số từ dừng, tính TF-IDF/cosine trên nội dung tin và hồ sơ; điểm gồm tối đa 50 điểm tương đồng kỹ năng, 10 điểm tương đồng nội dung, 20 điểm đúng lĩnh vực, 10 điểm kinh nghiệm, 5 điểm ngân sách ước tính và 5 điểm sẵn sàng nhận việc. Trên mỗi hồ sơ, mục “Xem phân tích chi tiết” trình bày điểm đạt/tối đa, trọng số, kỹ năng khớp/chưa thấy trong hồ sơ, dữ liệu kinh nghiệm và phép tính ngân sách. Tổng điểm bằng tổng điểm thành phần. Ước tính ngân sách theo đơn giá giờ × 4 giờ/ngày × số ngày dự kiến, nên chỉ mang tính tham khảo.

Danh sách “Phù hợp nhất” sắp xếp điểm giảm dần, sau đó dùng đánh giá, kinh nghiệm và tên làm tiêu chí phụ ổn định. Khối “Top 3 gợi ý” luôn dựa trên điểm tương thích, không đổi thứ tự theo bộ lọc sắp xếp chính. Khi tin không nêu cấp kinh nghiệm nhận diện được, hệ thống không cộng điểm hoặc tuyên bố ứng viên đạt yêu cầu kinh nghiệm. Kết quả phụ thuộc vào chất lượng dữ liệu hồ sơ/tin tuyển và không tự động quyết định tuyển dụng.

Trang `/customer/talent` xếp hạng toàn bộ hồ sơ Freelancer đang hoạt động, vì vậy tổng số hồ sơ có thể giữ nguyên giữa các công việc. Điểm từng hồ sơ, thứ tự gợi ý và số hồ sơ “Phù hợp cao” (điểm từ 65 trở lên) được tính lại theo công việc đã chọn; bộ lọc tìm kiếm tiếp tục thu hẹp tập hồ sơ hiển thị. Không đồng nhất tổng số hồ sơ được xếp hạng với số ứng viên đã nộp đơn cho công việc.

Để trình diễn với nhiều hồ sơ Freelancer đa ngành, đặt `APP_DEMO_MATCHING_SEED=true` rồi khởi động lại ứng dụng. Bộ nạp tạo tối đa 12 hồ sơ mẫu có kỹ năng, lĩnh vực, kinh nghiệm, đơn giá và hình thức làm việc khác nhau; chỉ tạo email chưa tồn tại dạng `matching.demo.*@talentmatch.vn` và không sửa hồ sơ đã có. Mật khẩu các tài khoản mẫu được sinh ngẫu nhiên nên không dùng để đăng nhập. Tắt lại biến này sau khi nạp; dữ liệu mẫu đã tạo sẽ vẫn nằm trong database cho đến khi được xóa riêng. Chỉ bật trên database demo/kiểm thử.

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
APP_DEMO_MATCHING_SEED=false
APP_MAIL_ENABLED=false
VNPAY_TMN_CODE=
VNPAY_HASH_SECRET=
VNPAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:8080/payment/vnpay/return
```

Nếu MySQL không chạy trên máy hoặc không dùng port 3306, đặt thêm `DB_URL` cho phù hợp.
Để kiểm thử thanh toán VNPay Sandbox:

1. Đăng ký/được cấp tài khoản **VNPay Sandbox**; điền `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` từ cấu hình merchant vào biến môi trường Run Configuration. Không đưa các giá trị này vào source code.
2. VNPay phải gọi được IPN từ máy chủ của họ. Khi chạy local, tạo HTTPS tunnel tới cổng `8080` (ví dụ công cụ tunnel bạn tin cậy), rồi đăng ký URL IPN công khai `https://<ten-mien-cong-khai>/payment/vnpay/ipn` trong cấu hình merchant Sandbox. Đặt `VNPAY_RETURN_URL=https://<ten-mien-cong-khai>/payment/vnpay/return` để trình duyệt quay về đúng ứng dụng. Không đăng ký `localhost` làm IPN URL.
3. Khởi động lại ứng dụng, ký hợp đồng hai bên, thiết lập lịch/hạn thanh toán rồi bắt đầu thanh toán từ checkout. VNPay Sandbox chỉ dùng tài khoản/thẻ thử nghiệm do VNPay cung cấp; không dùng thông tin thanh toán thật.
4. Chỉ IPN hợp lệ mới chuyển giao dịch từ `PENDING` sang `PAID`; trang Return không tự xác nhận tiền. Kiểm tra lịch sử giao dịch sau khi VNPay báo thành công. Nếu IPN không tới, giao dịch có thể vẫn `PENDING`; xác nhận tunnel đang mở và URL IPN trong merchant trùng khớp.

`VNPAY_URL` mặc định trỏ tới Sandbox. Không đổi sang cổng production hoặc dùng khóa Sandbox cho giao dịch thật. Có thể đặt `APP_SECURITY_REMEMBER_ME_KEY` nếu cần giữ đăng nhập qua lần khởi động lại; mặc định khóa được tạo mới khi ứng dụng chạy.

### Tạo hợp đồng mẫu để thử thanh toán

Sau khi đã chạy ứng dụng ít nhất một lần để Hibernate cập nhật schema, có thể chạy `DATABASE_VNPAY_DEMO_CONTRACT.sql` trong MySQL Workbench. Script thêm một công việc, báo giá đã chốt và hợp đồng một lần trị giá 500.000đ, đã ký hai bên, hạn thanh toán sau 7 ngày; script không tạo sẵn giao dịch và có thể chạy lại an toàn. Cần có sẵn hai tài khoản demo `client@talentmatch.vn` và `freelancer@talentmatch.vn`; nếu dùng tài khoản do DataInitializer tạo, mật khẩu mặc định là `123456`. Chỉ chạy trên database demo/kiểm thử và cấu hình VNPay Sandbox trước khi thanh toán.

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
