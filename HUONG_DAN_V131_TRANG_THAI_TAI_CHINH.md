# TalentMatch V131 – Admin thanh toán và rút tiền

- Bỏ khối giải thích “Chính sách giải ngân demo” và khối giải thích nghiệp vụ trong chi tiết giao dịch.
- Giữ bốn ô số liệu ngắn gọn: phí đã ghi nhận, tiền thực tế ghi có ví, chờ nghiệm thu, chờ giải ngân.
- Tổng phí và tiền ghi có ví được lấy từ `wallet_credits` (bút toán có thật trong dữ liệu), thay vì ước lượng từ trạng thái `DISBURSED`.
- Phí và tiền sau phí khi chưa có bút toán ví được ghi nhãn **dự kiến**, tránh hiểu nhầm đã thu và chi tiền.
- Tô màu trạng thái ở bảng và chi tiết giao dịch.

## Phân biệt trạng thái

- `Payment.PENDING`: giao dịch đã tạo, chưa xác nhận thanh toán.
- `Payment.PAID`: xác nhận thanh toán trong hệ thống demo, chưa ghi có ví; nếu `accepted_at` trống thì chờ nghiệm thu, nếu có thì chờ đến hạn giải ngân.
- `Payment.DISBURSED`: đã đánh dấu giải ngân; chỉ coi là đã ghi có khi tồn tại dòng tương ứng trong `wallet_credits`.
- `Payment.FAILED`: thanh toán thất bại.
- `Payment.REFUNDED`: đã ghi nhận hoàn tiền trong hệ thống demo.
- `WalletWithdrawal.PENDING`: chờ Admin duyệt; tiền vẫn bị giữ.
- `WalletWithdrawal.APPROVED`: Admin duyệt, chưa hoàn tất thao tác rút.
- `WalletWithdrawal.COMPLETED`: hoàn tất *mô phỏng*, không có bằng chứng chuyển tiền thực tế.
- `WalletWithdrawal.REJECTED`: từ chối; số dư giữ được trả về khả dụng bằng phép tính ví.

## Điều chưa được xác nhận

Chưa chạy kiểm thử tích hợp Maven + MySQL và đối soát nhiều luồng trùng/hoàn tiền/tranh chấp. VNPay Sandbox không thực hiện chuyển tiền ngân hàng thật.
