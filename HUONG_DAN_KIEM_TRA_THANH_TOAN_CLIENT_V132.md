# TalentMatch V132: Kiểm tra logic thanh toán Client

## Đã chỉnh
- Tách trạng thái milestone (`UNPAID`, `PAID`, `IN_PROGRESS`, `SUBMITTED`, `COMPLETED`) khỏi trạng thái giao dịch (`PENDING`, `PAID`, `DISBURSED`, `FAILED`, `REFUNDED`).
- Lấy nhãn thanh toán từ giao dịch thực tế của từng milestone thay vì suy từ tiến độ.
- Nếu trạng thái milestone và lịch sử giao dịch không nhất quán, hiển thị `Cần đối soát` và không tạo nút thanh toán tự động.
- Giao dịch đã thanh toán trước khi thêm trường hạn: hiển thị `Dữ liệu cũ chưa ghi nhận`, không thay đổi lịch sử.
- Giữ nguyên quy tắc hạn thanh toán, quyền Client, chữ ký và chặn giao dịch trùng của PaymentService.

## Còn cần hoàn thiện trước khi triển khai thật
- Chốt lịch thanh toán ngay khi ký hợp đồng; đổi lịch phải có phụ lục và đồng ý hai phía.
- Bổ sung ví Client/escrow thực và bút toán kép nếu yêu cầu nạp ví trước.
- Chạy kiểm thử tích hợp với MySQL và VNPAY Sandbox IPN, nhất là callback thất bại/trùng lặp và hoàn tiền.
- Số tiền đã trả đang tính theo trạng thái PAID+DISBURSED, không trừ REFUNDED nếu bản ghi hoàn tiền tách riêng.

## Kịch bản demo
1. Một lần: hợp đồng ký + hạn thanh toán -> thanh toán một lần -> PAID -> nghiệm thu -> chờ giải ngân -> DISBURSED.
2. Theo mốc: tổng các mốc bằng hợp đồng, mỗi mốc có deadline bàn giao và hạn thanh toán; thanh toán từng mốc; báo cáo tiến độ, bàn giao 100%, nghiệm thu riêng rồi giải ngân riêng.
3. Thanh toán thất bại: FAILED không tính là đã trả; có thể tạo lại giao dịch nếu mốc thực sự chưa trả.
4. Giao dịch PENDING: không tạo thêm yêu cầu thanh toán trùng.
5. Dữ liệu cũ: mốc đã trả mà thiếu hạn phải hiện `Dữ liệu cũ chưa ghi nhận`.
