package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/payment")
public class PaymentController {
    private final ProjectService projects;private final PaymentService payments;private final UserService users;
    public PaymentController(ProjectService projects,PaymentService payments,UserService users){this.projects=projects;this.payments=payments;this.users=users;}
    @GetMapping("/project/{id}") public String checkout(@PathVariable Long id,@RequestParam(required=false)Long milestoneId,Authentication auth,Model model){User u=users.byEmail(auth.getName());Project p=projects.get(id);if(u.getRole()!=Role.CUSTOMER||!p.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Chỉ khách hàng của dự án được thanh toán.");Milestone m=null;if(milestoneId!=null)m=projects.milestones(p).stream().filter(x->x.getId().equals(milestoneId)).findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy Milestone."));model.addAttribute("project",p);model.addAttribute("milestone",m);model.addAttribute("amount",payments.checkoutAmount(p,milestoneId));return "payment/checkout";}
    @PostMapping("/project/{id}") public String pay(@PathVariable Long id,@RequestParam(required=false)Long milestoneId,@RequestParam String method,Authentication auth,RedirectAttributes ra){try{Payment p=payments.pay(projects.get(id),milestoneId,users.byEmail(auth.getName()),method);ra.addFlashAttribute("success","Thanh toán thành công. Mã giao dịch: "+p.getTransactionCode());}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/projects/"+id;}
}
