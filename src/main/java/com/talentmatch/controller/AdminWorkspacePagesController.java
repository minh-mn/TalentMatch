package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminWorkspacePagesController {
  private final UserRepository users;
  private final ProjectRepository projects;
  private final PaymentRepository payments;
  private final AdminAuditLogRepository audits;
  private final JobRepository jobs;
  private final TalentReportRepository reports;
  public record CategoryRow(String name, long total, long open, long verified) {}
  public AdminWorkspacePagesController(UserRepository users,ProjectRepository projects,PaymentRepository payments,
           AdminAuditLogRepository audits,JobRepository jobs,TalentReportRepository reports){
    this.users=users;this.projects=projects;this.payments=payments;this.audits=audits;
    this.jobs=jobs;this.reports=reports;
  }
  @GetMapping("/verification") @Transactional(readOnly=true)
  public String verification(Model m,@RequestParam(defaultValue="") String q,
                             @RequestParam(defaultValue="pending") String status){
    var all=users.findAll().stream().filter(u->u.getRole()!=Role.ADMIN).toList();
    String keyword=q==null?"":q.strip().toLowerCase(Locale.ROOT);
    var result=all.stream().filter(u->"all".equals(status)||("pending".equals(status)&&!Boolean.TRUE.equals(u.getEnabled()))||("verified".equals(status)&&Boolean.TRUE.equals(u.getEnabled())))
        .filter(u->(u.getFullName()+" "+u.getEmail()).toLowerCase(Locale.ROOT).contains(keyword))
        .sorted(Comparator.comparing(User::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    m.addAttribute("active","verification");m.addAttribute("rows",result);m.addAttribute("q",q);m.addAttribute("status",status);
    m.addAttribute("pendingCount",all.stream().filter(u->!Boolean.TRUE.equals(u.getEnabled())).count());
    m.addAttribute("verifiedCount",all.stream().filter(u->Boolean.TRUE.equals(u.getEnabled())).count());
    m.addAttribute("lockedCount",all.stream().filter(u->Boolean.TRUE.equals(u.getLocked())).count());
    return "admin/verification";
  }
  @GetMapping("/settings")
  public String settings(Model m){
    m.addAttribute("active","settings");m.addAttribute("userCount",users.count());
    m.addAttribute("projectCount",projects.count());m.addAttribute("paymentCount",payments.count());
    m.addAttribute("auditCount",audits.count());m.addAttribute("reportCount",reports.count());
    return "admin/settings";
  }
  @GetMapping("/support") @Transactional(readOnly=true)
  public String support(Model m,@RequestParam(defaultValue="all") String status){
    var all=reports.findAllByOrderByCreatedAtDesc();
    var visible=all.stream().filter(r->"all".equals(status)||r.getStatus().equals(status)).limit(30).toList();
    m.addAttribute("active","support");m.addAttribute("status",status);m.addAttribute("rows",visible);
    m.addAttribute("totalReports",all.size());m.addAttribute("pendingReports",all.stream().filter(r->"PENDING".equals(r.getStatus())).count());
    m.addAttribute("reviewedReports",all.stream().filter(r->"REVIEWED".equals(r.getStatus())).count());
    m.addAttribute("resolvedReports",all.stream().filter(r->"RESOLVED".equals(r.getStatus())||"REJECTED".equals(r.getStatus())).count());
    m.addAttribute("disputedProjects",projects.countByStatus(ProjectStatus.DISPUTED));
    return "admin/support";
  }
  @GetMapping("/categories") @Transactional(readOnly=true)
  public String categories(Model m,@RequestParam(defaultValue="") String q){
    var all=jobs.findAll();
    var summary=all.stream().filter(j->j.getCategory()!=null&&!j.getCategory().isBlank())
      .collect(Collectors.groupingBy(Job::getCategory)).entrySet().stream()
      .map(e->new CategoryRow(e.getKey(),e.getValue().size(),
           e.getValue().stream().filter(j->j.getStatus()==JobStatus.OPEN).count(),
           e.getValue().stream().filter(Job::isVerified).count()))
      .filter(r->r.name().toLowerCase(Locale.ROOT).contains(q.strip().toLowerCase(Locale.ROOT)))
      .sorted(Comparator.comparingLong(CategoryRow::total).reversed()).toList();
    m.addAttribute("active","categories");m.addAttribute("rows",summary);
    m.addAttribute("q",q);m.addAttribute("categoryCount",summary.size());m.addAttribute("jobCount",all.size());
    Map<String,Long> skills=new HashMap<>();
    for(Job job:all)if(job.getRequiredSkills()!=null)for(String part:job.getRequiredSkills().split("[,;]")){
      String name=part.strip();if(!name.isBlank() && name.length()<80)skills.merge(name,1L,Long::sum);
    }
    m.addAttribute("skillRows",skills.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(25).toList());
    return "admin/categories";
  }
}
