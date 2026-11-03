package com.talentmatch.controller;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
@Controller
@RequestMapping("/admin")
public class AdminWorkspacePagesController {
  private final UserRepository users;
  private final ProjectRepository projects;
  private final PaymentRepository payments;
  private final AdminAuditLogRepository audits;
  public AdminWorkspacePagesController(UserRepository users, ProjectRepository projects, PaymentRepository payments, AdminAuditLogRepository audits) {
    this.users=users; this.projects=projects; this.payments=payments; this.audits=audits;
  }
  @GetMapping("/settings") public String settings(Model m) {
    m.addAttribute("userCount", users.count());
    m.addAttribute("projectCount", projects.count());
    m.addAttribute("paymentCount", payments.count());
    m.addAttribute("auditCount", audits.count());
    return "admin/settings";
  }
  @GetMapping("/support") public String support(){ return "admin/support"; }
}
