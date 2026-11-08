package com.talentmatch.controller;

import com.talentmatch.service.ProjectService;
import com.talentmatch.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;

@Controller
@RequestMapping("/customer/payments/schedule")
public class CustomerPaymentScheduleController {
    private final ProjectService projects;
    private final UserService users;
    public CustomerPaymentScheduleController(ProjectService projects, UserService users){
        this.projects=projects;this.users=users;
    }
    @PostMapping("/{id}")
    public String set(@PathVariable Long id,@RequestParam(required=false) Long milestoneId,
                      @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate paymentDueDate,
                      Authentication auth,RedirectAttributes ra){
        try{
            projects.setPaymentDueDate(id,milestoneId,users.byEmail(auth.getName()),paymentDueDate);
            ra.addFlashAttribute("success","Đã lưu hạn thanh toán. Hạn thanh toán khác với deadline bàn giao.");
        }catch(RuntimeException ex){ra.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/customer/payments?tab=projects";
    }
}
