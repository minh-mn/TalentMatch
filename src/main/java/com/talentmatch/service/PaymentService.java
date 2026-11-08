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
    private final AgreementService agreements;
    private final PaymentRepository payments;
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;

    public PaymentService(PaymentRepository payments,ProjectRepository projects,MilestoneRepository milestones,AgreementService agreements){this.payments=payments;this.projects=projects;this.milestones=milestones;this.agreements=agreements;}

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

    private void validatePaymentPlan(Project p, Long milestoneId){
        List<Milestone> plan=milestones.findByProjectOrderByCreatedAtAsc(p);
        if(plan.isEmpty()){
            if(milestoneId!=null)throw new IllegalArgumentException("Hợp đồng thanh toán một lần, không có mốc.");
            if(p.getPaymentDueDate()==null)throw new IllegalArgumentException("Client cần thiết lập hạn thanh toán toàn hợp đồng trước khi trả tiền.");
            return;
        }
        BigDecimal sum=plan.stream().map(Milestone::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(sum.compareTo(p.getTotalPrice())!=0)
            throw new IllegalArgumentException("Tổng giá trị các mốc phải bằng giá trị hợp đồng trước khi thanh toán.");
        if(milestoneId==null)throw new IllegalArgumentException("Hợp đồng thanh toán theo mốc. Hãy chọn mốc cụ thể.");
        Milestone m=plan.stream().filter(x->x.getId().equals(milestoneId)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Không tìm thấy mốc của dự án."));
        if(m.getPaymentDueDate()==null)throw new IllegalArgumentException("Mốc chưa được thiết lập hạn thanh toán.");
        if(m.getDeadline()==null)throw new IllegalArgumentException("Mốc chưa có deadline bàn giao.");
    }

    @Transactional
    public Payment startVnpay(Project p,Long milestoneId,User customer){
        p=projects.findForUpdate(p.getId()).orElseThrow(()->new IllegalArgumentException("Dự án không tồn tại."));
        if(!p.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Không có quyền thanh toán.");
        if(p.getStatus()==ProjectStatus.COMPLETED||p.getStatus()==ProjectStatus.CANCELLED||p.getStatus()==ProjectStatus.DISPUTED)
            throw new IllegalArgumentException("Dự án không nhận thanh toán.");
        if(!agreements.signedCurrent(p))throw new IllegalArgumentException("Hai bên cần ký phiên bản hợp đồng hiện hành.");
        validatePaymentPlan(p,milestoneId);
        Milestone m=null;
        if(milestoneId!=null){
            m=milestones.findById(milestoneId).orElseThrow();
            if(!m.getProject().getId().equals(p.getId())||m.getStatus()!=MilestoneStatus.UNPAID)
                throw new IllegalArgumentException("Milestone không hợp lệ hoặc đã thanh toán.");
        }else if(!milestones.findByProjectOrderByCreatedAtAsc(p).isEmpty())throw new IllegalArgumentException("Vui lòng thanh toán milestone.");
        Collection<PaymentStatus> blocked=List.of(PaymentStatus.PENDING,PaymentStatus.PAID,PaymentStatus.DISBURSED);
        if(m!=null?payments.existsByProjectAndMilestoneAndStatusIn(p,m,blocked):payments.existsByProjectAndMilestoneIsNullAndStatusIn(p,blocked))
            throw new IllegalArgumentException("Đã có thanh toán được xác nhận.");
        Payment tx=new Payment();tx.setProject(p);tx.setMilestone(m);tx.setPayer(customer);
        tx.setAmount(m==null?p.getTotalPrice():m.getAmount());tx.setMethod("VNPAY_SANDBOX");tx.setStatus(PaymentStatus.PENDING);
        tx.setTransactionCode("VNS"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase());
        return payments.save(tx);
    }
    @Transactional
    public String confirmVnpay(String txnRef,String amount,boolean success){
        Payment tx=payments.lockByCode(txnRef).orElse(null);
        if(tx==null||!"VNPAY_SANDBOX".equals(tx.getMethod()))return "01";
        if(tx.getStatus()==PaymentStatus.PAID||tx.getStatus()==PaymentStatus.DISBURSED)return "02";
        if(tx.getStatus()!=PaymentStatus.PENDING)return "02";
        long expected;
        try{expected=Math.multiplyExact(tx.getAmount().longValueExact(),100L);
            if(expected!=Long.parseLong(amount))return "04";
        }catch(Exception ex){return "04";}
        if(!success){tx.setStatus(PaymentStatus.FAILED);payments.save(tx);return "00";}
        Project p=projects.findForUpdate(tx.getProject().getId()).orElseThrow();
        if(!agreements.signedCurrent(p))return "02";
        if(p.getStatus()==ProjectStatus.DISPUTED||p.getStatus()==ProjectStatus.CANCELLED||p.getStatus()==ProjectStatus.COMPLETED)return "02";
        Collection<PaymentStatus> paid=List.of(PaymentStatus.PAID,PaymentStatus.DISBURSED);
        if(tx.getMilestone()!=null){
            Milestone m=tx.getMilestone();
            if(m.getStatus()!=MilestoneStatus.UNPAID||payments.findByProjectAndMilestone(p,m).stream()
                .anyMatch(other->!other.getId().equals(tx.getId())&&paid.contains(other.getStatus())))return "02";
            m.setStatus(MilestoneStatus.PAID);milestones.save(m);
        }else if(payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(other->!other.getId().equals(tx.getId())&&other.getMilestone()==null&&paid.contains(other.getStatus())))return "02";
        tx.setStatus(PaymentStatus.PAID);tx.setPaidAt(LocalDateTime.now());payments.save(tx);
        p.setStatus(ProjectStatus.IN_PROGRESS);projects.save(p);return "00";
    }

    @Transactional
    public Payment pay(Project p,Long milestoneId,User customer,String method){
        p=projects.findForUpdate(p.getId()).orElseThrow(()->new IllegalArgumentException("Dự án không tồn tại."));
        if(!p.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Chỉ khách hàng của dự án được thanh toán.");
        if(p.getStatus()==ProjectStatus.COMPLETED||p.getStatus()==ProjectStatus.CANCELLED)throw new IllegalArgumentException("Dự án đã kết thúc.");
        if(!agreements.signedCurrent(p))throw new IllegalArgumentException("Hai bên phải ký đúng phiên bản hợp đồng hiện hành và xử lý xong yêu cầu sửa trước khi thanh toán.");
        validatePaymentPlan(p,milestoneId);
        if(p.getStatus()==ProjectStatus.DISPUTED)throw new IllegalArgumentException("Dự án đang được xử lý tranh chấp.");
        if(!java.util.Set.of("VNPAY_DEMO","MOMO_DEMO","BANK_CARD_DEMO").contains(method))throw new IllegalArgumentException("Chỉ cho phép phương thức thanh toán mô phỏng.");
        Milestone m=null;BigDecimal amount=p.getTotalPrice();
        Collection<PaymentStatus> active=List.of(PaymentStatus.PENDING,PaymentStatus.PAID,PaymentStatus.DISBURSED);
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
