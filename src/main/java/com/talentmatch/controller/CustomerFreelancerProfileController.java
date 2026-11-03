package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Client-facing Freelancer portfolio. Only real, public-facing profile data is presented. */
@Controller
public class CustomerFreelancerProfileController {
    private final UserService userService;
    private final UserRepository users;
    private final JobRepository jobs;
    private final ProjectRepository projects;
    private final TalentReviewRepository reviews;
    private final SavedFreelancerRepository saved;
    private final Path profileStorage;

    public CustomerFreelancerProfileController(UserService userService, UserRepository users,
            JobRepository jobs, ProjectRepository projects, TalentReviewRepository reviews,
            SavedFreelancerRepository saved,
            @Value("${talentmatch.profile.storage:uploads/freelancer-profile}") String storage) {
        this.userService = userService;
        this.users = users;
        this.jobs = jobs;
        this.projects = projects;
        this.reviews = reviews;
        this.saved = saved;
        this.profileStorage = Paths.get(storage).toAbsolutePath().normalize();
    }

    private User freelancer(Long id) {
        User user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.FREELANCER || Boolean.TRUE.equals(user.getLocked())
                || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hồ sơ Freelancer không khả dụng.");
        }
        return user;
    }

    private static String safeLink(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) return uri.toString();
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    private static String publicAvatar(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("/images/") && !value.contains("..") && !value.contains("\\")) return value;
        return safeLink(value);
    }

    @GetMapping("/customer/freelancers/{id}")
    @Transactional(readOnly = true)
    public String view(@PathVariable Long id, Authentication authentication, Model model) {
        User customer = userService.byEmail(authentication.getName());
        if (customer.getRole() != Role.CUSTOMER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        User freelancer = freelancer(id);

        List<Project> allProjects = projects.findByFreelancerOrderByCreatedAtDesc(freelancer);
        List<Project> completed = allProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.COMPLETED).toList();
        long activeCount = allProjects.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS
                || p.getStatus() == ProjectStatus.WAITING_ACCEPTANCE).count();
        List<TalentReview> feedback = reviews.findByTarget(freelancer).stream()
                .filter(r -> r.getRating() != null && r.getRating() >= 1 && r.getRating() <= 5)
                .sorted(Comparator.comparing(TalentReview::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        double rating = feedback.stream().mapToInt(TalentReview::getRating).average().orElse(0.0);
        Map<Integer, Long> ratingCounts = feedback.stream().collect(
                Collectors.groupingBy(TalentReview::getRating, Collectors.counting()));
        List<Map<String, Object>> ratingBars = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            long count = ratingCounts.getOrDefault(star, 0L);
            ratingBars.add(Map.of("star", star, "count", count,
                    "percent", feedback.isEmpty() ? 0L : Math.round(100.0 * count / feedback.size())));
        }
        List<String> skillList = freelancer.getSkills() == null ? List.of() : Arrays.stream(freelancer.getSkills().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(30).toList();
        List<Job> openJobs = jobs.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .filter(job -> job.getStatus() == JobStatus.OPEN).toList();
        List<Project> sharedProjects = projects.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .filter(p -> p.getFreelancer().getId().equals(id)).toList();
        Long chatProjectId = sharedProjects.isEmpty() ? null : sharedProjects.get(0).getId();

        model.addAttribute("profile", freelancer);
        model.addAttribute("profileSkills", skillList);
        model.addAttribute("portfolio", completed.stream().limit(6).toList());
        model.addAttribute("completedProjectCount", completed.size());
        model.addAttribute("activeProjectCount", activeCount);
        model.addAttribute("reviewCount", feedback.size());
        model.addAttribute("ratingAvg", rating);
        model.addAttribute("ratingBars", ratingBars);
        model.addAttribute("recentReviews", feedback.stream().limit(4).toList());
        model.addAttribute("clientJobs", openJobs);
        model.addAttribute("savedFreelancer", saved.findByCustomerAndFreelancer(customer, freelancer).isPresent());
        model.addAttribute("chatProjectId", chatProjectId);
        model.addAttribute("publicAvatarLink", publicAvatar(freelancer.getAvatar()));
        model.addAttribute("websiteLink", safeLink(freelancer.getWebsite()));
        model.addAttribute("linkedinLink", safeLink(freelancer.getLinkedinUrl()));
        return "customer/freelancer-profile";
    }

    /** Public avatar/cover only. CVs remain private and cannot be downloaded here. */
    @GetMapping("/freelancers/{id}/photo/{type}")
    @ResponseBody
    public ResponseEntity<Resource> photo(@PathVariable Long id, @PathVariable String type) {
        if (!"avatar".equals(type) && !"cover".equals(type)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        User freelancer = freelancer(id);
        String filename = "avatar".equals(type) ? freelancer.getProfileAvatarFile() : freelancer.getProfileCover();
        if (filename == null || !filename.matches("[a-f0-9\\-]{36}\\.(png|jpg)")) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path folder = profileStorage.resolve(id.toString()).resolve(type).normalize();
            Path file = folder.resolve(filename).normalize();
            if (!file.startsWith(folder) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();
            MediaType media = filename.endsWith("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(media)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (Exception ex) { return ResponseEntity.notFound().build(); }
    }
}
