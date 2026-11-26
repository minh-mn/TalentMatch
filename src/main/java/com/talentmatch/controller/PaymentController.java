package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/payment")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
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
        }catch(Exception e){
            ra.addFlashAttribute("error",e.getMessage());
            if(milestoneId!=null)ra.addAttribute("milestoneId",milestoneId);
            return "redirect:/payment/project/"+id;
        }
    }
    @GetMapping("/vnpay/ipn") @ResponseBody
    public java.util.Map<String,String> ipn(@RequestParam java.util.Map<String,String> params){
        String txnRef=params.getOrDefault("vnp_TxnRef", "");
        String safeTxnRef=txnRef.matches("[A-Za-z0-9_-]{1,40}")?txnRef:"[invalid]";
        log.info("VNPay IPN received: txnRef={}, responseCode={}, transactionStatus={}",
                safeTxnRef, params.get("vnp_ResponseCode"), params.get("vnp_TransactionStatus"));
        if(!vnpay.verify(params)){
            log.warn("VNPay IPN rejected: invalid checksum or merchant code, txnRef={}",safeTxnRef);
            return java.util.Map.of("RspCode","97","Message","Invalid Checksum");
        }
        boolean success="00".equals(params.get("vnp_ResponseCode"))&&"00".equals(params.get("vnp_TransactionStatus"));
        try{
            String code=payments.confirmVnpay(params.get("vnp_TxnRef"),params.get("vnp_Amount"),success);
            log.info("VNPay IPN processed: txnRef={}, response={}",safeTxnRef,code);
            return java.util.Map.of("RspCode",code,"Message",code.equals("00")?"Confirm Success":"Rejected or already confirmed");
        }catch(Exception e){
            log.error("VNPay IPN processing failed: txnRef={}",safeTxnRef,e);
            return java.util.Map.of("RspCode","99","Message","Server Error");
        }
    }
    @GetMapping("/vnpay/return")
    public String paymentReturn(@RequestParam java.util.Map<String,String> params,Model model){
        boolean verified=vnpay.verify(params);
        String responseCode=params.get("vnp_ResponseCode");
        String transactionStatus=params.get("vnp_TransactionStatus");
        boolean bankAccepted=verified
                &&"00".equals(responseCode)
                &&(transactionStatus==null||transactionStatus.isBlank()||"00".equals(transactionStatus));
        if(bankAccepted){
            try{
                String confirmCode=payments.confirmVnpay(
                        params.get("vnp_TxnRef"),
                        params.get("vnp_Amount"),
                        true);
                log.info("VNPay return confirmed locally: txnRef={}, response={}",
                        txnRefForLog(params.get("vnp_TxnRef")),confirmCode);
            }catch(Exception e){
                log.error("VNPay return confirmation failed: txnRef={}",
                        txnRefForLog(params.get("vnp_TxnRef")),e);
            }
        }else if(verified){
            try{
                payments.confirmVnpay(params.get("vnp_TxnRef"),params.get("vnp_Amount"),false);
            }catch(Exception e){
                log.error("VNPay failed-return confirmation failed: txnRef={}",
                        txnRefForLog(params.get("vnp_TxnRef")),e);
            }
        }
        PaymentStatus status=bankAccepted?payments.statusByTransactionCode(params.get("vnp_TxnRef")).orElse(null):null;
        model.addAttribute("verified",verified);
        model.addAttribute("success",bankAccepted&&(status==PaymentStatus.PAID||status==PaymentStatus.DISBURSED));
        model.addAttribute("pending",bankAccepted&&(status==null||status==PaymentStatus.PENDING));
        model.addAttribute("failed",verified&&(!bankAccepted||status==PaymentStatus.FAILED||status==PaymentStatus.REFUNDED));
        model.addAttribute("transactionCode",params.get("vnp_TxnRef"));
        model.addAttribute("bankCode",params.get("vnp_BankCode"));
        model.addAttribute("responseCode",params.get("vnp_ResponseCode"));
        java.math.BigDecimal displayAmount=null;
        if(params.get("vnp_Amount")!=null){
            try{
                displayAmount=new java.math.BigDecimal(params.get("vnp_Amount"))
                        .divide(java.math.BigDecimal.valueOf(100));
            }catch(NumberFormatException ignored){}
        }
        model.addAttribute("amount",displayAmount);
        return "payment/vnpay-return";
    }

    private String txnRefForLog(String txnRef){
        return txnRef!=null&&txnRef.matches("[A-Za-z0-9_-]{1,40}")?txnRef:"[invalid]";
    }
}