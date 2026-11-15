# TalentMatch V125 — Tên dự án và phần trăm tiến độ

## Tính toán
- Tiến độ báo cáo = ROUND(Σ giá trị mốc × phần trăm tiến độ từng mốc / Σ giá trị các mốc).
- Tỷ lệ nghiệm thu = ROUND(Σ giá trị mốc đã nghiệm thu / Σ giá trị các mốc × 100).
- Số liệu phản ánh tiến độ lưu trong bảng milestones, **không suy diễn trạng thái SUBMITTED thành 100%**.
- Ví dụ: 4.000.000đ × 100% và 6.000.000đ × 40% => 64% báo cáo, 0% nghiệm thu nếu chưa mốc nào COMPLETED.
- Trọn gói không chia mốc hiển thị dấu — hoặc "Chưa chia mốc", thay vì gán 0% không có ý nghĩa.

## Các thay đổi
1. `ProjectDisplay.java`: chuyển các tên TMTEST và mã trạng thái trong tên mốc thành tên dễ đọc, giữ định danh dự án và nhãn bản A/B. Không sửa tên trong database, tránh ảnh hưởng dữ liệu/test lịch sử.
2. `ProgressMetrics.java`: một công thức tính tiến độ/ nghiệm thu dùng chung cho Freelancer và Client.
3. `FreelancerWorkflowController.java`: sử dụng công thức chung; tính số milestone để hiển thị nhãn phù hợp.
4. `CustomerProgressWorkspaceController.java`: sử dụng công thức chung.
5. `freelancer/progress-deliveries.html`: hiển thị tên mới, ý nghĩa %, cảnh báo dữ liệu lịch sử không nhất quán.
6. `customer/progress.html`: tên và % thống nhất, không hiện nút nghiệm thu nếu mốc SUBMITTED <100%; cho phép Client yêu cầu chỉnh sửa.
7. `ProjectService.java`: kiểm tra 100% ở nghiệp vụ nghiệm thu; từ chối chấp nhận / giải ngân sai.
8. CSS của 2 trang: bổ sung kiểu cảnh báo dữ liệu cũ.

## Trường hợp dữ liệu cũ "đã gửi" mà vẫn 40%
Đây là bản ghi từ dữ liệu test cũ (tạo trước quy tắc bàn giao 100%).
Không chỉnh `progress_percent` tự động, vì đó là làm sai dữ liệu lịch sử. Để demo:
1. Client mở trang Tiến độ & Nghiệm thu, chọn mốc 40%, chọn "Yêu cầu chỉnh sửa".
2. Freelancer cập nhật tiến độ thực tế lên 100% bằng báo cáo mới và đính kèm minh chứng.
3. Freelancer gửi bản bàn giao mới.
4. Client xác nhận nghiệm thu; khi đó giải ngân tương ứng mốc.

## Giới hạn kiểm chứng
Đã kiểm tra tĩnh các file được thay đổi. Chưa chạy tích hợp Maven + MySQL / trình duyệt trong môi trường này.
