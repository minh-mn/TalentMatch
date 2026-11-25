package com.talentmatch.controller;

import com.talentmatch.model.Project;
import com.talentmatch.model.Role;
import com.talentmatch.model.TalentReport;
import com.talentmatch.model.User;
import com.talentmatch.repository.ProjectRepository;
import com.talentmatch.repository.TalentReportRepository;
import com.talentmatch.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/customer/support")
public class CustomerSupportController {
    private final UserService users;
    private final ProjectRepository projects;
    private final TalentReportRepository reports;

    public CustomerSupportController(UserService users, ProjectRepository projects, TalentReportRepository reports) {
        this.users = users;
        this.projects = projects;
        this.reports = reports;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String index(Authentication auth, Model model,
                        @RequestParam(defaultValue="") String q,
                        @RequestParam(defaultValue="all") String status,
                        @RequestParam(required=false) Long selected) {
        User customer = customer(auth);
        List<SupportProject> ownedProjects = projects.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .map(project -> new SupportProject(project.getId(), projectTitle(project), project.getStatus().name()))
                .toList();
        List<SupportRequest> requests = reports.findByReporterOrderByCreatedAtDesc(customer).stream()
                .map(report -> new SupportRequest(
                        report.getId(),
                        report.getProject() == null ? null : report.getProject().getId(),
                        report.getProject() == null ? "Yêu cầu hỗ trợ chung" : projectTitle(report.getProject()),
                        report.getReportedUser() == null ? "Đội ngũ TalentMatch" : report.getReportedUser().getFullName(),
                        report.getReason(),
                        report.getDescription(),
                        report.getStatus(),
                        report.getCreatedAt()))
                .toList();
        model.addAttribute("ownedProjects", ownedProjects);
        String query=q==null?"":q.trim().toLowerCase(java.util.Locale.ROOT);
        String safeStatus=java.util.Set.of("all","PENDING","REVIEWING","RESOLVED","REJECTED","NEED_EVIDENCE").contains(status)?status:"all";
        List<SupportRequest> filtered=requests.stream().filter(r->"all".equals(safeStatus)||safeStatus.equalsIgnoreCase(r.status()))
           .filter(r->query.isBlank()||(r.projectTitle()+" "+r.reason()+" "+r.description()+" "+r.id()).toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
        model.addAttribute("requests",filtered);
        model.addAttribute("totalRequests",requests.size());
        model.addAttribute("pendingRequests",requests.stream().filter(r->"PENDING".equals(r.status())).count());
        model.addAttribute("processedRequests",requests.stream().filter(r->"RESOLVED".equals(r.status())||"REJECTED".equals(r.status())).count());
        model.addAttribute("q",q);model.addAttribute("statusFilter",safeStatus);
        model.addAttribute("selectedRequest",selected==null?null:filtered.stream().filter(r->r.id().equals(selected)).findFirst().orElse(null));
        return "customer/support";
    }

    @PostMapping("/reports")
    @Transactional
    public String report(@RequestParam Long projectId,
                         @RequestParam String reason,
                         @RequestParam String description,
                         Authentication auth,
                         RedirectAttributes flash) {
        User customer = customer(auth);
        String cleanReason = reason == null ? "" : reason.strip();
        String cleanDescription = description == null ? "" : description.strip();
        if (cleanReason.isBlank() || cleanReason.length() > 120
                || cleanDescription.length() < 15 || cleanDescription.length() > 5000) {
            flash.addFlashAttribute("error", "Vui lòng chọn lý do và mô tả sự việc từ 15 đến 5000 ký tự.");
            return "redirect:/customer/support";
        }

        Project project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án cần hỗ trợ."));
        if (!project.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Bạn không có quyền gửi khiếu nại cho dự án này.");
        }
        if (project.getFreelancer() == null) {
            throw new IllegalStateException("Dự án chưa có Freelancer để tiếp nhận khiếu nại.");
        }

        TalentReport report = new TalentReport();
        report.setProject(project);
        report.setReporter(customer);
        report.setReportedUser(project.getFreelancer());
        report.setReason(cleanReason);
        report.setDescription(cleanDescription);
        reports.save(report);
        flash.addFlashAttribute("success", "Đã gửi khiếu nại đến đội ngũ hỗ trợ để xem xét.");
        return "redirect:/customer/support";
    }

    private User customer(Authentication auth) {
        if (auth == null) throw new AccessDeniedException("Vui lòng đăng nhập bằng tài khoản Client.");
        User user = users.byEmail(auth.getName());
        if (user.getRole() != Role.CUSTOMER) throw new AccessDeniedException("Chức năng chỉ dành cho Client.");
        return user;
    }

    private String projectTitle(Project project) {
        if (project.getApplication() == null || project.getApplication().getJob() == null) {
            return "Dự án #" + project.getId();
        }
        String title = project.getApplication().getJob().getTitle();
        return title == null || title.isBlank() ? "Dự án #" + project.getId() : title;
    }

    public record SupportProject(Long id, String title, String status) {}

    public record SupportRequest(Long id, Long projectId, String projectTitle, String reportedUser,
                                 String reason, String description, String status, LocalDateTime createdAt) {}
}
