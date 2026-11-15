# TalentMatch V126 — Thanh toán, Thu nhập và Rút tiền Freelancer

## Sửa lỗi hiển thị trắng tab Rút tiền
- Loại bỏ các biểu thức điều kiện so sánh chứa dấu `<` trong thuộc tính HTML của các nút Thymeleaf; chuyển tính toán `withdrawEnabled` và `withdrawMax` sang controller.
- Không gọi phương thức BigDecimal trực tiếp ở biểu mẫu để tính `max`.
- Bổ sung lớp CSS `earnings-v126.css`, tránh ẩn tab/bảng lịch sử.
- Thêm kiểm tra null cho thông tin ngân hàng ở phần lọc/lịch sử (phòng trường hợp dữ liệu cũ không đầy đủ).

## Màu trạng thái
- Chờ thanh toán/yêu cầu chờ duyệt: cam.
- Đang thực hiện/đã giữ tiền/chờ nghiệm thu/đã duyệt: xanh dương.
- Hoàn thành/đã giải ngân/hoàn tất rút: xanh lá.
- Thất bại/tranh chấp/từ chối: đỏ.
- Hoàn tiền/hủy: tím.

## Phân biệt bộ lọc chính xác
- Theo dự án: lọc `ProjectStatus`.
- Giao dịch: lọc `PaymentStatus`.
- Rút tiền: lọc `WalletWithdrawal.State`.
- Mỗi lần đổi tab, URL mới không mang trạng thái bộ lọc tab cũ.

## Số liệu theo dự án
- `Client đã trả, đang giữ` = tổng các Payment `PAID` của chính dự án đó.
- `Đã giải ngân` = tổng các Payment `DISBURSED` của chính dự án.
- `Phí nền tảng đã trừ` = tổng WalletCredit.fee gắn với Payment của dự án.
- `Thực nhận sau phí` = tổng WalletCredit.net gắn với Payment của dự án.
- Thanh toán PENDING/FAILED/REFUNDED không được tính vào tiền có thể rút.
- `Số dư khả dụng` = tổng tiền ghi có ròng - yêu cầu rút đang giữ - yêu cầu rút hoàn tất.

## Cách chạy thử
1. Sao lưu dữ liệu nếu đã chỉnh trực tiếp trong MySQL.
2. Dùng source V126 thay V125, không import lại SQL.
3. Chạy `mvn clean test` rồi `mvn spring-boot:run` với Java 21.
4. Truy cập `/freelancer/earnings`, thử từng tab và bộ lọc.
5. Thử gửi rút 10.000đ, kiểm tra Admin duyệt, từ chối, hoàn tất, số dư và lịch sử.

**Giới hạn xác nhận:** Những thay đổi đã kiểm tra tĩnh. Môi trường tạo bản ZIP không có Maven hoặc MySQL chạy để kiểm thử tích hợp. Nếu trang còn trắng, xem thông báo lỗi Spring Boot tại terminal IntelliJ; đừng kết luận lỗi CSS chỉ từ màn hình trắng.
