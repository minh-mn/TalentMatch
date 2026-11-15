# TalentMatch — Giai đoạn 2 (Freelancer workflow)

Nguồn: bản TalentMatch Giai đoạn 1 đã chuẩn hóa Maven/JPA. Không đổi tên các route chính và không thay thế giao diện đã có.

## Những phần đã thay đổi

1. **Ứng tuyển** (`JobService`, `JobController`, `jobs/apply.html`, `jobs/detail.html`): API POST từ chối dự án hết hạn hoặc đã đóng; kiểm tra tài khoản bị khóa/chưa kích hoạt. Giao diện không đưa nút gửi đề xuất với dự án hết hạn.
2. **Lời mời** (`TalentWorkflowController`): trạng thái `SENT`, `ACCEPTED`, `DECLINED` được giữ; không được chấp nhận lời mời của dự án đã đóng/hết hạn. Từ chối vẫn cho phép.
3. **Việc quan tâm** (`FreelancerController`): lọc trình độ `BEGINNER`, `MIDDLE`, `SENIOR` thống nhất với các mức kinh nghiệm thực tế (`INTERN/FRESHER/JUNIOR`, `MIDDLE/INTERMEDIATE`, `SENIOR/EXPERT`).
4. **Trang chủ Freelancer** (`FreelancerController`, `MatchingService`, `DashboardService`, `freelancer/dashboard.html`): dự án gợi ý theo điểm phù hợp, bỏ công việc đã ứng tuyển; biểu đồ hoạt động 7 tháng được tính từ đơn ứng tuyển và thời điểm hoàn thành dự án thật (không còn cột cố định minh họa). Có trạng thái rỗng khi chưa có số liệu.
5. **Dự án đang làm** (`FreelancerController`, `freelancer/projects.html`): lọc hạn theo deadline hợp đồng (nếu được đặt), nếu không có thì dùng deadline của tin tuyển; tiến độ được tính theo **trọng số tiền của milestone**, ghi rõ đây là ước tính trạng thái, chưa phải số % công việc báo cáo thực tế.
6. **Bàn giao** (`ProjectController`, `ProjectService`, `projects/detail.html`): tích hợp biểu mẫu tải file hoặc gửi liên kết HTTPS và lịch sử phiên bản bàn giao; yêu cầu có bản bàn giao trước khi gửi/duyệt nghiệm thu. Đảm bảo Client/Freelancer vẫn dùng logic phân quyền có sẵn trong `DeliveryService`.
7. **Kiểm thử**: thêm `Stage2WorkflowTest` và `Stage2MilestoneTest`.

## Phạm vi chưa triển khai

- Chưa thêm thương lượng báo giá nhiều phiên bản và hợp đồng phiên bản mới (giai đoạn tiếp theo).
- Chưa xử lý rút tiền ngân hàng, giải ngân thực hoặc quản lý eKYC.
- Chưa có nhật ký % thực hiện thực do Freelancer nhập; tiến độ hiện mới là ước tính từ trạng thái milestone.
- Các KPI doanh thu vẫn theo dữ liệu `Payment` đang dùng, chưa phải khoản thực nhận sau mọi phí và đối soát ngân hàng.
- Chưa khẳng định các luồng đã chạy thành công trên MySQL của người dùng.

## Database

**Giai đoạn 2 không tạo bảng/cột mới**; sử dụng schema của Giai đoạn 1. Tệp SQL `database/TalentMatch_GD1_MIGRATION_FIX_1452.sql` chỉ phục vụ người chưa hoàn thành migration Giai đoạn 1. Không chạy lại script fresh demo vì script đó xóa các bảng hiện có.
