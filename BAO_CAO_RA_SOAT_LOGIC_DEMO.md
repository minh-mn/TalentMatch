# Báo cáo rà soát luồng demo TalentMatch (03/11/2026)

## Điểm đã chỉnh trên mã nguồn
- Mốc dự án chỉ được thêm trước khi thanh toán, không sửa sau khi có giao dịch chờ xử lý.
- Chặn chuyển `SUBMITTED` trực tiếp qua endpoint cập nhật trạng thái; phải có bàn giao file hoặc URL HTTPS.
- Client chỉ nghiệm thu mốc khi đã nộp bàn giao và đã giữ tiền mốc; nghiệm thu chuyển chính giao dịch `PAID` của mốc thành `DISBURSED` (một lần).
- Không có mốc: Client chỉ nghiệm thu khi trạng thái `WAITING_ACCEPTANCE` và có bản bàn giao, sau đó giải ngân.
- Khóa dự án khi bắt đầu thanh toán demo/VNPAY; ngăn tạo giao dịch mới nếu có `PENDING`, `PAID`, hoặc `DISBURSED` đối với cùng mốc.
- Callback VNPAY từ chối nếu hợp đồng hiện hành chưa được ký hợp lệ.
- Tiến độ phải trong khoảng 0–100% và không được giảm.

## Vai trò và luồng vận hành
- **Client:** Chọn báo giá đã đồng thuận → ký hợp đồng → thanh toán từng mốc (tiền được giữ) → kiểm tra tiến độ/bàn giao → yêu cầu sửa hoặc nghiệm thu.
- **Freelancer:** Ký hợp đồng → bắt đầu mốc đã thanh toán → báo cáo tiến độ → nộp sản phẩm → nhận ghi có sau nghiệm thu → yêu cầu rút.
- **Admin:** Theo dõi thanh toán/giải ngân → xử lý tranh chấp → phê duyệt hoặc từ chối yêu cầu rút → đánh dấu hoàn tất rút **mô phỏng**. Admin không ký thay hai bên.

## Các giới hạn chưa được coi là hoàn thiện
1. Đây là mô phỏng, không có chuyển tiền ngân hàng thực tế. VNPAY Sandbox là giả lập thu tiền, **không hỗ trợ chuyển tiền ra tài khoản ngân hàng**.
2. Luồng ví của Client chưa có hệ thống escrow sổ cái kép, giữ tiền trước đặt hàng và OTP xác nhận thanh toán; hiện luồng demo ghi nhận `PAID` sau thao tác thanh toán. Không được giới thiệu như hệ thống tiền thật.
3. Chưa có kiểm thử tích hợp MySQL/Maven tại môi trường này; hãy thực hiện test trước khi trình diễn.
4. Dữ liệu seed trạng thái trong database có thể không đủ file vật lý bàn giao tương ứng; nên chạy thao tác tạo bản bàn giao mới trước khi nghiệm thu đối với bản ghi seed thiếu file.
5. Việc quản trị hoàn tiền/giải quyết tranh chấp chưa tự bù trừ một sổ cái escrow chuẩn; cần triển khai riêng nếu mục tiêu là thanh toán production.

## Test bắt buộc
- Mỗi trạng thái dùng hai dự án A/B, không tái sử dụng cùng dự án đã hoàn tất.
- Không có chữ ký hai bên: không cho thanh toán.
- Trùng thanh toán hoặc callback lặp: không cộng tiền hai lần.
- Freelancer gửi bàn giao khi chưa có thanh toán: bị chặn.
- Client nghiệm thu khi không có bản bàn giao: bị chặn.
- Client nghiệm thu hợp lệ: chỉ một giao dịch được giải ngân và một bản ghi ghi có ví được sinh.
- Freelancer rút quá số dư: bị chặn. Yêu cầu rút đang chờ: tiền bị khóa; từ chối: hoàn lại tiền khả dụng; duyệt+hoàn tất: tiền trừ đúng một lần.
- Tranh chấp: khóa các thao tác trả tiền/giải ngân tiếp theo.
