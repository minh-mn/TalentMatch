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
    private final ProjectService projects;private final PaymentService payments;private final UserService users;private final VnPaySandboxService vnpay;
    public PaymentController(ProjectService projects,PaymentService payments,UserService users,VnPaySandboxService vnpay){this.projects=projects;this.payments=payments;this.users=users;this.vnpay=vnpay;}
    @GetMapping("/project/{id}") public String checkout(@PathVariable Long id,@RequestParam(required=false)Long milestoneId,Authentication auth,Model model){User u=users.byEmail(auth.getName());Project p=projects.get(id);if(u.getRole()!=Role.CUSTOMER||!p.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Chỉ khách hàng của dự án được thanh toán.");Milestone m=null;if(milestoneId!=null)m=projects.milestones(p).stream().filter(x->x.getId().equals(milestoneId)).findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy Milestone."));model.addAttribute("project",p);model.addAttribute("milestone",m);model.addAttribute("milestones",projects.milestones(p));model.addAttribute("amount",(milestoneId==null && !projects.milestones(p).isEmpty())?null:payments.checkoutAmount(p,milestoneId));model.addAttribute("sandboxReady",vnpay.configured());return "payment/checkout";}
    @PostMapping("/project/{id}") public String pay(@PathVariable Long id,@RequestParam(required=false)Long milestoneId,@RequestParam String method,Authentication auth,RedirectAttributes ra){try{Payment p=payments.pay(projects.get(id),milestoneId,users.byEmail(auth.getName()),method);ra.addFlashAttribute("success","Thanh toán thành công. Mã giao dịch: "+p.getTransactionCode());}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/projects/"+id;}
    @PostMapping("/vnpay/start/{id}")
    public String start(@PathVariable Long id,@RequestParam(required=false) Long milestoneId,Authentication auth,jakarta.servlet.http.HttpServletRequest request,RedirectAttributes ra){
        try{
            if(!vnpay.configured())throw new IllegalArgumentException("Chưa cấu hình mã merchant VNPay Sandbox.");
            Payment tx=payments.startVnpay(projects.get(id),milestoneId,users.byEmail(auth.getName()));
            return "redirect:"+vnpay.createPaymentUrl(tx,request.getRemoteAddr());
        }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());return "redirect:/payment/project/"+id;}
    }
    @GetMapping("/vnpay/ipn") @ResponseBody
    public java.util.Map<String,String> ipn(@RequestParam java.util.Map<String,String> params){
        if(!vnpay.verify(params))return java.util.Map.of("RspCode","97","Message","Invalid Checksum");
        boolean success="00".equals(params.get("vnp_ResponseCode"))&&"00".equals(params.get("vnp_TransactionStatus"));
        try{
            String code=payments.confirmVnpay(params.get("vnp_TxnRef"),params.get("vnp_Amount"),success);
            return java.util.Map.of("RspCode",code,"Message",code.equals("00")?"Confirm Success":"Rejected or already confirmed");
        }catch(Exception e){return java.util.Map.of("RspCode","99","Message","Server Error");}
    }
    @GetMapping("/vnpay/return")
    public String paymentReturn(@RequestParam java.util.Map<String,String> params,Model model){
        boolean verified=vnpay.verify(params);
        model.addAttribute("verified",verified);
        model.addAttribute("success",verified&&"00".equals(params.get("vnp_ResponseCode"))&&"00".equals(params.get("vnp_TransactionStatus")));
        return "payment/vnpay-return";
    }
}
