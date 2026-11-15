# TalentMatch Client V127 – Hợp đồng, Tiến độ & Nghiệm thu

## Phần đã cập nhật

- Sửa bố cục trang `customer/contracts`: khối giới thiệu đứng ngoài CSS grid; danh sách hợp đồng và chi tiết hiện song song, tránh một ô trống chiếm 50% không gian.
- Gắn trạng thái hợp đồng đúng hơn: chờ ký, đã ký nhưng chờ thanh toán, đang thực hiện, hoàn thành, tranh chấp, đã hủy.
- Bộ lọc hợp đồng có thêm **Đã ký – chờ thanh toán** và **Tranh chấp**. Bộ lọc ``Đang hiệu lực`` không bao gồm hợp đồng đã ký nhưng chưa thanh toán.
- Chi tiết hợp đồng hiển thị chữ ký Client/Freelancer, phiên bản, tiến trình ký → thanh toán → nghiệm thu và liên kết đến các trang liên quan.
- Trang Tiến độ & Nghiệm thu hiện 3 thống kê; làm rõ deadline, giá trị và trạng thái từng milestone bằng màu; bổ sung điều kiện khi nghiệm thu.
- Trong Controller, chỉ cho Client nghiệm thu milestone đã có deadline và tiến độ 100% kèm bản bàn giao mới nhất; việc ghi nhận giải ngân vẫn qua ProjectService.
- Dự án trọn gói (không có milestone) dẫn sang chi tiết dự án để nghiệm thu toàn bộ; không tự ý tạo milestone hay tự giải ngân.
- Giữ các mã trạng thái Java/SQL như cũ để tránh phá vỡ logic.

## Các file đã sửa

- `src/main/java/com/talentmatch/controller/CustomerContractWorkspaceController.java`
- `src/main/java/com/talentmatch/controller/CustomerProgressWorkspaceController.java`
- `src/main/resources/templates/customer/contracts.html`
- `src/main/resources/templates/customer/progress.html`
- `src/main/resources/static/css/client-workflow-v127.css` (mới)

## Kiểm thử cần chạy tại máy

1. `mvn clean test` rồi `mvn spring-boot:run`.
2. Client mở `/customer/contracts`: lọc trạng thái, mở hợp đồng, ký đúng version; thử hợp đồng đã ký/chưa thanh toán.
3. Client mở `/customer/progress`: lọc dự án theo trạng thái/loại; chọn mốc có deadline và bản bàn giao; thử xác nhận nghiệm thu và yêu cầu sửa.
4. Đảm bảo tiền chỉ chuyển sang `DISBURSED` sau nghiệm thu hợp lệ và Freelancer nhìn thấy đồng bộ.

**Không import lại database.** Bản này chưa được kiểm thử tích hợp chạy thật trên MySQL và Spring Boot, nên cần thực hiện các ca trên trước khi demo chính thức.
