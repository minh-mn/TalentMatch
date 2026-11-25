package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.dto.ChartPoint;
import com.talentmatch.service.*;
import com.talentmatch.repository.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

@Controller
@RequestMapping("/customer/reports")
public class CustomerReportsController {
 private final UserService users;
 private final DashboardService dashboard;
 private final JobRepository jobs;
 private final ProjectRepository projects;
 private final PaymentRepository payments;
 public CustomerReportsController(UserService users, DashboardService dashboard, JobRepository jobs, ProjectRepository projects, PaymentRepository payments){
   this.users=users; this.dashboard=dashboard; this.jobs=jobs; this.projects=projects; this.payments=payments;
 }
 @GetMapping
 @Transactional(readOnly=true)
 public String view(Authentication auth, Model model,
     @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
     @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
     @RequestParam(defaultValue="projects") String metric,
     @RequestParam(defaultValue="status") String pieMode) {
   if(auth==null)throw new AccessDeniedException("Cần đăng nhập Client.");
   User u=users.byEmail(auth.getName());
   if(u==null||u.getRole()!=Role.CUSTOMER)throw new AccessDeniedException("Chỉ Client được xem báo cáo.");
   var range=dashboard.normalizeRange(from,to);
   String chosenMetric="revenue".equals(metric)?"revenue":"projects";
   String chosenPie="category".equals(pieMode)?"category":"status";
   var owned=projects.findByCustomerOrderByCreatedAtDesc(u);
   var company=u.getCompanyName()==null||u.getCompanyName().isBlank()?u.getFullName():u.getCompanyName();
   model.addAttribute("companyDisplayName",company);
   model.addAttribute("currentUser",u);
   model.addAttribute("active","reports");
   model.addAttribute("from",range.from());model.addAttribute("to",range.to());
   model.addAttribute("metric",chosenMetric);model.addAttribute("pieMode",chosenPie);
   model.addAttribute("stats",dashboard.customerStats(u));
   model.addAttribute("projectCount",owned.size());
   model.addAttribute("activeCount",owned.stream().filter(p->p.getStatus()==ProjectStatus.IN_PROGRESS||p.getStatus()==ProjectStatus.WAITING_ACCEPTANCE).count());
   model.addAttribute("completedCount",owned.stream().filter(p->p.getStatus()==ProjectStatus.COMPLETED).count());
   model.addAttribute("disputedCount",owned.stream().filter(p->p.getStatus()==ProjectStatus.DISPUTED).count());
   var points=dashboard.bar(u,range.from(),range.to(),chosenMetric);
   double max=points.stream().mapToDouble(ChartPoint::value).max().orElse(0d);
   model.addAttribute("barData",points);model.addAttribute("barMax",max<=0?1:max);
   model.addAttribute("pieData",dashboard.pie(u,range.from(),range.to(),chosenPie));
   model.addAttribute("recentProjects",owned.stream().limit(8).toList());
   return "customer/reports";
 }
}
