-- Dùng khi không sử dụng spring.jpa.hibernate.ddl-auto=update.
-- MySQL 8+: chạy MỘT LẦN sau khi đã sao lưu.
ALTER TABLE projects ADD COLUMN payment_due_date DATE NULL;
ALTER TABLE milestones ADD COLUMN payment_due_date DATE NULL;
-- Không gán hạn thanh toán giả cho các hợp đồng cũ.
