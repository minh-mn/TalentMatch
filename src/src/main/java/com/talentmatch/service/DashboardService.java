package com.talentmatch.service;

import com.talentmatch.dto.ChartPoint;
import com.talentmatch.dto.PieSlice;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private final UserRepository users;
    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final ProjectRepository projects;
    private final PaymentRepository payments;

    public DashboardService(UserRepository users,
                            JobRepository jobs,
                            JobApplicationRepository applications,
                            ProjectRepository projects,
                            PaymentRepository payments) {
        this.users = users;
        this.jobs = jobs;
        this.applications = applications;
        this.projects = projects;
        this.payments = payments;
    }

    public Map<String, Object> freelancerStats(User u) {
        var ps = projects.findByFreelancerOrderByCreatedAtDesc(u);
        var pays = successfulPaymentsFor(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applications", applications.findByFreelancerOrderByCreatedAtDesc(u).size());
        m.put("activeProjects", ps.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS || p.getStatus() == ProjectStatus.WAITING_ACCEPTANCE).count());
        m.put("completedProjects", ps.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count());
        m.put("revenue", pays.stream()
                .filter(p -> p.getStatus() == PaymentStatus.DISBURSED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return m;
    }

    public Map<String, Object> customerStats(User u) {
        var ps = projects.findByCustomerOrderByCreatedAtDesc(u);
        var pays = payments.findByPayerOrderByCreatedAtDesc(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobs", jobs.findByCustomerOrderByCreatedAtDesc(u).size());
        m.put("activeProjects", ps.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS || p.getStatus() == ProjectStatus.WAITING_ACCEPTANCE).count());
        m.put("completedProjects", ps.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count());
        m.put("cost", pays.stream().filter(this::successful).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return m;
    }

    public Map<String, Object> adminStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", users.count());
        m.put("jobs", jobs.count());
        m.put("projects", projects.count());
        BigDecimal total = payments.findAll().stream()
                .filter(this::successful)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("transactionValue", total);
        return m;
    }

    /**
     * Chuẩn hóa khoảng ngày cho dashboard/profile:
     * - mặc định 14 ngày gần nhất;
     * - tự đảo nếu người dùng nhập From > To;
     * - giới hạn tối đa 90 ngày để biểu đồ không quá nặng.
     */
    public ChartRange normalizeRange(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(13) : from;

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        if (ChronoUnit.DAYS.between(start, end) > 89) {
            start = end.minusDays(89);
        }
        return new ChartRange(start, end);
    }

    public List<ChartPoint> bar(User u, LocalDate from, LocalDate to, String metric) {
        ChartRange range = normalizeRange(from, to);
        LocalDate start = range.from();
        LocalDate end = range.to();
        String safeMetric = "revenue".equalsIgnoreCase(metric) ? "revenue" : "projects";

        Map<LocalDate, Double> values = new HashMap<>();
        if ("revenue".equals(safeMetric)) {
            for (Payment p : paymentsFor(u)) {
                LocalDateTime eventTime = revenueTimeFor(u, p);
                if (eventTime == null || !isRevenueCountedFor(u, p)) continue;
                LocalDate day = eventTime.toLocalDate();
                if (!day.isBefore(start) && !day.isAfter(end)) {
                    double value = p.getAmount() == null ? 0d : p.getAmount().doubleValue();
                    if (u != null && u.getRole() == Role.ADMIN) value *= 0.07d;
                    values.merge(day, value, Double::sum);
                }
            }
        } else {
            for (Project p : projectsFor(u)) {
                if (p.getCreatedAt() == null) continue;
                LocalDate day = p.getCreatedAt().toLocalDate();
                if (!day.isBefore(start) && !day.isAfter(end)) {
                    values.merge(day, 1d, Double::sum);
                }
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        List<ChartPoint> result = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            result.add(new ChartPoint(day.format(formatter), values.getOrDefault(day, 0d)));
        }
        return result;
    }

    /**
     * Biểu đồ tròn sử dụng cùng khoảng thời gian với biểu đồ cột để khi người dùng
     * đổi ngày, cả hai biểu đồ thay đổi đồng bộ.
     */
    public List<PieSlice> pie(User u, LocalDate from, LocalDate to, String mode) {
        ChartRange range = normalizeRange(from, to);
        List<Project> filtered = projectsFor(u).stream()
                .filter(p -> p.getCreatedAt() != null)
                .filter(p -> {
                    LocalDate d = p.getCreatedAt().toLocalDate();
                    return !d.isBefore(range.from()) && !d.isAfter(range.to());
                })
                .toList();

        Map<String, Long> counts;
        if ("category".equalsIgnoreCase(mode)) {
            counts = filtered.stream().collect(Collectors.groupingBy(
                    p -> {
                        if (p.getApplication() == null || p.getApplication().getJob() == null) return "Chưa phân loại";
                        String category = p.getApplication().getJob().getCategory();
                        return category == null || category.isBlank() ? "Chưa phân loại" : category;
                    },
                    LinkedHashMap::new,
                    Collectors.counting()
            ));
        } else {
            counts = filtered.stream().collect(Collectors.groupingBy(
                    p -> statusLabel(p.getStatus()),
                    LinkedHashMap::new,
                    Collectors.counting()
            ));
        }

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return List.of();

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new PieSlice(e.getKey(), e.getValue(), e.getValue() * 100d / total))
                .toList();
    }

    // Giữ overload cũ cho các chỗ khác trong project nếu còn gọi trực tiếp.
    public List<PieSlice> pie(User u, String mode) {
        ChartRange range = normalizeRange(null, null);
        return pie(u, range.from(), range.to(), mode);
    }

    public double total(List<ChartPoint> points) {
        if (points == null) return 0d;
        return points.stream().mapToDouble(ChartPoint::value).sum();
    }

    private List<Project> projectsFor(User u) {
        if (u == null || u.getRole() == Role.ADMIN) return projects.findAll();
        if (u.getRole() == Role.FREELANCER) return projects.findByFreelancerOrderByCreatedAtDesc(u);
        if (u.getRole() == Role.CUSTOMER) return projects.findByCustomerOrderByCreatedAtDesc(u);
        return projects.findAll();
    }

    private List<Payment> paymentsFor(User u) {
        if (u == null || u.getRole() == Role.ADMIN) return payments.findAll();
        if (u.getRole() == Role.CUSTOMER) return payments.findByPayerOrderByCreatedAtDesc(u);
        if (u.getRole() == Role.FREELANCER) return successfulPaymentsFor(u);
        return payments.findAll();
    }

    private List<Payment> successfulPaymentsFor(User u) {
        return payments.findAll().stream()
                .filter(p -> p.getProject() != null && p.getProject().getFreelancer() != null)
                .filter(p -> Objects.equals(p.getProject().getFreelancer().getId(), u.getId()))
                .toList();
    }

    /** Freelancer chỉ thực sự ghi nhận doanh thu khi tiền đã giải ngân. */
    private boolean isRevenueCountedFor(User u, Payment p) {
        if (p == null) return false;
        if (u != null && u.getRole() == Role.FREELANCER) {
            return p.getStatus() == PaymentStatus.DISBURSED;
        }
        return successful(p);
    }

    private LocalDateTime revenueTimeFor(User u, Payment p) {
        if (p == null) return null;
        if (u != null && u.getRole() == Role.FREELANCER) {
            return p.getDisbursedAt() != null ? p.getDisbursedAt() : p.getPaidAt();
        }
        return p.getPaidAt() != null ? p.getPaidAt() : p.getDisbursedAt();
    }

    private boolean successful(Payment p) {
        return p != null && (p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.DISBURSED);
    }

    private String statusLabel(ProjectStatus status) {
        if (status == null) return "Chưa xác định";
        return switch (status) {
            case WAITING_PAYMENT -> "Chờ thanh toán";
            case IN_PROGRESS -> "Đang thực hiện";
            case WAITING_ACCEPTANCE -> "Chờ nghiệm thu";
            case COMPLETED -> "Đã hoàn thành";
            case CANCELLED -> "Đã hủy";
            case DISPUTED -> "Đang tranh chấp";
        };
    }

    public record ChartRange(LocalDate from, LocalDate to) {
        public long days() {
            return ChronoUnit.DAYS.between(from, to) + 1;
        }
    }
}
