package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final MilestoneRepository milestones;
    private final PaymentRepository payments;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private ProjectDeliveryRepository deliveryEvidence;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private WalletDemoService walletDemo;
    private final UserRepository users;
    private final QuoteService quotes;
    private final AgreementService agreements;
    @org.springframework.beans.factory.annotation.Value("${talentmatch.settlement.hold-days:3}") private int holdDays;

    public ProjectService(ProjectRepository projects, JobApplicationRepository applications, JobRepository jobs,
                          MilestoneRepository milestones, PaymentRepository payments, UserRepository users,
                          QuoteService quotes, AgreementService agreements) {
        this.projects=projects; this.applications=applications; this.jobs=jobs; this.milestones=milestones; this.payments=payments; this.users=users;this.quotes=quotes;this.agreements=agreements;
    }

    public Project get(Long id){return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án."));}
    public List<Project> forFreelancer(User u){return projects.findByFreelancerOrderByCreatedAtDesc(u);}
    public List<Project> forCustomer(User u){return projects.findByCustomerOrderByCreatedAtDesc(u);}
    public List<Milestone> milestones(Project p){return milestones.findByProjectOrderByCreatedAtAsc(p);}

    public void ensureParticipant(Project p,User u){
        if (!p.getCustomer().getId().equals(u.getId()) && !p.getFreelancer().getId().equals(u.getId()) && u.getRole()!=Role.ADMIN)
            throw new IllegalArgumentException("Bạn không có quyền xem dự án này.");
    }

    @Transactional
    public void signAgreement(Project project,User user,int expectedVersion){
        Project p=projects.findById(project.getId()).orElseThrow(()->new IllegalArgumentException("Không tìm thấy dự án."));
        ensureParticipant(p,user);
        if(user.getRole()==Role.ADMIN)throw new IllegalArgumentException("Admin không được ký thay.");
        agreements.recordSignature(p,user,expectedVersion);
    }
    @Transactional
    public void requestCancellation(Project p,User user,String reason){
        ensureParticipant(p,user);
        if(p.getStatus()==ProjectStatus.COMPLETED||p.getStatus()==ProjectStatus.CANCELLED||p.getStatus()==ProjectStatus.DISPUTED)throw new IllegalArgumentException("Dự án đang kết thúc hoặc bị tranh chấp.");
        if(reason==null||reason.trim().length()<10)throw new IllegalArgumentException("Vui lòng nêu lý do ít nhất 10 ký tự.");
        p.setCancellationReason(reason.trim());
        boolean funded=payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(x->x.getStatus()==PaymentStatus.PAID||x.getStatus()==PaymentStatus.DISBURSED);
        if(funded){p.setStatus(ProjectStatus.DISPUTED);} else {p.setStatus(ProjectStatus.CANCELLED);}
        projects.save(p);
    }
    @Transactional
    public void requestRevision(Long milestoneId,User customer,String note){
        Milestone m=milestones.findById(milestoneId).orElseThrow(()->new IllegalArgumentException("Không tìm thấy giai đoạn."));
        Project p=m.getProject();if(!p.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Chỉ Client được yêu cầu sửa.");
        if(m.getStatus()!=MilestoneStatus.SUBMITTED)throw new IllegalArgumentException("Chỉ có thể yêu cầu sửa sau khi bàn giao.");
        if(note==null||note.trim().length()<10)throw new IllegalArgumentException("Ghi rõ nội dung cần sửa (ít nhất 10 ký tự).");
        m.setRevisionNote(note.trim());m.setStatus(MilestoneStatus.IN_PROGRESS);milestones.save(m);
        p.setStatus(ProjectStatus.IN_PROGRESS);projects.save(p);
    }
    @Transactional
    public void shortlist(Long applicationId,User customer,boolean value){
        JobApplication app=applications.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ứng viên."));
        if(!app.getJob().getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Bạn không sở hữu công việc này.");
        if(app.getStatus()==ApplicationStatus.ACCEPTED)throw new IllegalArgumentException("Ứng viên đã được chọn.");
        app.setStatus(value?ApplicationStatus.SHORTLISTED:ApplicationStatus.REJECTED);applications.save(app);
    }

    @Transactional
    public Project acceptApplication(Long applicationId,User customer){
        JobApplication initial=applications.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ứng viên."));
        Job job=jobs.findForUpdate(initial.getJob().getId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));
        JobApplication selected=applications.findForUpdate(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ứng viên."));
        if(!selected.getJob().getId().equals(job.getId()))
            throw new IllegalArgumentException("Ứng viên không thuộc công việc này.");
        if(!job.getCustomer().getId().equals(customer.getId()))
            throw new IllegalArgumentException("Bạn không sở hữu công việc này.");
        if(selected.getStatus()==ApplicationStatus.WITHDRAWN||selected.getStatus()==ApplicationStatus.REJECTED)
            throw new IllegalArgumentException("Không thể chọn đề xuất đã rút hoặc bị từ chối.");
        Optional<Project> existing=projects.findByApplication(selected);
        if(existing.isPresent())return existing.get();
        if(job.getStatus()!=JobStatus.OPEN && job.getStatus()!=JobStatus.PAUSED)throw new IllegalArgumentException("Công việc đã đóng hoặc bị hủy.");
        QuoteRevision agreed=quotes.accepted(selected).orElseThrow(()->
            new IllegalArgumentException("Hai bên cần chấp nhận báo giá trước khi chọn Freelancer."));
        for(JobApplication a:applications.findByJobOrderByCreatedAtDesc(job)){
            a.setStatus(a.getId().equals(selected.getId())?ApplicationStatus.ACCEPTED:ApplicationStatus.REJECTED);applications.save(a);
        }
        job.setStatus(JobStatus.CLOSED);jobs.save(job);
        Project p=new Project();p.setApplication(selected);p.setCustomer(customer);p.setFreelancer(selected.getFreelancer());
        p.setTotalPrice(agreed.getAmount());p.setStatus(ProjectStatus.WAITING_PAYMENT);
        Project saved=projects.save(p);agreements.createDraft(saved,agreed);return saved;
    }

    /** Configure a payment date separately from the delivery deadline. Scheduling does not constitute payment. */
    @Transactional
    public void setPaymentDueDate(Long projectId, Long milestoneId, User customer, LocalDate dueDate){
        Project p=projects.findForUpdate(projectId).orElseThrow(()->new IllegalArgumentException("Không tìm thấy dự án."));
        if(customer==null || customer.getRole()!=Role.CUSTOMER || !p.getCustomer().getId().equals(customer.getId()))
            throw new IllegalArgumentException("Chỉ Client của dự án được thiết lập hạn thanh toán.");
        if(p.getStatus()==ProjectStatus.COMPLETED || p.getStatus()==ProjectStatus.CANCELLED || p.getStatus()==ProjectStatus.DISPUTED)
            throw new IllegalArgumentException("Không thể đổi lịch thanh toán của dự án đã kết thúc hoặc tranh chấp.");
        if(dueDate==null)throw new IllegalArgumentException("Hãy chọn hạn thanh toán.");
        if(dueDate.isBefore(LocalDate.now()))throw new IllegalArgumentException("Hạn thanh toán mới không thể trong quá khứ.");
        List<Milestone> stages=milestones(p);
        if(milestoneId==null){
            if(!stages.isEmpty())throw new IllegalArgumentException("Dự án chia mốc cần thiết lập hạn riêng cho từng mốc.");
            if(payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(x->x.getMilestone()==null))
                throw new IllegalArgumentException("Đã phát sinh giao dịch: không thể sửa hạn thanh toán một lần.");
            p.setPaymentDueDate(dueDate); projects.save(p);return;
        }
        Milestone stage=stages.stream().filter(x->x.getId().equals(milestoneId)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Mốc không thuộc dự án."));
        if(stage.getDeadline()==null)throw new IllegalArgumentException("Cần có deadline bàn giao của mốc trước khi thiết lập hạn thanh toán.");
        if(dueDate.isAfter(stage.getDeadline()))throw new IllegalArgumentException("Hạn thanh toán không được sau deadline bàn giao mốc.");
        if(stage.getStatus()!=MilestoneStatus.UNPAID || !payments.findByProjectAndMilestone(p,stage).isEmpty())
            throw new IllegalArgumentException("Đã phát sinh giao dịch hoặc mốc đang thực hiện: không thể sửa hạn thanh toán.");
        stage.setPaymentDueDate(dueDate); milestones.save(stage);
    }

    @Transactional
    public Milestone addMilestone(Project p,User customer,String title,String description,BigDecimal amount,LocalDate deadline){
        if(!p.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Chỉ khách hàng được tạo Milestone.");
        if(p.getStatus()!=ProjectStatus.WAITING_PAYMENT)throw new IllegalArgumentException("Chỉ được chia mốc trước khi bắt đầu thanh toán và thực hiện dự án.");
        if(payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(pay->pay.getStatus()==PaymentStatus.PENDING))throw new IllegalArgumentException("Đang có giao dịch chờ thanh toán; không được thay đổi mốc.");
        title=title==null?"":title.trim(); if(title.isBlank())throw new IllegalArgumentException("Tên Milestone không được để trống.");
        if(amount==null||amount.signum()<=0)throw new IllegalArgumentException("Giá trị Milestone phải lớn hơn 0.");
        if(deadline==null)throw new IllegalArgumentException("Milestone phải có deadline để theo dõi tiến độ và nghiệm thu.");
        if(deadline.isBefore(LocalDate.now()))throw new IllegalArgumentException("Deadline Milestone không được ở quá khứ.");
        if(payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(pay->pay.getMilestone()==null && (pay.getStatus()==PaymentStatus.PAID||pay.getStatus()==PaymentStatus.DISBURSED)))
            throw new IllegalArgumentException("Dự án đã thanh toán toàn bộ; không thể tạo Milestone sau thanh toán.");
        BigDecimal current=milestones(p).stream().map(Milestone::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(current.add(amount).compareTo(p.getTotalPrice())>0)throw new IllegalArgumentException("Tổng giá trị Milestone không được vượt tổng giá trị dự án.");
        Milestone m=new Milestone();m.setProject(p);m.setTitle(title);m.setDescription(description);m.setAmount(amount);m.setDeadline(deadline);return milestones.save(m);
    }

    @Transactional
    public void markMilestone(Long milestoneId,User actor,MilestoneStatus target){
        Milestone m=milestones.findById(milestoneId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Milestone."));
        Project p=m.getProject();ensureParticipant(p,actor);
        if(p.getStatus()==ProjectStatus.CANCELLED||p.getStatus()==ProjectStatus.DISPUTED||p.getStatus()==ProjectStatus.COMPLETED)throw new IllegalArgumentException("Dự án không cho phép cập nhật Milestone.");
        if(target==MilestoneStatus.IN_PROGRESS){
            if(!p.getFreelancer().getId().equals(actor.getId()))throw new IllegalArgumentException("Chỉ Freelancer được bắt đầu Milestone.");
            if(m.getStatus()!=MilestoneStatus.PAID)throw new IllegalArgumentException("Milestone phải được thanh toán trước khi bắt đầu.");
            if(m.getDeadline()==null)throw new IllegalArgumentException("Milestone phải có deadline trước khi thực hiện.");
            List<Milestone> ordered=milestones(p);
            int idx=ordered.indexOf(m);
            if(idx>0){
                boolean prevDone=ordered.subList(0,idx).stream().allMatch(x->x.getStatus()==MilestoneStatus.COMPLETED);
                if(!prevDone)throw new IllegalArgumentException("Bạn cần hoàn thành và được nghiệm thu các milestone trước đó trước khi bắt đầu mốc này.");
            }
        }else if(target==MilestoneStatus.SUBMITTED){
            throw new IllegalArgumentException("Để gửi nghiệm thu, Freelancer phải tải file hoặc liên kết bàn giao tại mục Bàn giao sản phẩm.");
        }else if(target==MilestoneStatus.COMPLETED){
            if(!p.getCustomer().getId().equals(actor.getId()))throw new IllegalArgumentException("Chỉ khách hàng được nghiệm thu.");
            if(m.getStatus()!=MilestoneStatus.SUBMITTED)throw new IllegalArgumentException("Milestone chưa được bàn giao để nghiệm thu.");
            if(m.getProgressPercent()!=100)throw new IllegalArgumentException("Dữ liệu mốc không hợp lệ: đã gửi bàn giao nhưng tiến độ chưa đạt 100%. Hãy yêu cầu Freelancer chỉnh sửa và cập nhật tiến độ trước khi nghiệm thu.");
            if(deliveryEvidence!=null&&!deliveryEvidence.existsByProjectAndMilestone(p,m))throw new IllegalArgumentException("Chưa có minh chứng bàn giao của mốc này.");
            if(!payments.existsByProjectAndMilestoneAndStatusIn(p,m,java.util.List.of(PaymentStatus.PAID,PaymentStatus.DISBURSED)))throw new IllegalArgumentException("Mốc chưa được thanh toán và giữ tiền.");
            m.setStatus(MilestoneStatus.COMPLETED);
            m.setCompletedAt(LocalDateTime.now());
            milestones.save(m);
            // Giải ngân đúng mốc sau khi Client nghiệm thu; không giải ngân lần hai.
            for(Payment pay:payments.findByProjectAndMilestone(p,m)) {
                if(pay.getStatus()==PaymentStatus.PAID){
                    pay.setSettlementFeePercent(walletDemo==null?new BigDecimal("10"):walletDemo.feePercent());pay.setAcceptedAt(LocalDateTime.now());pay.setScheduledDisbursementAt(LocalDateTime.now().plusDays(holdDays));payments.save(pay);
                }
            }
            // Khoản PAID chỉ được giải ngân theo lịch sau nghiệm thu.
            List<Milestone> all=milestones(p);
            boolean allDone=all.stream().allMatch(x->x.getStatus()==MilestoneStatus.COMPLETED);
            BigDecimal total=all.stream().map(Milestone::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(allDone&&total.compareTo(p.getTotalPrice())==0){completeProject(p);}else{p.setStatus(ProjectStatus.IN_PROGRESS);projects.save(p);}
            return;
        }else throw new IllegalArgumentException("Trạng thái Milestone không hợp lệ cho thao tác này.");
        m.setStatus(target);milestones.save(m);
    }

    @Transactional
    public void completeProjectWithoutMilestones(Project p,User customer){
        if(!p.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Chỉ khách hàng được nghiệm thu dự án.");
        if(p.getStatus()!=ProjectStatus.WAITING_ACCEPTANCE)throw new IllegalArgumentException("Freelancer phải bàn giao sản phẩm trước khi Client nghiệm thu.");
        if(!p.isAgreementSigned())throw new IllegalArgumentException("Hai bên cần xác nhận hợp đồng trước.");
        if(!milestones(p).isEmpty())throw new IllegalArgumentException("Dự án có Milestone, hãy nghiệm thu theo từng giai đoạn.");
        if(deliveryEvidence!=null&&!deliveryEvidence.existsByProjectAndMilestone(p,null))throw new IllegalArgumentException("Chưa có sản phẩm bàn giao để nghiệm thu.");
        boolean paid=payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(x->x.getMilestone()==null&&x.getStatus()==PaymentStatus.PAID);
        if(!paid)throw new IllegalArgumentException("Dự án chưa được thanh toán.");
        for(Payment pay:payments.findByProjectOrderByCreatedAtDesc(p))if(pay.getMilestone()==null&&pay.getStatus()==PaymentStatus.PAID){pay.setSettlementFeePercent(walletDemo==null?new BigDecimal("10"):walletDemo.feePercent());pay.setAcceptedAt(LocalDateTime.now());pay.setScheduledDisbursementAt(LocalDateTime.now().plusDays(holdDays));payments.save(pay);}
        // Ví chỉ được ghi có sau khi dịch vụ giải ngân hoàn tất.
        completeProject(p);
    }

    private void completeProject(Project p){
        if(p.getStatus()==ProjectStatus.COMPLETED)return;
        p.setStatus(ProjectStatus.COMPLETED);p.setCompletedAt(LocalDateTime.now());projects.save(p);
        if(!p.isCreditAwarded()){
            User f=p.getFreelancer();f.setCompletedProjects((f.getCompletedProjects()==null?0:f.getCompletedProjects())+1);
            // Demo rule: +10 TalentCredit per verified completed project (idempotent).
            f.setTalentCreditBalance(f.getTalentCreditBalance()+10);users.save(f);
            p.setCreditAwarded(true);projects.save(p);
        }
    }
}
