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
        addCharts(model, u, from, to, metric, pieMode);
        return "freelancer/dashboard";
    }

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
    public String saved(Authentication auth, Model model) {
        model.addAttribute("savedJobs", jobs.saved(users.byEmail(auth.getName())));
        return "freelancer/saved-jobs";
    }

    @PostMapping("/saved-jobs/{jobId}/remove")
    public String removeSaved(@PathVariable Long jobId, Authentication auth, RedirectAttributes ra) {
        try {
            jobs.toggleSaved(jobs.get(jobId), users.byEmail(auth.getName()));
            ra.addFlashAttribute("success", "Đã bỏ công việc khỏi danh sách quan tâm.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/freelancer/saved-jobs";
    }

    @GetMapping("/applications")
    public String applications(Authentication auth,
                               @RequestParam(defaultValue = "ALL") String status,
                               @RequestParam(required = false) String q,
                               Model model) {
        User u = users.byEmail(auth.getName());
        List<JobApplication> all = jobs.applications(u);
        String safeStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<JobApplication> filtered = all.stream()
                .filter(a -> matchesApplicationStatus(a, safeStatus))
                .filter(a -> keyword.isBlank() || applicationText(a).contains(keyword))
                .toList();

        model.addAttribute("applications", filtered);
        model.addAttribute("status", safeStatus);
        model.addAttribute("q", q);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("submittedCount", all.stream().filter(a -> a.getStatus() == ApplicationStatus.SUBMITTED).count());
        model.addAttribute("shortlistedCount", all.stream().filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED).count());
        model.addAttribute("acceptedCount", all.stream().filter(a -> a.getStatus() == ApplicationStatus.ACCEPTED).count());
        model.addAttribute("pendingCount", all.stream().filter(a -> a.getStatus() == ApplicationStatus.SUBMITTED || a.getStatus() == ApplicationStatus.SHORTLISTED).count());
        model.addAttribute("rejectedCount", all.stream().filter(a -> a.getStatus() == ApplicationStatus.REJECTED).count());
        model.addAttribute("withdrawnCount", all.stream().filter(a -> a.getStatus() == ApplicationStatus.WITHDRAWN).count());
        return "freelancer/applications";
    }

    private boolean matchesApplicationStatus(JobApplication app, String status) {
        if ("ALL".equals(status)) return true;
        if ("PENDING".equals(status)) {
            return app.getStatus() == ApplicationStatus.SUBMITTED || app.getStatus() == ApplicationStatus.SHORTLISTED;
        }
        try {
            return app.getStatus() == ApplicationStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private String applicationText(JobApplication app) {
        String company = app.getJob().getCustomer().getCompanyName() != null
                ? app.getJob().getCustomer().getCompanyName() : app.getJob().getCustomer().getFullName();
        return (app.getJob().getTitle() + " " + company + " " + app.getProposal()).toLowerCase(Locale.ROOT);
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
                           Model model) {
        User u = users.byEmail(auth.getName());
        List<Project> all = projects.forFreelancer(u);
        String safeStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<Project> filtered = all.stream()
                .filter(p -> matchesProjectStatus(p, safeStatus))
                .filter(p -> keyword.isBlank() || projectText(p).contains(keyword))
                .toList();

        Map<Long, Integer> milestoneTotal = new HashMap<>();
        Map<Long, Integer> milestoneDone = new HashMap<>();
        Map<Long, Integer> progress = new HashMap<>();
        Map<Long, BigDecimal> received = new HashMap<>();
        Map<Long, String> nextMilestone = new HashMap<>();

        for (Project p : filtered) {
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

        model.addAttribute("projects", filtered);
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

    @GetMapping("/history")
    public String history(Authentication auth, Model model) {
        User u = users.byEmail(auth.getName());
        model.addAttribute("applications", jobs.applications(u));
        model.addAttribute("projects", projects.forFreelancer(u));
        model.addAttribute("payments", payments.history(u));
        return "freelancer/history";
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
