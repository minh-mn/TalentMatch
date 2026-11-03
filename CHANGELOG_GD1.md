# Nhật ký chỉnh sửa – Giai đoạn 1

| Thành phần | Thay đổi |
|---|---|
| Cấu trúc Maven | Giữ một thư mục `src/main` và `src/test`; loại source lồng, `target` và `.idea` |
| `ProjectDelivery` + SQL | Đồng bộ các cột Java sử dụng, sửa 3 seed bàn giao |
| `JobInvitation` / `TalentWorkflowController` | Lưu thời điểm phản hồi; sửa seed `PENDING` → `SENT` |
| `Payment` / `Milestone` seed | Chuẩn hóa tiền đã giải ngân và milestone hoàn thành |
| `Project` | Ánh xạ metadata hợp đồng V33 (mã hợp đồng, phí, điều khoản, SOW, ...) |
| `Dispute` + `DisputeRepository` | Thêm Entity/Repository; lưu tranh chấp khi đề nghị hủy dự án có tiền demo |
| `AdminDisputeController` | Khi quyết định tranh chấp, cập nhật hồ sơ `disputes` tương ứng |
| `DataInitializer` | Chỉ seed khi bật `APP_DEMO_SEED=true` **và chưa có công việc**; mặc định không tự seed |
| SQL V33 FRESH | Đủ 30 bảng tương thích 25 Entity; bổ sung 8 bảng còn thiếu |
| SQL MIGRATION SAFE | Bổ sung dữ liệu/cột/bảng thiếu mà không xóa bảng đã tồn tại |
| Dữ liệu mẫu | Job banner #17; ứng tuyển #9; lời mời job còn mở; số hồ sơ theo DB; deadline demo |
| Kiểm tra | Thêm `scripts/verify_stage1.py` và 4 unit test Entity |

**Chưa thay thế được kiểm thử build Maven và tích hợp MySQL.** Những thay đổi nghiệp vụ giai đoạn 2–5 không nằm trong bản này.
