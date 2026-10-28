package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;

@Controller
@RequestMapping("/admin/disputes")
public class AdminDisputeController {
    private final ProjectRepository projects;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final AdminAuditLogRepository audits;
    public AdminDisputeController(ProjectRepository projects,PaymentRepository payments,
                                  UserRepository users,AdminAuditLogRepository audits){
        this.projects=projects;this.payments=payments;this.users=users;this.audits=audits;
    }
    @GetMapping public String list(Model m){
        var disputeList=projects.findAll().stream().filter(p->p.getStatus()==ProjectStatus.DISPUTED)
            .sorted(Comparator.comparing(Project::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        m.addAttribute("projects",disputeList);
        return "admin/disputes";
    }

    /** Hoàn tiền demo chỉ cập nhật ledger nội bộ; thanh toán thật phải xử lý qua cổng và đối soát. */
    @PostMapping("/{id}/resolve")
    @Transactional
    public String resolve(@PathVariable Long id,@RequestParam boolean approveDemoRefund,
                          @RequestParam String note,Authentication actor,RedirectAttributes ra){
        try{
            if(note==null||note.strip().length()<8)throw new IllegalArgumentException("Cần ghi chú xử lý tối thiểu 8 ký tự.");
            Project p=projects.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy dự án."));
            if(p.getStatus()!=ProjectStatus.DISPUTED)throw new IllegalArgumentException("Dự án không còn tranh chấp đang xử lý.");
            var related=payments.findByProjectOrderByCreatedAtDesc(p);
            if(approveDemoRefund){
                if(related.stream().anyMatch(pay->pay.getStatus()==PaymentStatus.DISBURSED))
                    throw new IllegalArgumentException("Đã giải ngân, không thể tự động hoàn tiền; cần đối soát thủ công.");
                for(Payment pay:related)if(pay.getStatus()==PaymentStatus.PAID){
                    pay.setStatus(PaymentStatus.REFUNDED);payments.save(pay);
                }
                p.setStatus(ProjectStatus.CANCELLED);
                ra.addFlashAttribute("success","Đã hủy hợp đồng và ghi nhận hoàn tiền trên ledger DEMO. Không phát sinh hoàn tiền ngân hàng thật.");
            }else{
                p.setStatus(ProjectStatus.IN_PROGRESS);
                ra.addFlashAttribute("success","Đã kết thúc tranh chấp; dự án tiếp tục thực hiện.");
            }
            projects.save(p);
            User admin=users.findByEmailIgnoreCase(actor.getName()).orElseThrow();
            if(admin.getRole()!=Role.ADMIN)throw new IllegalArgumentException("Không đủ quyền Admin.");
            AdminAuditLog log=new AdminAuditLog();log.setActorId(admin.getId());log.setActorEmail(admin.getEmail());
            log.setAction(approveDemoRefund?"DISPUTE_CANCEL_REFUND_DEMO":"DISPUTE_CONTINUE");
            log.setTargetType("PROJECT");log.setTargetId(id);log.setDetails(note.strip().substring(0,Math.min(600,note.strip().length())));
            audits.save(log);
        }catch(Exception ex){TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();ra.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/admin/disputes";
    }
}
