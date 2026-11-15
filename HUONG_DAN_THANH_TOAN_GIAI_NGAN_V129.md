# TalentMatch V129 — Thanh toán & Giải ngân Admin (demo)

## Chính sách đã chọn cho demo
- Phí nền tảng: **10%** trên giá trị *được giải ngân*, không thu trước ở thời điểm Client trả tiền.
- Thời gian giữ sau nghiệm thu: **3 ngày lịch (72 giờ)**, tính từ thời điểm Client chấp nhận nghiệm thu.
- Trả tiền theo mốc: mỗi milestone độc lập, đúng số tiền và kết quả nghiệm thu của mốc đó.
- Trả trọn gói: nghiệm thu toàn bộ dự án rồi giữ 3 ngày.
- Ghi có ví sau phí: số tiền được giải ngân - phí nền tảng.
- Rút tiền: chỉ rút từ ví đã được ghi có; các yêu cầu chưa hoàn tất vẫn bị giữ riêng.
- Admin chỉ giám sát và kiểm toán trên trang Giao dịch & Thanh toán; không có nút ép giải ngân trái điều kiện.
- Toàn bộ giao dịch là **mô phỏng nội bộ**. VNPAY Sandbox không đại diện tiền ký quỹ thực.

## Ví dụ số tiền
Client trả 10.000.000đ → nghiệm thu → giữ 72 giờ → phí 10% = 1.000.000đ → Freelancer có 9.000.000đ.
Với mốc 4.000.000đ và 6.000.000đ: phí tương ứng 400.000đ và 600.000đ; nhận 3.600.000đ và 5.400.000đ, độc lập theo từng nghiệm thu.

## Trạng thái logic
- PENDING: chưa được thanh toán thành công, chưa có tiền giữ.
- PAID, chưa có accepted_at: Client đã trả (demo), tiền đang giữ, chưa nghiệm thu.
- PAID, có accepted_at: đã nghiệm thu, chờ đến scheduled_disbursement_at.
- DISBURSED: đã ghi nhận giải ngân nội bộ, hệ thống tạo đúng một WalletCredit theo payment_id.
- FAILED/REFUNDED: không phát sinh ghi có Freelancer.
- Dự án DISPUTED/CANCELLED: không được giải ngân tự động.

## Cấu hình
`src/main/resources/application.properties`:
- `talentmatch.settlement.hold-days=3`
- `talentmatch.settlement.check-ms=60000` (quét các giao dịch đủ điều kiện mỗi 60 giây khi server hoạt động)
- `talentmatch.wallet.demo-fee-percent=10`

Để demo luồng nhanh ở một dự án **mới**, có thể thay `hold-days=0` *trước khi thực hiện nghiệm thu* rồi khởi động lại server. Không làm vậy cho thanh toán thật.

## Database
Khi Hibernate đang đặt `spring.jpa.hibernate.ddl-auto=update`, 3 cột mới sẽ được tạo khi ứng dụng khởi động. Nếu quản lý schema thủ công, chạy `DATABASE_SETTLEMENT_V129.sql` **một lần trên database đã sao lưu**. Không chạy cả hai cách.

## Dữ liệu cũ
- Các dòng DISBURSED cũ không có accepted_at: giữ nguyên, vì chưa biết thời điểm nghiệm thu thật trong database.
- Các dòng PAID cũ đã nghiệm thu nhưng thiếu accepted_at: không tự động giải ngân để tránh lấy sai mốc thời gian; cần đối soát rồi mới nhập dữ liệu từ chứng cứ.

## Kiểm thử cần chạy
1. Thanh toán 1 lần, chưa nghiệm thu: tiền PAID nhưng không ghi có Freelancer.
2. Nghiệm thu toàn bộ: có accepted_at và scheduled_disbursement_at, sau hạn mới DISBURSED.
3. Hợp đồng 2 mốc: nghiệm thu mốc 1 chỉ giải ngân đúng mốc 1; mốc 2 vẫn đang giữ.
4. Dự án tranh chấp: không tự động giải ngân.
5. Worker chạy lại 2 lần: chỉ 1 WalletCredit cho mỗi Payment.
6. Rút tiền: không vượt số dư net sau phí.

**Chưa chạy tích hợp Maven + MySQL trên máy người dùng; cần thực hiện các test trên để xác nhận trước demo.**
