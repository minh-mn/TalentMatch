package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;

    public PaymentService(PaymentRepository payments,ProjectRepository projects,MilestoneRepository milestones){this.payments=payments;this.projects=projects;this.milestones=milestones;}

    public List<Payment> history(User user){
        if(user.getRole()==Role.CUSTOMER)return payments.findByPayerOrderByCreatedAtDesc(user);
        if(user.getRole()==Role.FREELANCER){
            return payments.findAll().stream().filter(p->p.getProject().getFreelancer().getId().equals(user.getId())).sorted(Comparator.comparing(Payment::getCreatedAt).reversed()).toList();
        }
        return payments.findAll().stream().sorted(Comparator.comparing(Payment::getCreatedAt).reversed()).toList();
    }
    public List<Payment> forProject(Project project){return payments.findByProjectOrderByCreatedAtDesc(project);}

    public BigDecimal checkoutAmount(Project p,Long milestoneId){
        if(milestoneId==null){
            if(!milestones.findByProjectOrderByCreatedAtAsc(p).isEmpty())throw new IllegalArgumentException("Dự án đã chia Milestone. Hãy chọn Milestone cần thanh toán.");
            return p.getTotalPrice();
        }
        Milestone m=milestones.findById(milestoneId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Milestone."));
        if(!m.getProject().getId().equals(p.getId()))throw new IllegalArgumentException("Milestone không thuộc dự án.");
        return m.getAmount();
    }

    @Transactional
    public Payment pay(Project p,Long milestoneId,User customer,String method){
        if(!p.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Chỉ khách hàng của dự án được thanh toán.");
        if(p.getStatus()==ProjectStatus.COMPLETED||p.getStatus()==ProjectStatus.CANCELLED)throw new IllegalArgumentException("Dự án đã kết thúc.");
        if(!p.isAgreementSigned())throw new IllegalArgumentException("Client và Freelancer phải xác nhận hợp đồng trước khi thanh toán.");
        if(p.getStatus()==ProjectStatus.DISPUTED)throw new IllegalArgumentException("Dự án đang được xử lý tranh chấp.");
        if(!java.util.Set.of("VNPAY_DEMO","MOMO_DEMO","BANK_CARD_DEMO").contains(method))throw new IllegalArgumentException("Chỉ cho phép phương thức thanh toán mô phỏng.");
        Milestone m=null;BigDecimal amount=p.getTotalPrice();
        Collection<PaymentStatus> active=List.of(PaymentStatus.PAID,PaymentStatus.DISBURSED);
        if(milestoneId!=null){
            m=milestones.findById(milestoneId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Milestone."));
            if(!m.getProject().getId().equals(p.getId()))throw new IllegalArgumentException("Milestone không thuộc dự án.");
            if(m.getStatus()!=MilestoneStatus.UNPAID)throw new IllegalArgumentException("Milestone này đã được thanh toán hoặc đang xử lý.");
            if(payments.existsByProjectAndMilestoneAndStatusIn(p,m,active))throw new IllegalArgumentException("Milestone đã có giao dịch thành công.");
            amount=m.getAmount();
        }else{
            if(!milestones.findByProjectOrderByCreatedAtAsc(p).isEmpty())throw new IllegalArgumentException("Dự án đã chia Milestone. Hãy thanh toán từng Milestone.");
            if(payments.existsByProjectAndMilestoneIsNullAndStatusIn(p,active))throw new IllegalArgumentException("Dự án đã được thanh toán.");
        }
        Payment pay=new Payment();pay.setProject(p);pay.setMilestone(m);pay.setPayer(customer);pay.setAmount(amount);pay.setMethod(method);
        pay.setStatus(PaymentStatus.PAID);pay.setPaidAt(LocalDateTime.now());pay.setTransactionCode("TM"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase());
        pay=payments.save(pay);
        if(m!=null){m.setStatus(MilestoneStatus.PAID);milestones.save(m);}p.setStatus(ProjectStatus.IN_PROGRESS);projects.save(p);return pay;
    }
}
