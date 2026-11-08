package com.talentmatch.config;

import org.springframework.stereotype.Component;
import java.util.Map;

/** Vietnamese presentation labels only; persisted enum codes remain unchanged. */
@Component("vnStatus")
public class VietnameseStatus {
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("PENDING", "Chờ xử lý"), Map.entry("SUBMITTED", "Đã gửi"),
        Map.entry("SHORTLISTED", "Được chọn vào danh sách ngắn"),
        Map.entry("ACCEPTED", "Đã chấp nhận"), Map.entry("REJECTED", "Đã từ chối"),
        Map.entry("WITHDRAWN", "Đã rút"), Map.entry("SUPERSEDED", "Đã được thay thế"),
        Map.entry("EXPIRED", "Đã hết hạn"), Map.entry("DRAFT", "Bản nháp"),
        Map.entry("OPEN", "Đang mở"), Map.entry("PAUSED", "Tạm dừng"),
        Map.entry("CLOSED", "Đã đóng"), Map.entry("CANCELLED", "Đã hủy"),
        Map.entry("WAITING_PAYMENT", "Chờ thanh toán"),
        Map.entry("IN_PROGRESS", "Đang thực hiện"),
        Map.entry("WAITING_ACCEPTANCE", "Chờ nghiệm thu"),
        Map.entry("COMPLETED", "Đã hoàn thành"), Map.entry("DISPUTED", "Đang tranh chấp"),
        Map.entry("UNPAID", "Chưa thanh toán"), Map.entry("PAID", "Đã thanh toán"),
        Map.entry("DISBURSED", "Đã giải ngân"), Map.entry("FAILED", "Thất bại"),
        Map.entry("REFUNDED", "Đã hoàn tiền"), Map.entry("APPROVED", "Đã phê duyệt"),
        Map.entry("SENT", "Đã gửi"), Map.entry("DECLINED", "Đã từ chối"),
        Map.entry("UNDER_REVIEW", "Đang xem xét"),
        Map.entry("RESOLVED", "Đã giải quyết"), Map.entry("REVIEWED", "Đã xem xét"),
        Map.entry("ACTIVE", "Đang hoạt động"), Map.entry("INACTIVE", "Ngừng hoạt động"),
        Map.entry("VERIFIED", "Đã xác minh"), Map.entry("NOT_SUBMITTED", "Chưa gửi xác minh"),
        Map.entry("PROCESSING", "Đang xử lý"), Map.entry("SUCCESS", "Thành công"),
        Map.entry("FREELANCER", "Freelancer"), Map.entry("CUSTOMER", "Khách hàng"),
        Map.entry("ADMIN", "Quản trị viên")
    );
    public String label(Object status) {
        if (status == null) return "—";
        String raw = status instanceof Enum<?> e ? e.name() : status.toString();
        return LABELS.getOrDefault(raw, raw);
    }
}
