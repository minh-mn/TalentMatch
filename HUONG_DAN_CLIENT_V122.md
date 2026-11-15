# TalentMatch – Client Workflow V122

Bản cập nhật từ V121, chỉ chỉnh `src/main` (Java controllers, HTML templates, CSS) cho các mục Client: Báo giá & Thương lượng, Hợp đồng & Điều khoản, Tiến độ & Nghiệm thu, Thanh toán & Giao dịch.

## Luồng demo
1. Client xem phiên bản báo giá, yêu cầu thương lượng hoặc chốt báo giá.
2. Chọn Freelancer từ báo giá đã được hai bên chấp nhận, tạo dự án và hợp đồng.
3. Client và Freelancer ký đúng phiên bản hợp đồng, Client thanh toán theo dự án hoặc từng mốc.
4. Freelancer cập nhật tiến độ và nộp bản bàn giao; Client chỉ nghiệm thu bản bàn giao mới nhất.
5. Sau nghiệm thu hợp lệ hệ thống ghi nhận giải ngân mô phỏng; Freelancer theo dõi thu nhập và tạo yêu cầu rút.

## Thay đổi
- `CustomerQuotationController`: bổ sung lọc ứng tuyển chưa có QuoteRevision và số liệu tương ứng; giữ các trạng thái phiên bản đã có.
- `CustomerPaymentsWorkspaceController`: trạng thái hợp lệ khác nhau cho tab dự án và tab giao dịch; không còn lọc chéo trạng thái; thêm sắp xếp theo số tiền, ngày; không cho hiển thị nút thanh toán ở dự án đã hoàn tất/hủy/tranh chấp.
- `CustomerProgressWorkspaceController`: lọc theo mốc/trọn gói, cần chỉnh sửa, chờ thanh toán, tranh chấp; giữ kiểm tra quyền sở hữu bản bàn giao, phiên bản bản bàn giao mới nhất, và giải ngân theo ProjectService.
- Bốn template Client: thanh hướng dẫn nghiệp vụ, đường dẫn nội bộ, nhãn trạng thái bằng tiếng Việt, bộ lọc rõ ràng hơn; CSS dùng chung `client-workflow-v122.css`.

## Kiểm thử cần thực hiện trên máy có MySQL/Maven
- `mvn clean test` và `mvn spring-boot:run`.
- Mỗi tab lọc có trạng thái riêng; không nhập trạng thái giao dịch vào tab dự án.
- Kiểm thử có/không có mốc, đã gửi/cần sửa/đã nghiệm thu.
- Kiểm thử thanh toán thất bại/đã giải ngân và dự án tranh chấp.

## Giới hạn
Chưa chạy Maven/MySQL trong môi trường đóng gói; chưa xác nhận luồng VNPAY Sandbox trên thực tế. Đây là thay đổi UI và lọc dữ liệu, không phải hệ thống escrow ngân hàng. Chưa tự động sinh dữ liệu mới.
