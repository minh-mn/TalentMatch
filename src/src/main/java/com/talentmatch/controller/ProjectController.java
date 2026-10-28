package com.talentmatch.controller;

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

@Controller
@RequestMapping("/projects")
public class ProjectController {
    private final ProjectService projects; private final PaymentService payments; private final UserService users; private final com.talentmatch.repository.TalentReviewRepository reviews;
    public ProjectController(ProjectService projects,PaymentService payments,UserService users,com.talentmatch.repository.TalentReviewRepository reviews){this.projects=projects;this.payments=payments;this.users=users;this.reviews=reviews;}
    @GetMapping("/{id}") public String detail(@PathVariable Long id,Authentication auth,Model model){User u=users.byEmail(auth.getName());Project p=projects.get(id);projects.ensureParticipant(p,u);model.addAttribute("project",p);model.addAttribute("milestones",projects.milestones(p));model.addAttribute("payments",payments.forProject(p));model.addAttribute("reviews",reviews.findByProjectOrderByCreatedAtDesc(p));model.addAttribute("alreadyReviewed",reviews.existsByProjectAndAuthor(p,u));return "projects/detail";}
    @PostMapping("/{id}/milestones") public String addMilestone(@PathVariable Long id,@RequestParam String title,@RequestParam(required=false)String description,@RequestParam BigDecimal amount,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate deadline,Authentication auth,RedirectAttributes ra){return act(id,ra,()->projects.addMilestone(projects.get(id),users.byEmail(auth.getName()),title,description,amount,deadline),"Đã tạo Milestone.");}
    @PostMapping("/{id}/milestones/{milestoneId}/status") public String milestoneStatus(@PathVariable Long id,@PathVariable Long milestoneId,@RequestParam MilestoneStatus status,Authentication auth,RedirectAttributes ra){return act(id,ra,()->{
        Project p=projects.get(id);
        if(projects.milestones(p).stream().noneMatch(m->m.getId().equals(milestoneId)))throw new IllegalArgumentException("Milestone không thuộc dự án này.");
        projects.markMilestone(milestoneId,users.byEmail(auth.getName()),status);
    },"Đã cập nhật Milestone.");}
    @PostMapping("/{id}/agreement/sign") public String sign(@PathVariable Long id,Authentication auth,RedirectAttributes ra){return act(id,ra,()->projects.signAgreement(projects.get(id),users.byEmail(auth.getName())),"Đã xác nhận hợp đồng.");}
    @PostMapping("/{id}/cancel") public String cancel(@PathVariable Long id,@RequestParam String reason,Authentication auth,RedirectAttributes ra){return act(id,ra,()->projects.requestCancellation(projects.get(id),users.byEmail(auth.getName()),reason),"Đã ghi nhận yêu cầu hủy. Các khoản đã nạp (nếu có) cần Admin giải quyết.");}
    @PostMapping("/{id}/milestones/{mid}/revision") public String revision(@PathVariable Long id,@PathVariable Long mid,@RequestParam String note,Authentication auth,RedirectAttributes ra){return act(id,ra,()->{
        Project p=projects.get(id);if(projects.milestones(p).stream().noneMatch(m->m.getId().equals(mid)))throw new IllegalArgumentException("Milestone không thuộc dự án.");
        projects.requestRevision(mid,users.byEmail(auth.getName()),note);
    },"Đã gửi yêu cầu chỉnh sửa cho Freelancer.");}
    @PostMapping("/{id}/complete") public String complete(@PathVariable Long id,Authentication auth,RedirectAttributes ra){return act(id,ra,()->projects.completeProjectWithoutMilestones(projects.get(id),users.byEmail(auth.getName())),"Dự án đã nghiệm thu và hoàn thành.");}
    private String act(Long id,RedirectAttributes ra,Runnable r,String success){try{r.run();ra.addFlashAttribute("success",success);}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/projects/"+id;}
}
