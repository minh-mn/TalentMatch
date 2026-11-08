package com.talentmatch.config;

import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Nhãn dễ đọc cho dữ liệu kiểm thử, không thay đổi dữ liệu gốc hoặc ID. */
@Component("projectDisplay")
public class ProjectDisplay {
    private static final Pattern SEED = Pattern.compile("(?i)^TMTEST-(\\d+)\\s*\\|\\s*(.*)$");
    private static final Pattern COPY = Pattern.compile("(?i)\\s*\\[([AB])\\]\\s*$");
    private static final Map<String, String> TITLES = Map.ofEntries(
        Map.entry("01", "Thiết kế giao diện trang giới thiệu sản phẩm"),
        Map.entry("02", "Xây dựng website quản lý đơn hàng"),
        Map.entry("03", "Thiết kế bộ nhận diện thương hiệu"),
        Map.entry("04", "Phát triển trang quản lý khách hàng"),
        Map.entry("05", "Thiết kế giao diện ứng dụng đặt lịch"),
        Map.entry("06", "Xây dựng hệ thống quản lý dự án"),
        Map.entry("07", "Thiết kế giao diện website thương mại điện tử"),
        Map.entry("08", "Phát triển hệ thống quản lý kho"),
        Map.entry("09", "Thiết kế và triển khai website doanh nghiệp"),
        Map.entry("10", "Phát triển chức năng thanh toán trực tuyến"),
        Map.entry("11", "Lập trình ứng dụng quản lý công việc")
    );
    public String title(String original) {
        if (original == null) return "Dự án chưa có tên";
        String clean = original.trim();
        Matcher variant = COPY.matcher(clean);
        String suffix = "";
        if (variant.find()) {
            suffix = " (Bản kiểm thử " + variant.group(1).toUpperCase(Locale.ROOT) + ")";
            clean = clean.substring(0, variant.start()).trim();
        }
        Matcher seed = SEED.matcher(clean);
        if (seed.matches()) clean = TITLES.getOrDefault(seed.group(1), seed.group(2));
        return clean.replace('_', ' ').replaceAll("\\s+", " ").trim() + suffix;
    }
    public String milestone(String original) {
        if (original == null) return "Mốc công việc";
        return original.replaceAll("(?i)WAITING_ACCEPTANCE", "Chờ nghiệm thu")
            .replaceAll("(?i)IN_PROGRESS", "Đang thực hiện")
            .replaceAll("(?i)COMPLETED", "Đã hoàn thành")
            .replace('_', ' ').trim();
    }
}
