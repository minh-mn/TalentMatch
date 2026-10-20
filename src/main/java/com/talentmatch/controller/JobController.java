package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.service.JobService;
import com.talentmatch.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
public class JobController {
    private static final List<String> WORK_TYPES = List.of("REMOTE", "HYBRID", "ONSITE");
    private static final List<String> LEVELS = List.of("INTERN", "FRESHER", "JUNIOR", "MIDDLE", "SENIOR");
    private static final List<String> CATEGORIES = List.of(
            "Hành chính & Nhân sự", "Khác", "Kỹ thuật & Kiến trúc", "Luật & Pháp lý",
            "Lập trình & CNTT", "Marketing & Bán hàng", "Thiết kế & Sáng tạo",
            "Tài chính & Kế toán", "Viết lách & Nội dung", "Đào tạo & Giáo dục"
    );

    private final JobService jobs;
    private final UserService users;

    public JobController(JobService jobs, UserService users) {
        this.jobs = jobs;
        this.users = users;
    }

    @GetMapping({"/jobs", "/projects"})
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String skill,
                       @RequestParam(required = false) BigDecimal minBudget,
                       @RequestParam(required = false) BigDecimal maxBudget,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
                       @RequestParam(required = false) List<String> workType,
                       @RequestParam(required = false) List<String> level,
                       @RequestParam(required = false) List<String> levelGroup,
                       @RequestParam(required = false) String duration,
                       @RequestParam(defaultValue = "false") boolean featuredOnly,
                       @RequestParam(defaultValue = "false") boolean verifiedOnly,
                       @RequestParam(defaultValue = "false") boolean escrowOnly,
                       @RequestParam(defaultValue = "false") boolean savedOnly,
                       @RequestParam(defaultValue = "latest") String sort,
                       @RequestParam(defaultValue = "1") int page,
                       Authentication auth, Model model, HttpServletRequest request) {
        String chosenSort = normalizeSort(sort);
        List<String> selectedWorkTypes = sanitize(workType, WORK_TYPES);
        List<String> selectedGroups = sanitize(levelGroup, List.of("BEGINNER", "MIDDLE", "SENIOR"));
        List<String> selectedLevels = new ArrayList<>(sanitize(level, LEVELS));
        if (selectedGroups.contains("BEGINNER")) selectedLevels.addAll(List.of("INTERN", "FRESHER", "JUNIOR"));
        if (selectedGroups.contains("MIDDLE")) selectedLevels.add("MIDDLE");
        if (selectedGroups.contains("SENIOR")) selectedLevels.add("SENIOR");
        selectedLevels = selectedLevels.stream().distinct().toList();

        Set<Long> savedIds = new HashSet<>();
        Map<Long, ApplicationStatus> applicationStates = new HashMap<>();
        User currentUser = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            currentUser = users.byEmail(auth.getName());
            if (currentUser.getRole() == Role.FREELANCER) {
                jobs.saved(currentUser).forEach(s -> savedIds.add(s.getJob().getId()));
                jobs.applications(currentUser).forEach(a -> applicationStates.put(a.getJob().getId(), a.getStatus()));
            }
        }
        final User freelancer = currentUser != null && currentUser.getRole() == Role.FREELANCER ? currentUser : null;
        List<Job> allOpen = jobs.searchAdvanced(null, null, null, null, null, null, List.of(), List.of(), "latest");
        List<Job> filtered = new ArrayList<>(jobs.searchAdvanced(q, category, skill, minBudget, maxBudget,
                deadline, selectedWorkTypes, selectedLevels, chosenSort));
        filtered.removeIf(j -> (featuredOnly && !j.isFeatured()) || (verifiedOnly && !j.isVerified())
                || (escrowOnly && !j.isEscrowFunded()) || (savedOnly && !savedIds.contains(j.getId()))
                || !matchesDuration(j.getEstimatedDurationDays(), duration));
        if ("match_desc".equals(chosenSort) && freelancer != null)
            filtered.sort(Comparator.comparingInt((Job j) -> matchScore(freelancer, j)).reversed()
                    .thenComparing(Job::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / 10.0));
        int currentPage = Math.min(totalPages, Math.max(1, page));
        int from = Math.min((currentPage - 1) * 10, filtered.size());
        List<Job> pageJobs = filtered.subList(from, Math.min(from + 10, filtered.size()));
        Map<Long, String> posted = new HashMap<>();
        Map<Long, Long> daysLeft = new HashMap<>();
        Map<Long, Integer> matching = new HashMap<>();
        Map<Long, Long> proposals = new HashMap<>();
        for (Job j : pageJobs) {
            posted.put(j.getId(), relativeTime(j.getCreatedAt()));
            daysLeft.put(j.getId(), j.getDeadline() == null ? 0 : Math.max(0,
                    java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), j.getDeadline())));
            matching.put(j.getId(), freelancer == null ? 0 : matchScore(freelancer, j));
            proposals.put(j.getId(), jobs.applicationCount(j));
        }
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (String c : CATEGORIES) categoryCounts.put(c,
                allOpen.stream().filter(j -> c.equals(j.getCategory())).count());
        String path = "/jobs";
        String queryString = request.getQueryString();
        String returnTo = path + (queryString == null || queryString.isBlank() ? "" : "?" + queryString);

        model.addAttribute("jobs", pageJobs);
        model.addAttribute("totalJobs", filtered.size());
        model.addAttribute("allJobsCount", allOpen.size());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("paginationItems", paginationItems(currentPage, totalPages));
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("skill", skill);
        model.addAttribute("minBudget", minBudget);
        model.addAttribute("maxBudget", maxBudget);
        model.addAttribute("deadline", deadline);
        model.addAttribute("workType", selectedWorkTypes);
        model.addAttribute("levelGroup", selectedGroups);
        model.addAttribute("duration", duration);
        model.addAttribute("featuredOnly", featuredOnly);
        model.addAttribute("verifiedOnly", verifiedOnly);
        model.addAttribute("escrowOnly", escrowOnly);
        model.addAttribute("savedOnly", savedOnly);
        model.addAttribute("sort", chosenSort);
        model.addAttribute("sortLabel", sortLabel(chosenSort));
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("categoryCounts", categoryCounts);
        model.addAttribute("savedJobIds", savedIds);
        model.addAttribute("applicationStatusByJobId", applicationStates);
        model.addAttribute("postedAgo", posted);
        model.addAttribute("daysLeft", daysLeft);
        model.addAttribute("proposalCounts", proposals);
        model.addAttribute("matchScores", matching);
        model.addAttribute("hasFreelancerProfile", freelancer != null);
        model.addAttribute("profileField", freelancer == null ? null : freelancer.getField());
        model.addAttribute("returnTo", returnTo);
        return "jobs/list";
    }

    private boolean matchesDuration(Integer days, String duration) {
        if (duration == null || duration.isBlank()) return true;
        if (days == null || days <= 0) return false;
        return switch (duration) {
            case "under7" -> days <= 7;
            case "7to14" -> days >= 8 && days <= 14;
            case "15to30" -> days >= 15 && days <= 30;
            case "over30" -> days > 30;
            default -> true;
        };
    }

    @GetMapping("/jobs/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        Job job = jobs.get(id);
        model.addAttribute("job", job);
        model.addAttribute("saved", false);
        model.addAttribute("applied", false);
        model.addAttribute("currentApplication", null);
        model.addAttribute("proposalCount", jobs.applicationCount(job));
        model.addAttribute("postedAgo", relativeTime(job.getCreatedAt()));
        long remain = job.getDeadline() == null ? 0
                : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), job.getDeadline());
        model.addAttribute("daysLeft", Math.max(0, remain));
        model.addAttribute("matchScore", 0);

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            User u = users.byEmail(auth.getName());
            if (u.getRole() == Role.FREELANCER) {
                Optional<JobApplication> currentApplication = jobs.findApplication(job, u);
                model.addAttribute("saved", jobs.isSaved(job, u));
                model.addAttribute("applied", currentApplication.isPresent());
                model.addAttribute("currentApplication", currentApplication.orElse(null));
                model.addAttribute("matchScore", matchScore(u, job));
            }
        }

        List<Job> similarJobs = jobs.searchAdvanced(
                        null, job.getCategory(), null, null, null, null,
                        List.of(), List.of(), "latest")
                .stream()
                .filter(item -> !Objects.equals(item.getId(), job.getId()))
                .limit(3)
                .toList();
        model.addAttribute("similarJobs", similarJobs);
        return "jobs/detail";
    }

    @PostMapping("/jobs/{id}/save")
    public String save(@PathVariable Long id,
                       @RequestParam(required = false) String returnTo,
                       Authentication auth,
                       RedirectAttributes ra) {
        try {
            if (auth == null || !auth.isAuthenticated()) return "redirect:/login";
            boolean now = jobs.toggleSaved(jobs.get(id), users.byEmail(auth.getName()));
            ra.addFlashAttribute("success", now ? "Đã lưu công việc vào danh sách quan tâm." : "Đã bỏ công việc khỏi danh sách quan tâm.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (returnTo != null && returnTo.startsWith("/jobs") && !returnTo.contains("\n") && !returnTo.contains("\r")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/jobs/{id}/apply")
    public String apply(@PathVariable Long id,
                        @RequestParam String proposal,
                        @RequestParam BigDecimal proposedPrice,
                        @RequestParam Integer estimatedDays,
                        @RequestParam(required = false) String portfolioLink,
                        Authentication auth,
                        RedirectAttributes ra) {
        try {
            jobs.apply(jobs.get(id), users.byEmail(auth.getName()), proposal, proposedPrice, estimatedDays, portfolioLink);
            ra.addFlashAttribute("success", "Ứng tuyển thành công. Proposal và báo giá đã được gửi cho khách hàng.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/jobs/" + id;
    }

    private List<String> sanitize(List<String> input, List<String> allowed) {
        if (input == null) return List.of();
        return input.stream().filter(allowed::contains).distinct().toList();
    }

    private long countWorkType(List<Job> list, String value) {
        return list.stream().filter(j -> value.equalsIgnoreCase(j.getWorkType())).count();
    }

    private long countLevel(List<Job> list, String selected) {
        return list.stream().filter(j -> {
            String value = j.getExperienceLevel() == null ? "" : j.getExperienceLevel().toUpperCase(Locale.ROOT);
            if (selected.equals(value)) return true;
            if ("MIDDLE".equals(selected) && "INTERMEDIATE".equals(value)) return true;
            return "SENIOR".equals(selected) && "EXPERT".equals(value);
        }).count();
    }

    private String normalizeSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "budget_desc", "budget_asc", "competition_asc", "deadline_asc", "match_desc" -> sort;
            default -> "latest";
        };
    }

    private String sortLabel(String sort) {
        return switch (normalizeSort(sort)) {
            case "budget_desc" -> "Ngân sách cao nhất";
            case "budget_asc" -> "Ngân sách thấp nhất";
            case "competition_asc" -> "Ít cạnh tranh nhất";
            case "deadline_asc" -> "Deadline gần nhất";
            case "match_desc" -> "Phù hợp nhất";
            default -> "Mới nhất";
        };
    }

    private int matchScore(User freelancer, Job job) {
        if (freelancer == null || job == null) return 0;
        int score = 0;

        if (hasText(freelancer.getField()) && hasText(job.getCategory())
                && freelancer.getField().trim().equalsIgnoreCase(job.getCategory().trim())) {
            score += 35;
        }

        Set<String> freelancerSkills = skillTokens(freelancer.getSkills());
        Set<String> jobSkills = skillTokens(job.getRequiredSkills());
        int overlap = 0;
        for (String skill : freelancerSkills) if (jobSkills.contains(skill)) overlap++;
        score += Math.min(45, overlap * 15);

        int years = freelancer.getExperienceYears() == null ? 0 : freelancer.getExperienceYears();
        String level = job.getExperienceLevel() == null ? "" : job.getExperienceLevel().toUpperCase(Locale.ROOT);
        boolean experienceFits = switch (level) {
            case "INTERN", "FRESHER" -> true;
            case "JUNIOR" -> years >= 1;
            case "MIDDLE", "INTERMEDIATE" -> years >= 3;
            case "SENIOR", "EXPERT" -> years >= 5;
            default -> true;
        };
        if (experienceFits) score += 10;
        if ("REMOTE".equalsIgnoreCase(job.getWorkType())) score += 5;

        return Math.min(95, Math.max(0, score));
    }

    private Set<String> skillTokens(String raw) {
        if (!hasText(raw)) return Set.of();
        Set<String> result = new HashSet<>();
        for (String token : raw.split("[,;/]")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT)
                    .replace("product design", "ui/ux")
                    .replace("ui design", "ui/ux")
                    .replace("ux design", "ui/ux");
            if (!normalized.isBlank()) result.add(normalized);
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<Integer> paginationItems(int current, int total) {
        if (total <= 7) {
            List<Integer> items = new ArrayList<>();
            for (int i = 1; i <= total; i++) items.add(i);
            return items;
        }
        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        pages.add(1);
        pages.add(2);
        pages.add(3);
        for (int i = current - 1; i <= current + 1; i++) if (i > 3 && i < total) pages.add(i);
        pages.add(total);
        List<Integer> sorted = pages.stream().sorted().toList();
        List<Integer> result = new ArrayList<>();
        Integer prev = null;
        for (Integer value : sorted) {
            if (prev != null && value - prev > 1) result.add(0); // 0 = dấu ...
            result.add(value);
            prev = value;
        }
        return result;
    }

    private String relativeTime(java.time.LocalDateTime createdAt) {
        if (createdAt == null) return "Vừa đăng";
        long minutes = Math.max(0, java.time.Duration.between(createdAt, java.time.LocalDateTime.now()).toMinutes());
        if (minutes < 1) return "Vừa đăng";
        if (minutes < 60) return minutes + " phút trước";
        long hours = minutes / 60;
        if (hours < 24) return hours + " giờ trước";
        long days = hours / 24;
        if (days < 30) return days + " ngày trước";
        long months = days / 30;
        return months + " tháng trước";
    }
}
