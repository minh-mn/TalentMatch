package com.talentmatch.controller;

import com.talentmatch.dto.ChartPoint;
import com.talentmatch.dto.PieSlice;
import com.talentmatch.model.*;
import com.talentmatch.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.*;

@Controller
@RequestMapping("/freelancer")
public class FreelancerController {
    private final UserService users;
    private final JobService jobs;
    private final ProjectService projects;
    private final PaymentService payments;
    private final DashboardService dashboard;

    public FreelancerController(UserService users,
                                JobService jobs,
                                ProjectService projects,
                                PaymentService payments,
                                DashboardService dashboard) {
        this.users = users;
        this.jobs = jobs;
        this.projects = projects;
        this.payments = payments;
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth,
                            Model model,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "projects") String metric,
                            @RequestParam(defaultValue = "status") String pieMode) {
        User u = users.byEmail(auth.getName());
        model.addAttribute("stats", dashboard.freelancerStats(u));
        model.addAttribute("applications", jobs.applications(u).stream().limit(5).toList());
        model.addAttribute("projects", projects.forFreelancer(u).stream().limit(5).toList());
        // Các mục trên dashboard lấy từ database, không dùng số liệu tĩnh.
        List<Job> recommendedJobs = jobs.search(null, null, null, null, null, null, null).stream().limit(5).toList();
        model.addAttribute("recommendedJobs", recommendedJobs);
        List<Map<String, Object>> checklist = new ArrayList<>();
        checklist.add(Map.of("label", "Thông tin cơ bản", "done", hasText(u.getFullName()) && hasText(u.getPhone())));
        checklist.add(Map.of("label", "Kỹ năng", "done", hasText(u.getSkills())));
        checklist.add(Map.of("label", "Giới thiệu bản thân", "done", hasText(u.getBio())));
        checklist.add(Map.of("label", "Kinh nghiệm", "done", u.getExperienceYears() != null && u.getExperienceYears() > 0));
        checklist.add(Map.of("label", "Portfolio / Website", "done", hasText(u.getWebsite())));
        checklist.add(Map.of("label", "Ảnh đại diện", "done", hasText(u.getAvatar())));
        model.addAttribute("profileChecklist", checklist);
        model.addAttribute("profileCompletion", (int) (100L * checklist.stream().filter(c -> Boolean.TRUE.equals(c.get("done"))).count() / checklist.size()));
        addCharts(model, u, from, to, metric, pieMode);
        return "freelancer/dashboard";
    }

    @GetMapping("/reports")
    public String reports(Authentication auth,
                          Model model,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "all") String category,
                          @RequestParam(defaultValue = "ALL") String status) {
        User user = users.byEmail(auth.getName());
        List<Project> allProjects = projects.forFreelancer(user);
        LocalDate[] range = normalizeReportDates(from, to);
        LocalDate start = range[0];
        LocalDate end = range[1];
        List<String> categories = reportCategories(allProjects);
        String safeCategory = categories.contains(category) ? category : "all";
        String safeStatus = reportSafeStatus(status);

        // Các dự án được tính theo ngày tạo, trạng thái hiện tại và bộ lọc được chọn.
        List<Project> filtered = filterReportProjects(allProjects, start, end, safeCategory, safeStatus);
        long completed = filtered.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count();
        Set<Long> eligibleProjectIds = allProjects.stream()
            .filter(p -> reportMatches(p, safeCategory, safeStatus))
            .map(Project::getId).collect(java.util.stream.Collectors.toSet());

        // Doanh thu theo ngày GIẢI NGÂN, không tính tiền mới được thanh toán/chờ giải ngân.
        List<Payment> disbursed = payments.history(user).stream()
            .filter(p -> p.getStatus() == PaymentStatus.DISBURSED && p.getProject() != null)
            .filter(p -> eligibleProjectIds.contains(p.getProject().getId()))
            .filter(p -> {
                LocalDate d = reportDisbursementDate(p);
                return d != null && !d.isBefore(start) && !d.isAfter(end);
            }).toList();
        BigDecimal revenue = disbursed.stream()
            .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Các tháng dùng chung cho biểu đồ doanh thu và số dự án.
        Map<YearMonth, BigDecimal> revenueByMonth = new HashMap<>();
        for (Payment payment : disbursed) {
            YearMonth month = YearMonth.from(reportDisbursementDate(payment));
            revenueByMonth.merge(month, payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount(), BigDecimal::add);
        }
        Map<YearMonth, Long> projectsByMonth = new HashMap<>();
        for (Project project : filtered) {
            projectsByMonth.merge(YearMonth.from(project.getCreatedAt()), 1L, Long::sum);
        }
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) months.add(m);
        BigDecimal maxRevenue = months.stream().map(m -> revenueByMonth.getOrDefault(m, BigDecimal.ZERO))
            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        long maxProjects = months.stream().mapToLong(m -> projectsByMonth.getOrDefault(m,0L)).max().orElse(0L);
        List<ReportMonthRow> monthlyRows = new ArrayList<>();
        List<ReportLineDot> dots = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < months.size(); i++) {
            YearMonth m = months.get(i);
            BigDecimal amount = revenueByMonth.getOrDefault(m, BigDecimal.ZERO);
            long count = projectsByMonth.getOrDefault(m, 0L);
            double revPercent = maxRevenue.signum() == 0 ? 0 : amount.doubleValue() / maxRevenue.doubleValue() * 100d;
            double prjPercent = maxProjects == 0 ? 0 : count * 100d / maxProjects;
            monthlyRows.add(new ReportMonthRow(m.format(DateTimeFormatter.ofPattern("MM/yy")), amount, count, revPercent));
            double x = months.size() == 1 ? 300d : 12d + i * 576d / (months.size()-1);
            double y = 160d - prjPercent * 1.3d;
            dots.add(new ReportLineDot(x,y));
            if (line.length() != 0) line.append(' ');
            line.append(String.format(Locale.US,"%.2f,%.2f",x,y));
        }
        String area = line.length() == 0 ? "" : "12,160 " + line + " 588,160";

        // Phân bổ danh mục theo dự án; không bịa phần trăm khi không có dữ liệu.
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (Project project : filtered) {
            String name = reportProjectCategory(project);
            categoryCounts.merge(name, 1L, Long::sum);
        }
        String[] palette = {"#0867f5", "#3d9bfc", "#0fbda8", "#8b5cf6", "#f6b541", "#f87171", "#7e92b6"};
        List<ReportCategoryRow> categoryRows = new ArrayList<>();
        StringBuilder gradient = new StringBuilder("conic-gradient(");
        double cursor = 0d;
        List<Map.Entry<String,Long>> categoryEntries = categoryCounts.entrySet().stream()
            .sorted(Map.Entry.<String,Long>comparingByValue().reversed()).toList();
        for (int i = 0; i < categoryEntries.size(); i++) {
            var entry = categoryEntries.get(i);
            String color = palette[i % palette.length];
            double pct = filtered.isEmpty() ? 0 : 100d * entry.getValue() / filtered.size();
            categoryRows.add(new ReportCategoryRow(entry.getKey(), entry.getValue(), pct, color));
            if (i > 0) gradient.append(',');
            gradient.append(String.format(Locale.US,"%s %.4f%% %.4f%%",color,cursor,cursor+pct));
            cursor += pct;
        }
        gradient.append(')');

        Map<String,List<Project>> byClient = new LinkedHashMap<>();
        for (Project project : filtered) {
            byClient.computeIfAbsent(reportClientName(project), unused -> new ArrayList<>()).add(project);
        }
        List<ReportClientRow> topClients = byClient.entrySet().stream().map(entry ->
            new ReportClientRow(entry.getKey(), entry.getValue().size(),
                entry.getValue().stream().map(p -> p.getTotalPrice() == null ? BigDecimal.ZERO : p.getTotalPrice())
                    .reduce(BigDecimal.ZERO,BigDecimal::add)))
            .sorted(Comparator.comparingLong(ReportClientRow::count).reversed()
                .thenComparing(ReportClientRow::totalValue, Comparator.reverseOrder()))
            .limit(5).toList();
        List<ReportProjectRow> recentProjects = filtered.stream()
            .sorted(Comparator.comparing(Project::getCreatedAt).reversed())
            .limit(5).map(p -> new ReportProjectRow(
                reportProjectTitle(p), reportClientName(p),
                p.getTotalPrice() == null ? BigDecimal.ZERO : p.getTotalPrice(),
                p.getStatus() == null ? "UNKNOWN" : p.getStatus().name(),
                reportStatusName(p.getStatus()),
                p.getUpdatedAt() == null ? null : p.getUpdatedAt().toLocalDate()
            )).toList();

        model.addAttribute("reportFrom", start);
        model.addAttribute("reportTo", end);
        model.addAttribute("reportCategories", categories);
        model.addAttribute("selectedCategory", safeCategory);
        model.addAttribute("selectedStatus", safeStatus);
        model.addAttribute("reportTotalProjects", filtered.size());
        model.addAttribute("reportCompletedProjects", completed);
        model.addAttribute("reportClientCount", byClient.size());
        model.addAttribute("reportRating", user.getRating() == null ? 0d : user.getRating());
        model.addAttribute("reportRevenue", revenue);
        model.addAttribute("reportMonths", monthlyRows);
        model.addAttribute("reportHasRevenue", revenue.signum() > 0);
        model.addAttribute("reportHasProjects", !filtered.isEmpty());
        model.addAttribute("reportCategorySlices", categoryRows);
        model.addAttribute("reportHasCategoryData", !categoryRows.isEmpty());
        model.addAttribute("reportCategoryGradient", categoryRows.isEmpty() ? "#e5edf9" : gradient.toString());
        model.addAttribute("reportLinePoints", line.toString());
        model.addAttribute("reportLineArea", area);
        model.addAttribute("reportLineDots", dots);
        model.addAttribute("reportTopClients", topClients);
        model.addAttribute("reportRecentProjects", recentProjects);
        return "freelancer/reports";
    }

    @GetMapping(value = "/reports/export", produces = "text/csv;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<byte[]> exportReports(Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "ALL") String status) {
        User user = users.byEmail(auth.getName());
        List<Project> all = projects.forFreelancer(user);
        LocalDate[] range = normalizeReportDates(from, to);
        String safeCat = reportCategories(all).contains(category) ? category : "all";
        List<Project> selected = filterReportProjects(all, range[0], range[1], safeCat, reportSafeStatus(status));
        StringBuilder csv = new StringBuilder("\uFEFFMã dự án,Tên dự án,Khách hàng,Danh mục,Giá trị hợp đồng (VND),Trạng thái,Ngày tạo\r\n");
        for (Project project : selected) {
            csv.append(project.getId()).append(',')
                .append(reportCsv(reportProjectTitle(project))).append(',')
                .append(reportCsv(reportClientName(project))).append(',')
                .append(reportCsv(reportProjectCategory(project))).append(',')
                .append(project.getTotalPrice() == null ? "0" : project.getTotalPrice().toPlainString()).append(',')
                .append(reportCsv(reportStatusName(project.getStatus()))).append(',')
                .append(project.getCreatedAt() == null ? "" : project.getCreatedAt().toLocalDate())
                .append("\r\n");
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=talentmatch-bao-cao-du-an.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private LocalDate[] normalizeReportDates(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusMonths(5).withDayOfMonth(1) : from;
        if (start.isAfter(end)) { LocalDate temp = start; start = end; end = temp; }
        if (ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(end)) > 11)
            start = end.minusMonths(11).withDayOfMonth(1);
        return new LocalDate[] {start,end};
    }

    private String reportSafeStatus(String input) {
        if (input == null || "ALL".equalsIgnoreCase(input)) return "ALL";
        try { return ProjectStatus.valueOf(input.toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException ex) { return "ALL"; }
    }
    private List<String> reportCategories(List<Project> projects) {
        return projects.stream().map(this::reportProjectCategory).distinct().sorted().toList();
    }
    private List<Project> filterReportProjects(List<Project> projects, LocalDate from, LocalDate to,
                                                String category, String status) {
        return projects.stream()
            .filter(p -> p.getCreatedAt() != null)
            .filter(p -> { LocalDate d=p.getCreatedAt().toLocalDate(); return !d.isBefore(from) && !d.isAfter(to); })
            .filter(p -> reportMatches(p,category,status)).toList();
    }
    private boolean reportMatches(Project p, String category, String status) {
        return ("all".equals(category) || reportProjectCategory(p).equals(category))
            && ("ALL".equals(status) || (p.getStatus()!=null && p.getStatus().name().equals(status)));
    }
    private String reportProjectCategory(Project p) {
        if (p.getApplication() == null || p.getApplication().getJob() == null) return "Chưa phân loại";
        String value = p.getApplication().getJob().getCategory();
        return value == null || value.isBlank() ? "Chưa phân loại" : value;
    }
    private String reportProjectTitle(Project p) {
        return p.getApplication() == null || p.getApplication().getJob() == null ? "Dự án" : p.getApplication().getJob().getTitle();
    }
    private String reportClientName(Project p) {
        User client = p.getCustomer();
        if (client == null) return "Khách hàng";
        String name = client.getCompanyName();
        if (name == null || name.isBlank()) name = client.getFullName();
        return name == null || name.isBlank() ? "Khách hàng" : name;
    }
    private LocalDate reportDisbursementDate(Payment payment) {
        LocalDateTime time = payment.getDisbursedAt() == null ? payment.getPaidAt() : payment.getDisbursedAt();
        return time == null ? null : time.toLocalDate();
    }
    private String reportStatusName(ProjectStatus status) {
        if (status == null) return "Chưa xác định";
        return switch (status) {
            case WAITING_PAYMENT -> "Chờ thanh toán";
            case IN_PROGRESS -> "Đang thực hiện";
            case WAITING_ACCEPTANCE -> "Chờ nghiệm thu";
            case COMPLETED -> "Đã hoàn thành";
            case CANCELLED -> "Đã hủy";
            case DISPUTED -> "Tranh chấp";
        };
    }
    private String reportCsv(String raw) {
        String value = raw == null ? "" : raw.replace("\r"," ").replace("\n"," ");
        // Không cho phép ô nội dung biến thành công thức khi mở trong Excel.
        if (!value.isBlank() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"","\"\"") + "\"";
    }
    public record ReportMonthRow(String label, BigDecimal revenue, long projects, double revenuePercent) {}
    public record ReportLineDot(double x, double y) {}
    public record ReportCategoryRow(String label,long count,double percent,String color) {}
    public record ReportClientRow(String name,long count,BigDecimal totalValue) {}
    public record ReportProjectRow(String title,String clientName,BigDecimal value,String status,String statusText,LocalDate updatedDate) {}

    @GetMapping("/profile")
    public String profile(Authentication auth,
                          Model model,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "projects") String metric,
                          @RequestParam(defaultValue = "status") String pieMode) {
        User u = users.byEmail(auth.getName());
        List<Project> profileProjects = projects.forFreelancer(u);

        List<ProfileCheck> checks = List.of(
                new ProfileCheck("Họ & Tên", hasText(u.getFullName())),
                new ProfileCheck("Thông tin liên hệ", hasText(u.getEmail()) && hasText(u.getPhone())),
                new ProfileCheck("Ảnh đại diện", hasText(u.getAvatar())),
                new ProfileCheck("Giới thiệu bản thân", hasText(u.getBio())),
                new ProfileCheck("Địa chỉ", hasText(u.getLocation())),
                new ProfileCheck("Kỹ năng chính", hasText(u.getSkills())),
                new ProfileCheck("Kinh nghiệm làm việc", u.getExperienceYears() != null && u.getExperienceYears() > 0),
                new ProfileCheck("Vị trí mong muốn", hasText(u.getProfessionalTitle())),
                new ProfileCheck("Đơn giá theo giờ", u.getHourlyRate() != null && u.getHourlyRate().signum() > 0),
                new ProfileCheck("Portfolio / Website", hasText(u.getWebsite()))
        );

        long done = checks.stream().filter(ProfileCheck::done).count();
        int completion = (int) Math.round(done * 100.0 / checks.size());
        List<String> skillList = hasText(u.getSkills())
                ? java.util.Arrays.stream(u.getSkills().split(","))
                    .map(String::trim)
                    .filter(x -> !x.isBlank())
                    .toList()
                : List.of();

        model.addAttribute("profile", u);
        model.addAttribute("profileProjects", profileProjects);
        model.addAttribute("completedProjectCount", profileProjects.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count());
        model.addAttribute("profileChecks", checks);
        model.addAttribute("profileMissing", checks.stream().filter(c -> !c.done()).toList());
        model.addAttribute("profileDone", checks.stream().filter(ProfileCheck::done).toList());
        model.addAttribute("profileDoneCount", done);
        model.addAttribute("profileCompletion", completion);
        model.addAttribute("skillList", skillList);
        model.addAttribute("today", LocalDate.now());
        addCharts(model, u, from, to, metric, pieMode);
        return "freelancer/profile";
    }

    /**
     * API nhỏ cho riêng khối thống kê ở trang hồ sơ.
     * Front-end gọi endpoint này bằng fetch nên đổi ngày/chỉ số không reload cả trang.
     */
    @GetMapping(value = "/profile/statistics", produces = "application/json")
    @ResponseBody
    public Map<String, Object> profileStatistics(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "projects") String metric,
            @RequestParam(defaultValue = "status") String pieMode) {

        User u = users.byEmail(auth.getName());
        String safeMetric = normalizeMetric(metric);
        String safePieMode = normalizePieMode(pieMode);
        DashboardService.ChartRange range = dashboard.normalizeRange(from, to);

        List<ChartPoint> bars = dashboard.bar(u, range.from(), range.to(), safeMetric);
        List<PieSlice> pie = dashboard.pie(u, range.from(), range.to(), safePieMode);
        double max = bars.stream().mapToDouble(ChartPoint::value).max().orElse(0d);
        double total = dashboard.total(bars);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", range.from().toString());
        response.put("to", range.to().toString());
        response.put("days", range.days());
        response.put("metric", safeMetric);
        response.put("pieMode", safePieMode);
        response.put("barData", bars);
        response.put("barMax", max <= 0 ? 1d : max);
        response.put("barTotal", total);
        long pieTotal = pie.stream().mapToLong(PieSlice::count).sum();
        response.put("pieData", pie);
        response.put("pieTotal", pieTotal);
        response.put("pieGradient", gradient(pie));
        response.put("hasBarData", total > 0d);
        response.put("hasPieData", !pie.isEmpty());
        return response;
    }

    @PostMapping("/profile")
    public String updateProfile(Authentication auth, @ModelAttribute User profile, RedirectAttributes ra) {
        try {
            users.updateProfile(auth.getName(), profile);
            ra.addFlashAttribute("success", "Cập nhật hồ sơ Freelancer thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/freelancer/profile";
    }

    @GetMapping("/saved-jobs")
    public String saved(Authentication auth, Model model,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) BigDecimal minBudget,
                        @RequestParam(required = false) BigDecimal maxBudget,
                        @RequestParam(required = false) List<String> workType,
                        @RequestParam(required = false) List<String> levelGroup,
                        @RequestParam(required = false) String skill,
                        @RequestParam(defaultValue = "latest") String sort,
                        @RequestParam(defaultValue = "1") int page) {
        List<SavedJob> allSaved = jobs.saved(users.byEmail(auth.getName()));
        List<SavedJob> filtered = new ArrayList<>(allSaved);
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String skillQuery = skill == null ? "" : skill.trim().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty()) filtered.removeIf(s -> {
            Job j = s.getJob();
            String searchable = String.join(" ", Objects.toString(j.getTitle(), ""),
                Objects.toString(j.getDescription(), ""), Objects.toString(j.getRequiredSkills(), ""),
                Objects.toString(j.getCategory(), "")).toLowerCase(Locale.ROOT);
            return !searchable.contains(keyword);
        });
        if (!skillQuery.isEmpty()) filtered.removeIf(s -> !Objects.toString(s.getJob().getRequiredSkills(), "")
                .toLowerCase(Locale.ROOT).contains(skillQuery));
        if (minBudget != null) filtered.removeIf(s -> s.getJob().getBudget() == null || s.getJob().getBudget().compareTo(minBudget) < 0);
        if (maxBudget != null) filtered.removeIf(s -> s.getJob().getBudget() == null || s.getJob().getBudget().compareTo(maxBudget) > 0);
        List<String> selectedWork = workType == null ? List.of() : workType.stream()
                .filter(v -> List.of("REMOTE", "HYBRID", "ONSITE").contains(v)).distinct().toList();
        if (!selectedWork.isEmpty()) filtered.removeIf(s -> !selectedWork.contains(s.getJob().getWorkType()));
        List<String> selectedGroups = levelGroup == null ? List.of() : levelGroup.stream()
                .filter(v -> List.of("BEGINNER", "MIDDLE", "SENIOR").contains(v)).distinct().toList();
        if (!selectedGroups.isEmpty()) filtered.removeIf(s -> {
            String level = Objects.toString(s.getJob().getExperienceLevel(), "");
            return !(selectedGroups.contains(level) || (selectedGroups.contains("BEGINNER")
                    && List.of("INTERN", "FRESHER", "JUNIOR").contains(level)));
        });
        if ("budget_desc".equals(sort)) filtered.sort(Comparator.comparing(
                (SavedJob sj) -> sj.getJob().getBudget(), Comparator.nullsLast(Comparator.reverseOrder())));
        else if ("budget_asc".equals(sort)) filtered.sort(Comparator.comparing(
                (SavedJob sj) -> sj.getJob().getBudget(), Comparator.nullsLast(Comparator.naturalOrder())));
        else filtered.sort(Comparator.comparing(SavedJob::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int totalPages = Math.max(1, (filtered.size() + 4) / 5);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int start = Math.min((currentPage - 1) * 5, filtered.size());
        model.addAttribute("savedJobs", filtered.subList(start, Math.min(start + 5, filtered.size())));
        model.addAttribute("totalJobs", filtered.size());
        model.addAttribute("allJobsCount", allSaved.size());
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("skill", skill == null ? "" : skill);
        model.addAttribute("minBudget", minBudget);
        model.addAttribute("maxBudget", maxBudget);
        model.addAttribute("workType", selectedWork);
        model.addAttribute("levelGroup", selectedGroups);
        model.addAttribute("sort", sort);
        return "freelancer/saved-jobs";
    }

    @PostMapping("/saved-jobs/{jobId}/remove")
    public String removeSaved(@PathVariable Long jobId, Authentication auth, RedirectAttributes ra) {
        try {
            User user = users.byEmail(auth.getName());
            boolean exists = jobs.saved(user).stream().anyMatch(s -> s.getJob().getId().equals(jobId));
            if (exists) {
                jobs.toggleSaved(jobs.get(jobId), user);
                ra.addFlashAttribute("success", "Đã bỏ công việc khỏi danh sách quan tâm.");
            } else {
                ra.addFlashAttribute("error", "Công việc này không nằm trong danh sách đã lưu.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/freelancer/saved-jobs";
    }

    @GetMapping("/applications")
    public String applications(Authentication auth,
                               @RequestParam(defaultValue = "all") String stage,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "latest") String sort,
                               @RequestParam(defaultValue = "") String budget,
                               @RequestParam(required = false) java.math.BigDecimal minBudget,
                               @RequestParam(required = false) java.math.BigDecimal maxBudget,
                               @RequestParam(defaultValue = "") String appliedWithin,
                               @RequestParam(defaultValue = "1") int page,
                               Model model) {
        User u = users.byEmail(auth.getName());
        List<JobApplication> all = new ArrayList<>(jobs.applications(u));
        String safeStage = stage == null ? "all" : stage.trim().toLowerCase(Locale.ROOT);
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String safeBudget = budget == null ? "" : budget.trim().toLowerCase(Locale.ROOT);
        String safeAppliedWithin = appliedWithin == null ? "" : appliedWithin.trim().toLowerCase(Locale.ROOT);

        long reviewCount = all.stream().filter(a -> stageOfApplication(a).equals("review")).count();
        long contactedCount = all.stream().filter(a -> stageOfApplication(a).equals("contacted")).count();
        long interviewCount = 0L; // Chưa có trạng thái phỏng vấn riêng trong database
        long hiredCount = all.stream().filter(a -> stageOfApplication(a).equals("hired")).count();
        long rejectedCount = all.stream().filter(a -> stageOfApplication(a).equals("rejected")).count();
        long withdrawnCount = all.stream().filter(a -> stageOfApplication(a).equals("withdrawn")).count();

        List<JobApplication> filtered = all.stream()
                .filter(a -> matchesApplicationStage(a, safeStage))
                .filter(a -> keyword.isBlank() || applicationText(a).contains(keyword))
                .filter(a -> matchesApplicationBudget(a, safeBudget, minBudget, maxBudget))
                .filter(a -> matchesAppliedWithin(a, safeAppliedWithin))
                .sorted(applicationComparator(sort))
                .toList();

        int pageSize = 5;
        int totalApplications = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalApplications / (double) pageSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int from = Math.min((currentPage - 1) * pageSize, totalApplications);
        List<JobApplication> pageApplications = filtered.subList(from, Math.min(from + pageSize, totalApplications));

        model.addAttribute("applications", pageApplications);
        model.addAttribute("stage", safeStage);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sort", sort);
        model.addAttribute("budget", safeBudget);
        model.addAttribute("minBudget", minBudget);
        model.addAttribute("maxBudget", maxBudget);
        model.addAttribute("appliedWithin", safeAppliedWithin);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("contactedCount", contactedCount);
        model.addAttribute("interviewCount", interviewCount);
        model.addAttribute("acceptedCount", hiredCount);
        model.addAttribute("rejectedCount", rejectedCount);
        model.addAttribute("withdrawnCount", withdrawnCount);
        double base = Math.max(1, all.size());
        model.addAttribute("reviewPercent", reviewCount * 100.0 / base);
        model.addAttribute("contactedPercent", contactedCount * 100.0 / base);
        model.addAttribute("interviewPercent", interviewCount * 100.0 / base);
        model.addAttribute("acceptedPercent", hiredCount * 100.0 / base);
        model.addAttribute("rejectedPercent", rejectedCount * 100.0 / base);
        model.addAttribute("withdrawnPercent", withdrawnCount * 100.0 / base);
        model.addAttribute("totalApplications", totalApplications);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        return "freelancer/applications";
    }

    // Chỉ hiển thị trạng thái có dữ liệu nghiệp vụ thật.
    // Hệ thống hiện chưa lưu sự kiện liên hệ/phỏng vấn riêng biệt.
    private String stageOfApplication(JobApplication app) {
        return switch (app.getStatus()) {
            case SUBMITTED -> "review";
            case SHORTLISTED -> "contacted";  // được client đưa vào shortlist
            case ACCEPTED -> "hired";
            case REJECTED -> "rejected";
            case WITHDRAWN -> "withdrawn";
        };
    }

    private boolean matchesApplicationStage(JobApplication app, String stage) {
        if (stage == null || stage.isBlank() || "all".equals(stage)) return true;
        return stageOfApplication(app).equals(stage);
    }

    private boolean matchesApplicationBudget(JobApplication app, String budget,
                                             java.math.BigDecimal minBudget,
                                             java.math.BigDecimal maxBudget) {
        java.math.BigDecimal amount = app.getJob().getBudget();
        if (amount == null) return (budget == null || budget.isBlank()) && minBudget == null && maxBudget == null;
        if (minBudget != null && amount.compareTo(minBudget) < 0) return false;
        if (maxBudget != null && amount.compareTo(maxBudget) > 0) return false;
        java.math.BigDecimal m5 = new java.math.BigDecimal("5000000");
        java.math.BigDecimal m15 = new java.math.BigDecimal("15000000");
        java.math.BigDecimal m30 = new java.math.BigDecimal("30000000");
        if ("under5".equals(budget) && amount.compareTo(m5) >= 0) return false;
        if ("5to15".equals(budget) && (amount.compareTo(m5) < 0 || amount.compareTo(m15) > 0)) return false;
        if ("15to30".equals(budget) && (amount.compareTo(m15) <= 0 || amount.compareTo(m30) > 0)) return false;
        if ("over30".equals(budget) && amount.compareTo(m30) <= 0) return false;
        return true;
    }

    private boolean matchesAppliedWithin(JobApplication app, String appliedWithin) {
        if (appliedWithin == null || appliedWithin.isBlank() || "all".equals(appliedWithin)) return true;
        if (app.getCreatedAt() == null) return false;
        LocalDateTime since = null;
        if ("7".equals(appliedWithin)) since = LocalDateTime.now().minusDays(7);
        else if ("30".equals(appliedWithin)) since = LocalDateTime.now().minusDays(30);
        else if ("90".equals(appliedWithin)) since = LocalDateTime.now().minusDays(90);
        return since == null || !app.getCreatedAt().isBefore(since);
    }

    private Comparator<JobApplication> applicationComparator(String sort) {
        String safeSort = sort == null ? "latest" : sort.trim().toLowerCase(Locale.ROOT);
        if ("budget_high".equals(safeSort)) {
            return Comparator.comparing((JobApplication a) -> a.getJob().getBudget(), Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("budget_low".equals(safeSort)) {
            return Comparator.comparing((JobApplication a) -> a.getJob().getBudget(), Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if ("oldest".equals(safeSort)) {
            return Comparator.comparing(JobApplication::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return Comparator.comparing(JobApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private String applicationText(JobApplication app) {
        String company = app.getJob().getCustomer().getCompanyName() != null
                ? app.getJob().getCustomer().getCompanyName() : app.getJob().getCustomer().getFullName();
        return (app.getJob().getTitle() + " " + company + " " + app.getProposal() + " "
                + Objects.toString(app.getJob().getRequiredSkills(), "") + " "
                + Objects.toString(app.getJob().getCategory(), "")).toLowerCase(Locale.ROOT);
    }

    @PostMapping("/applications/{id}/withdraw")
    public String withdraw(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            jobs.withdraw(id, users.byEmail(auth.getName()));
            ra.addFlashAttribute("success", "Đã rút đơn ứng tuyển.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/freelancer/applications";
    }

    @GetMapping("/projects")
    public String projects(Authentication auth,
                           @RequestParam(defaultValue = "ALL") String status,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "ALL") String due,
                           @RequestParam(defaultValue = "ALL") String completion,
                           @RequestParam(defaultValue = "NEWEST") String sort,
                           @RequestParam(defaultValue = "1") int page,
                           Model model) {
        User u = users.byEmail(auth.getName());
        List<Project> all = projects.forFreelancer(u);
        String safeStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String safeSort = sort == null ? "NEWEST" : sort.trim().toUpperCase(Locale.ROOT);
        Comparator<Project> projectOrder = switch (safeSort) {
            case "OLDEST" -> Comparator.comparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "DEADLINE" -> Comparator.comparing(p -> p.getApplication().getJob().getDeadline(), Comparator.nullsLast(Comparator.naturalOrder()));
            case "PRICE_HIGH" -> Comparator.comparing(Project::getTotalPrice, Comparator.nullsLast(Comparator.reverseOrder()));
            case "PRICE_LOW" -> Comparator.comparing(Project::getTotalPrice, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        if (!Set.of("OLDEST", "DEADLINE", "PRICE_HIGH", "PRICE_LOW").contains(safeSort)) safeSort = "NEWEST";

        Map<Long, Integer> allProgress = new HashMap<>();
        for (Project p : all) {
            List<Milestone> ms = projects.milestones(p);
            int percent = ms.isEmpty() ? projectProgressWithoutMilestones(p) : milestoneProgress(ms);
            allProgress.put(p.getId(), Math.max(0, Math.min(100, percent)));
        }
        LocalDate today = LocalDate.now();
        List<Project> filtered = all.stream()
                .filter(p -> matchesProjectStatus(p, safeStatus))
                .filter(p -> keyword.isBlank() || projectText(p).contains(keyword))
                .filter(p -> {
                    LocalDate deadline = p.getApplication().getJob().getDeadline();
                    if ("WEEK".equalsIgnoreCase(due)) return deadline != null && !deadline.isBefore(today) && !deadline.isAfter(today.plusDays(7));
                    if ("MONTH".equalsIgnoreCase(due)) return deadline != null && !deadline.isBefore(today) && !deadline.isAfter(today.plusDays(30));
                    if ("OVERDUE".equalsIgnoreCase(due)) return deadline != null && deadline.isBefore(today) && p.getStatus() != ProjectStatus.COMPLETED;
                    return true;
                })
                .filter(p -> {
                    int pct = allProgress.getOrDefault(p.getId(), 0);
                    if ("LT25".equalsIgnoreCase(completion)) return pct < 25;
                    if ("25TO50".equalsIgnoreCase(completion)) return pct >= 25 && pct < 50;
                    if ("50TO75".equalsIgnoreCase(completion)) return pct >= 50 && pct <= 75;
                    if ("GT75".equalsIgnoreCase(completion)) return pct > 75;
                    return true;
                })
                .sorted(projectOrder)
                .toList();
        int totalFiltered = filtered.size();
        int totalPages = Math.max(1, (totalFiltered + 4) / 5);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int from = (currentPage - 1) * 5;
        List<Project> displayed = filtered.subList(from, Math.min(from + 5, totalFiltered));

        Map<Long, Long> daysLeft = new HashMap<>();
        for (Project p : displayed) {
            LocalDate deadline = p.getApplication().getJob().getDeadline();
            if (deadline != null) daysLeft.put(p.getId(), java.time.temporal.ChronoUnit.DAYS.between(today, deadline));
        }
        model.addAttribute("daysLeft", daysLeft);
        model.addAttribute("sort", safeSort);
        Map<Long, Integer> milestoneTotal = new HashMap<>();
        Map<Long, Integer> milestoneDone = new HashMap<>();
        Map<Long, Integer> progress = new HashMap<>();
        Map<Long, BigDecimal> received = new HashMap<>();
        Map<Long, String> nextMilestone = new HashMap<>();

        for (Project p : displayed) {
            List<Milestone> ms = projects.milestones(p);
            int total = ms.size();
            int done = (int) ms.stream().filter(m -> m.getStatus() == MilestoneStatus.COMPLETED).count();
            milestoneTotal.put(p.getId(), total);
            milestoneDone.put(p.getId(), done);
            int pct = total == 0 ? projectProgressWithoutMilestones(p) : milestoneProgress(ms);
            progress.put(p.getId(), Math.max(0, Math.min(100, pct)));
            received.put(p.getId(), payments.forProject(p).stream()
                    .filter(pay -> pay.getStatus() == PaymentStatus.DISBURSED)
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            nextMilestone.put(p.getId(), ms.stream()
                    .filter(m -> m.getStatus() != MilestoneStatus.COMPLETED)
                    .findFirst().map(Milestone::getTitle).orElse("Không còn milestone chờ xử lý"));
        }

        BigDecimal revenue = payments.history(u).stream()
                .filter(pay -> pay.getStatus() == PaymentStatus.DISBURSED)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("projects", displayed);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("filteredCount", totalFiltered);
        model.addAttribute("due", due);
        model.addAttribute("completion", completion);
        model.addAttribute("soonCount", all.stream().filter(p -> {
            LocalDate d = p.getApplication().getJob().getDeadline();
            return d != null && !d.isBefore(today) && !d.isAfter(today.plusDays(7)) && p.getStatus() != ProjectStatus.COMPLETED;
        }).count());
        model.addAttribute("overdueCount", all.stream().filter(p -> {
            LocalDate d = p.getApplication().getJob().getDeadline();
            return d != null && d.isBefore(today) && p.getStatus() != ProjectStatus.COMPLETED;
        }).count());
        model.addAttribute("statusCounts", Map.of(
            "IN_PROGRESS", all.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS).count(),
            "WAITING_ACCEPTANCE", all.stream().filter(p -> p.getStatus() == ProjectStatus.WAITING_ACCEPTANCE).count(),
            "COMPLETED", all.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count()));
        model.addAttribute("status", safeStatus);
        model.addAttribute("q", q);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("paymentPendingCount", all.stream().filter(p -> p.getStatus() == ProjectStatus.WAITING_PAYMENT).count());
        model.addAttribute("activeCount", all.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS).count());
        model.addAttribute("waitingCount", all.stream().filter(p -> p.getStatus() == ProjectStatus.WAITING_ACCEPTANCE).count());
        model.addAttribute("completedCount", all.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count());
        model.addAttribute("cancelledCount", all.stream().filter(p -> p.getStatus() == ProjectStatus.CANCELLED).count());
        model.addAttribute("revenue", revenue);
        model.addAttribute("milestoneTotal", milestoneTotal);
        model.addAttribute("milestoneDone", milestoneDone);
        model.addAttribute("progress", progress);
        model.addAttribute("received", received);
        model.addAttribute("nextMilestone", nextMilestone);
        return "freelancer/projects";
    }

    private boolean matchesProjectStatus(Project project, String status) {
        if ("ALL".equals(status)) return true;
        if ("ACTIVE".equals(status)) {
            return project.getStatus() == ProjectStatus.IN_PROGRESS;
        }
        if ("SOON".equals(status)) {
            LocalDate d = project.getApplication().getJob().getDeadline();
            LocalDate now = LocalDate.now();
            return d != null && !d.isBefore(now) && !d.isAfter(now.plusDays(7))
                    && project.getStatus() != ProjectStatus.COMPLETED;
        }
        try {
            return project.getStatus() == ProjectStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private String projectText(Project p) {
        String customer = p.getCustomer().getCompanyName() != null ? p.getCustomer().getCompanyName() : p.getCustomer().getFullName();
        return (p.getApplication().getJob().getTitle() + " " + customer + " " + p.getApplication().getJob().getCategory())
                .toLowerCase(Locale.ROOT);
    }

    private int milestoneProgress(List<Milestone> milestones) {
        if (milestones == null || milestones.isEmpty()) return 0;
        double sum = 0d;
        for (Milestone milestone : milestones) {
            sum += switch (milestone.getStatus()) {
                case UNPAID -> 0d;
                case PAID -> 15d;
                case IN_PROGRESS -> 55d;
                case SUBMITTED -> 85d;
                case COMPLETED -> 100d;
            };
        }
        return (int) Math.round(sum / milestones.size());
    }

    private int projectProgressWithoutMilestones(Project p) {
        return switch (p.getStatus()) {
            case WAITING_PAYMENT -> 10;
            case IN_PROGRESS -> 55;
            case WAITING_ACCEPTANCE -> 85;
            case COMPLETED -> 100;
            case CANCELLED, DISPUTED -> 0;
        };
    }

    /**
     * Lịch sử của Freelancer gồm 3 nguồn dữ liệu thực: ứng tuyển, dự án và thanh toán.
     * Mỗi tab có trạng thái, thống kê, bộ lọc và phân trang riêng.
     */
    @GetMapping("/history")
    public String history(Authentication auth, Model model,
                          @RequestParam(defaultValue = "applications") String tab,
                          @RequestParam(defaultValue = "ALL") String status,
                          @RequestParam(defaultValue = "") String q,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "all") String category,
                          @RequestParam(defaultValue = "1") int page) {
        User u = users.byEmail(auth.getName());
        String activeTab = Set.of("applications", "projects", "transactions").contains(tab)
            ? tab : "applications";

        List<HistoryEntry> all = new ArrayList<>();
        Map<String, String> statusOptions = new LinkedHashMap<>();
        statusOptions.put("ALL", "Tất cả");

        if ("applications".equals(activeTab)) {
            statusOptions.put("ACCEPTED", "Đã nhận việc");
            statusOptions.put("SHORTLISTED", "Được ưu tiên");
            statusOptions.put("SUBMITTED", "Đang xem xét");
            statusOptions.put("REJECTED", "Bị từ chối");
            statusOptions.put("WITHDRAWN", "Đã rút");
            for (JobApplication a : jobs.applications(u)) {
                if (a.getJob() == null) continue;
                Job job = a.getJob();
                User customer = job.getCustomer();
                all.add(new HistoryEntry(a.getId(), job.getTitle(), historyCompany(customer),
                    job.getRequiredSkills(), job.getCategory(), a.getProposedPrice(), a.getCreatedAt(),
                    a.getStatus().name(), historyApplicationStatus(a.getStatus()),
                    "/jobs/" + job.getId(), "Đơn ứng tuyển"));
            }
        } else if ("projects".equals(activeTab)) {
            statusOptions.put("COMPLETED", "Đã hoàn thành");
            statusOptions.put("IN_PROGRESS", "Đang thực hiện");
            statusOptions.put("WAITING_ACCEPTANCE", "Chờ xác nhận");
            statusOptions.put("WAITING_PAYMENT", "Chờ thanh toán");
            statusOptions.put("CANCELLED", "Đã hủy");
            statusOptions.put("DISPUTED", "Tranh chấp");
            for (Project p : projects.forFreelancer(u)) {
                if (p.getApplication() == null || p.getApplication().getJob() == null) continue;
                Job job = p.getApplication().getJob();
                all.add(new HistoryEntry(p.getId(), job.getTitle(), historyCompany(p.getCustomer()),
                    job.getRequiredSkills(), job.getCategory(), p.getTotalPrice(), p.getCreatedAt(),
                    p.getStatus().name(), historyProjectStatus(p.getStatus()),
                    "/projects/" + p.getId(), "Dự án"));
            }
        } else {
            statusOptions.put("DISBURSED", "Đã giải ngân");
            statusOptions.put("PAID", "Đã thanh toán");
            statusOptions.put("PENDING", "Đang chờ");
            statusOptions.put("REFUNDED", "Đã hoàn tiền");
            statusOptions.put("FAILED", "Thất bại");
            for (Payment payment : payments.history(u)) {
                Project p = payment.getProject();
                if (p == null || p.getApplication() == null || p.getApplication().getJob() == null) continue;
                Job job = p.getApplication().getJob();
                LocalDateTime transactionDate = payment.getDisbursedAt() != null
                    ? payment.getDisbursedAt() : (payment.getPaidAt() != null
                    ? payment.getPaidAt() : payment.getCreatedAt());
                all.add(new HistoryEntry(payment.getId(), job.getTitle(), historyCompany(p.getCustomer()),
                    job.getRequiredSkills(), job.getCategory(), payment.getAmount(), transactionDate,
                    payment.getStatus().name(), historyPaymentStatus(payment.getStatus()),
                    "/projects/" + p.getId(), payment.getTransactionCode()));
            }
        }

        String activeStatus = statusOptions.containsKey(status == null ? "" : status.toUpperCase(Locale.ROOT))
            ? status.toUpperCase(Locale.ROOT) : "ALL";
        List<String> categories = all.stream().map(HistoryEntry::category)
            .filter(c -> c != null && !c.isBlank()).distinct().sorted().toList();
        String activeCategory = categories.contains(category) ? category : "all";
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        LocalDate safeFrom = from, safeTo = to;
        if (safeFrom != null && safeTo != null && safeFrom.isAfter(safeTo)) {
            LocalDate temp = safeFrom; safeFrom = safeTo; safeTo = temp;
        }
        final LocalDate rangeStart = safeFrom, rangeEnd = safeTo;

        List<HistoryEntry> filtered = all.stream()
            .filter(e -> "ALL".equals(activeStatus) || activeStatus.equals(e.status()))
            .filter(e -> "all".equals(activeCategory) || activeCategory.equals(e.category()))
            .filter(e -> keyword.isBlank() || (e.title() + " " + e.company() + " " +
                (e.skills() == null ? "" : e.skills()) + " " + (e.reference() == null ? "" : e.reference()))
                .toLowerCase(Locale.ROOT).contains(keyword))
            .filter(e -> e.date() != null && (rangeStart == null || !e.date().toLocalDate().isBefore(rangeStart))
                && (rangeEnd == null || !e.date().toLocalDate().isAfter(rangeEnd)))
            .sorted(Comparator.comparing(HistoryEntry::date, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
        final int pageSize = 5;
        int pageCount = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        int currentPage = Math.max(1, Math.min(page, pageCount));
        int startAt = (currentPage - 1) * pageSize;
        int pageStart = Math.max(1, currentPage - 2);
        int pageEnd = Math.min(pageCount, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);

        model.addAttribute("historyTab", activeTab);
        model.addAttribute("historyPageStart", pageStart);
        model.addAttribute("historyPageEnd", pageEnd);
        model.addAttribute("historyEntries", filtered.subList(startAt, Math.min(startAt + pageSize, filtered.size())));
        model.addAttribute("historyTotalFiltered", filtered.size());
        model.addAttribute("historyPage", currentPage);
        model.addAttribute("historyPages", pageCount);
        model.addAttribute("historyTotal", all.size());
        model.addAttribute("historyStatuses", statusOptions);
        model.addAttribute("historyCategories", categories);
        model.addAttribute("historyStatus", activeStatus);
        model.addAttribute("historyCategory", activeCategory);
        model.addAttribute("historyQ", q == null ? "" : q);
        model.addAttribute("historyFrom", safeFrom);
        model.addAttribute("historyTo", safeTo);

        if ("applications".equals(activeTab)) {
            model.addAttribute("historyStatLabels", List.of("Tổng số lần ứng tuyển", "Đang xem xét", "Được ưu tiên", "Đã nhận việc"));
            model.addAttribute("historyStatValues", List.of(all.size(), historyCount(all, "SUBMITTED"),
                historyCount(all, "SHORTLISTED"), historyCount(all, "ACCEPTED")));
        } else if ("projects".equals(activeTab)) {
            model.addAttribute("historyStatLabels", List.of("Tổng dự án", "Đang thực hiện", "Chờ xác nhận", "Đã hoàn thành"));
            model.addAttribute("historyStatValues", List.of(all.size(), historyCount(all, "IN_PROGRESS"),
                historyCount(all, "WAITING_ACCEPTANCE"), historyCount(all, "COMPLETED")));
        } else {
            model.addAttribute("historyStatLabels", List.of("Tổng giao dịch", "Đang chờ", "Đã thanh toán", "Đã giải ngân"));
            model.addAttribute("historyStatValues", List.of(all.size(), historyCount(all, "PENDING"),
                historyCount(all, "PAID"), historyCount(all, "DISBURSED")));
        }
        return "freelancer/history";
    }

    public record HistoryEntry(Long id, String title, String company, String skills,
                               String category, BigDecimal amount, LocalDateTime date,
                               String status, String statusText, String detailUrl, String reference) {}

    private static long historyCount(List<HistoryEntry> entries, String status) {
        return entries.stream().filter(e -> status.equals(e.status())).count();
    }

    private static String historyCompany(User customer) {
        if (customer == null) return "Khách hàng";
        if (customer.getCompanyName() != null && !customer.getCompanyName().isBlank())
            return customer.getCompanyName();
        return customer.getFullName() != null ? customer.getFullName() : "Khách hàng";
    }

    private static String historyApplicationStatus(ApplicationStatus status) {
        return switch (status) {
            case SUBMITTED -> "Đang xem xét";
            case SHORTLISTED -> "Được ưu tiên";
            case ACCEPTED -> "Đã nhận việc";
            case REJECTED -> "Bị từ chối";
            case WITHDRAWN -> "Đã rút";
        };
    }

    private static String historyProjectStatus(ProjectStatus status) {
        return switch (status) {
            case WAITING_PAYMENT -> "Chờ thanh toán";
            case IN_PROGRESS -> "Đang thực hiện";
            case WAITING_ACCEPTANCE -> "Chờ xác nhận";
            case COMPLETED -> "Đã hoàn thành";
            case CANCELLED -> "Đã hủy";
            case DISPUTED -> "Tranh chấp";
        };
    }

    private static String historyPaymentStatus(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "Đang chờ";
            case PAID -> "Đã thanh toán";
            case DISBURSED -> "Đã giải ngân";
            case FAILED -> "Thất bại";
            case REFUNDED -> "Đã hoàn tiền";
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record ProfileCheck(String label, boolean done) {
    }

    private void addCharts(Model model,
                           User u,
                           LocalDate from,
                           LocalDate to,
                           String metric,
                           String pieMode) {
        String safeMetric = normalizeMetric(metric);
        String safePieMode = normalizePieMode(pieMode);
        DashboardService.ChartRange range = dashboard.normalizeRange(from, to);

        List<ChartPoint> bars = dashboard.bar(u, range.from(), range.to(), safeMetric);
        double max = bars.stream().mapToDouble(ChartPoint::value).max().orElse(0d);
        double total = dashboard.total(bars);
        List<PieSlice> pie = dashboard.pie(u, range.from(), range.to(), safePieMode);

        model.addAttribute("barData", bars);
        model.addAttribute("barMax", max <= 0 ? 1d : max);
        model.addAttribute("barTotal", total);
        model.addAttribute("hasBarData", total > 0d);
        model.addAttribute("pieData", pie);
        model.addAttribute("pieTotal", pie.stream().mapToLong(PieSlice::count).sum());
        model.addAttribute("pieGradient", gradient(pie));
        model.addAttribute("from", range.from());
        model.addAttribute("to", range.to());
        model.addAttribute("chartDays", range.days());
        model.addAttribute("metric", safeMetric);
        model.addAttribute("pieMode", safePieMode);
    }

    private String normalizeMetric(String metric) {
        return "revenue".equalsIgnoreCase(metric) ? "revenue" : "projects";
    }

    private String normalizePieMode(String pieMode) {
        return "category".equalsIgnoreCase(pieMode) ? "category" : "status";
    }

    private String gradient(List<PieSlice> slices) {
        if (slices == null || slices.isEmpty()) return "conic-gradient(#eef0f7 0 100%)";
        String[] colors = {"#6D28D9", "#0891B2", "#F97316", "#16A34A", "#E11D48", "#2563EB"};
        double start = 0;
        StringBuilder builder = new StringBuilder("conic-gradient(");
        for (int i = 0; i < slices.size(); i++) {
            double end = start + slices.get(i).percent();
            if (i > 0) builder.append(',');
            builder.append(colors[i % colors.length])
                    .append(' ')
                    .append(String.format(java.util.Locale.US, "%.2f", start))
                    .append("% ")
                    .append(String.format(java.util.Locale.US, "%.2f", end))
                    .append('%');
            start = end;
        }
        return builder.append(')').toString();
    }
}
