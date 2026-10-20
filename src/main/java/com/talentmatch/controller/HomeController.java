package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.JobService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class HomeController {
    private final JobService jobs; private final UserRepository users; private final JobRepository jobRepository;
    public HomeController(JobService jobs,UserRepository users,JobRepository jobRepository){this.jobs=jobs;this.users=users;this.jobRepository=jobRepository;}

    @GetMapping("/")
    public String home(Model model){
        var featuredJobs = jobs.search(null,null,null,null,null,null,null).stream().limit(4).toList();
        Map<Long, Long> proposalCounts = new HashMap<>();
        featuredJobs.forEach(job -> proposalCounts.put(job.getId(), (long) jobs.applicants(job).size()));
        model.addAttribute("featuredJobs", featuredJobs);
        model.addAttribute("proposalCounts", proposalCounts);
        model.addAttribute("featuredFreelancers",jobs.freelancers(null).stream().limit(3).toList());
        model.addAttribute("freelancerCount",users.findByRoleOrderByCreatedAtDesc(Role.FREELANCER).size());
        model.addAttribute("customerCount",users.findByRoleOrderByCreatedAtDesc(Role.CUSTOMER).size());
        model.addAttribute("openJobCount",jobRepository.countByStatus(JobStatus.OPEN));
        return "index";
    }

    @GetMapping({"/freelancers", "/hire"})
    public String freelancers(@RequestParam(required=false)String q,Model model){
        model.addAttribute("freelancers",jobs.freelancers(q));
        model.addAttribute("q",q);
        model.addAttribute("freelancerCount",users.findByRoleOrderByCreatedAtDesc(Role.FREELANCER).size());
        model.addAttribute("customerCount",users.findByRoleOrderByCreatedAtDesc(Role.CUSTOMER).size());
        model.addAttribute("openJobCount",jobRepository.countByStatus(JobStatus.OPEN));
        return "freelancer/public-list";
    }

    @GetMapping("/freelancers/{id}")
    public String freelancer(@PathVariable Long id,Model model){
        User u=users.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy Freelancer."));
        if(u.getRole()!=Role.FREELANCER||Boolean.TRUE.equals(u.getLocked()))throw new IllegalArgumentException("Hồ sơ Freelancer không khả dụng.");
        model.addAttribute("profile",u);return "freelancer/public-profile";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth){
        if(auth==null)return "redirect:/login";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")))return "redirect:/admin/dashboard";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CUSTOMER")))return "redirect:/customer/dashboard";
        return "redirect:/freelancer/dashboard";
    }
}
