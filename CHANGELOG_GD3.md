# TalentMatch — Giai đoạn 3: Báo giá, hợp đồng và tiến độ

## Những phần đã triển khai

### 1. Thương lượng báo giá (Client ↔ Freelancer)
- Thêm `QuoteRevision`, `QuoteStatus`, `QuoteRevisionRepository`, `QuoteService`, `QuoteController`.
- URL chung cho hai vai trò: `/quotations/application/{id}`.
- Mỗi lần đề xuất ghi phiên bản, người đề xuất, giá, thời gian, phạm vi, chính sách chỉnh sửa và ngày giờ.
- Chỉ bên còn lại được chấp nhận/từ chối. Có lưu lịch sử `PENDING`, `SUPERSEDED`, `ACCEPTED`, `REJECTED`.
- Khi chốt báo giá, cập nhật `JobApplication.proposedPrice` và `estimatedDays`.
- Client chưa được chọn Freelancer khi báo giá mới nhất chưa được chốt; đơn cũ chưa có vòng thương lượng vẫn hoạt động.
- Có nút liên kết từ trang `freelancer/applications.html` và `customer/applicants.html`.

### 2. Phiên bản điều khoản hợp đồng
- Thêm `AgreementRevision`, `AgreementRevisionRepository`, `AgreementService`.
- Lưu snapshot điều khoản, SOW, giá, thời gian và thời điểm Client/Freelancer ký từng phiên bản.
- Client được sửa điều khoản ở trạng thái `WAITING_PAYMENT` khi chưa tạo giao dịch thanh toán; sửa sẽ xóa trạng thái ký của phiên bản hiện tại.
- Form ký bắt buộc gửi `agreementVersion`; một form cũ không thể ký nhầm phiên bản mới.
- Hợp đồng cũ được lưu snapshot tương ứng khi mở chi tiết; giữ chữ ký cũ nếu có.
- Trang `/projects/{id}` hiển thị hợp đồng hiện hành và lịch sử các phiên bản.

### 3. Nhật ký tiến độ theo milestone
- Thêm `ProgressUpdate`, `ProgressUpdateRepository`, `ProgressService`.
- Thêm `milestones.progress_percent` (0..100); lịch sử lưu nội dung và link minh chứng HTTPS.
- Chỉ Freelancer của đúng dự án được báo cáo khi milestone `IN_PROGRESS`, dự án đang chạy, hợp đồng đã ký; tỷ lệ không giảm.
- Tính tiến độ theo trọng số giá trị milestone. Tách tiến độ **báo cáo** và tiến độ **được nghiệm thu**.
- Khi Client nghiệm thu, milestone chuyển 100%; không thay đổi logic bàn giao có ở Giai đoạn 2.
- Trên chi tiết dự án có biểu mẫu cập nhật và lịch sử toàn bộ lần cập nhật.

### 4. Sửa lỗi từ Giai đoạn 2
- Thêm `import java.time.LocalDate;` cho `TalentWorkflowController.java` (khắc phục lỗi build bạn chụp).

## File mới
- `src/main/java/com/talentmatch/model/{QuoteRevision,QuoteStatus,AgreementRevision,ProgressUpdate}.java`
- `src/main/java/com/talentmatch/repository/{QuoteRevisionRepository,AgreementRevisionRepository,ProgressUpdateRepository}.java`
- `src/main/java/com/talentmatch/service/{QuoteService,AgreementService,ProgressService}.java`
- `src/main/java/com/talentmatch/controller/QuoteController.java`
- `src/main/resources/templates/quotations/detail.html`
- `src/main/resources/static/css/talentmatch-gd3.css`
- `src/test/java/com/talentmatch/service/Stage3WorkflowTest.java`
- `database/TalentMatch_GD3_MIGRATION_SAFE.sql`

## File chỉnh sửa
- `ProjectService.java`, `ProjectController.java`, `TalentWorkflowController.java`
- `Milestone.java`, `JobApplicationRepository.java`, `MilestoneRepository.java`, `ProjectRepository.java`
- `templates/projects/detail.html`, `templates/freelancer/applications.html`, `templates/customer/applicants.html`

## Giới hạn phiên bản
- Chưa triển khai thanh toán tiền thật, dịch vụ ký số pháp lý hay rút tiền qua ngân hàng.
- Giai đoạn 3 không thêm chức năng khiếu nại nâng cao (để thực hiện giai đoạn tiếp theo).
- Chưa chạy Maven test và MySQL integration trong môi trường tạo gói; cần chạy `mvn clean test` trên máy có Maven và MySQL.
