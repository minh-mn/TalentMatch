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
    private final UserRepository users;

    public ProjectService(ProjectRepository projects, JobApplicationRepository applications, JobRepository jobs,
                          MilestoneRepository milestones, PaymentRepository payments, UserRepository users) {
        this.projects=projects; this.applications=applications; this.jobs=jobs; this.milestones=milestones; this.payments=payments; this.users=users;
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
    public void signAgreement(Project p,User user){
        ensureParticipant(p,user);
        if(p.getStatus()==ProjectStatus.COMPLETED||p.getStatus()==ProjectStatus.CANCELLED||p.getStatus()==ProjectStatus.DISPUTED)
            throw new IllegalArgumentException("Dự án không cho phép xác nhận hợp đồng.");
        if(user.getId().equals(p.getCustomer().getId()))p.setCustomerSignedAt(LocalDateTime.now());
        else if(user.getId().equals(p.getFreelancer().getId()))p.setFreelancerSignedAt(LocalDateTime.now());
        else throw new IllegalArgumentException("Chỉ hai bên của hợp đồng được xác nhận.");
        projects.save(p);
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
        JobApplication selected=applications.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ứng viên."));
        Job job=selected.getJob();
        if(selected.getStatus()==ApplicationStatus.WITHDRAWN||selected.getStatus()==ApplicationStatus.REJECTED)
            throw new IllegalArgumentException("Không thể chọn đề xuất đã rút hoặc bị từ chối.");
        if(!job.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Bạn không sở hữu công việc này.");
        if(job.getStatus()!=JobStatus.OPEN && job.getStatus()!=JobStatus.PAUSED)throw new IllegalArgumentException("Công việc đã đóng hoặc bị hủy.");
        Optional<Project> existing=projects.findByApplication(selected); if(existing.isPresent())return existing.get();
        for(JobApplication a:applications.findByJobOrderByCreatedAtDesc(job)){
            a.setStatus(a.getId().equals(selected.getId())?ApplicationStatus.ACCEPTED:ApplicationStatus.REJECTED);applications.save(a);
        }
        job.setStatus(JobStatus.CLOSED);jobs.save(job);
        Project p=new Project();p.setApplication(selected);p.setCustomer(customer);p.setFreelancer(selected.getFreelancer());
        p.setTotalPrice(selected.getProposedPrice());p.setStatus(ProjectStatus.WAITING_PAYMENT);return projects.save(p);
    }

    @Transactional
    public Milestone addMilestone(Project p,User customer,String title,String description,BigDecimal amount,LocalDate deadline){
        if(!p.getCustomer().getId().equals(customer.getId()))throw new IllegalArgumentException("Chỉ khách hàng được tạo Milestone.");
        if(p.getStatus()==ProjectStatus.COMPLETED||p.getStatus()==ProjectStatus.CANCELLED||p.getStatus()==ProjectStatus.DISPUTED)throw new IllegalArgumentException("Dự án đang kết thúc hoặc bị tranh chấp.");
        title=title==null?"":title.trim(); if(title.isBlank())throw new IllegalArgumentException("Tên Milestone không được để trống.");
        if(amount==null||amount.signum()<=0)throw new IllegalArgumentException("Giá trị Milestone phải lớn hơn 0.");
        if(deadline!=null&&deadline.isBefore(LocalDate.now()))throw new IllegalArgumentException("Deadline Milestone không được ở quá khứ.");
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
        }else if(target==MilestoneStatus.SUBMITTED){
            if(!p.getFreelancer().getId().equals(actor.getId()))throw new IllegalArgumentException("Chỉ Freelancer được gửi nghiệm thu.");
            if(m.getStatus()!=MilestoneStatus.IN_PROGRESS)throw new IllegalArgumentException("Milestone chưa ở trạng thái đang thực hiện.");
            p.setStatus(ProjectStatus.WAITING_ACCEPTANCE);projects.save(p);
        }else if(target==MilestoneStatus.COMPLETED){
            if(!p.getCustomer().getId().equals(actor.getId()))throw new IllegalArgumentException("Chỉ khách hàng được nghiệm thu.");
            if(m.getStatus()!=MilestoneStatus.SUBMITTED)throw new IllegalArgumentException("Milestone chưa được bàn giao để nghiệm thu.");
            m.setStatus(MilestoneStatus.COMPLETED);
            m.setCompletedAt(LocalDateTime.now());
            milestones.save(m);
            for(Payment pay:payments.findByProjectAndMilestone(p,m))if(pay.getStatus()==PaymentStatus.PAID){pay.setStatus(PaymentStatus.DISBURSED);pay.setDisbursedAt(LocalDateTime.now());payments.save(pay);}
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
        if(p.getStatus()!=ProjectStatus.IN_PROGRESS)throw new IllegalArgumentException("Dự án không ở trạng thái sẵn sàng nghiệm thu.");
        if(!p.isAgreementSigned())throw new IllegalArgumentException("Hai bên cần xác nhận hợp đồng trước.");
        if(!milestones(p).isEmpty())throw new IllegalArgumentException("Dự án có Milestone, hãy nghiệm thu theo từng giai đoạn.");
        boolean paid=payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(x->x.getMilestone()==null&&x.getStatus()==PaymentStatus.PAID);
        if(!paid)throw new IllegalArgumentException("Dự án chưa được thanh toán.");
        for(Payment pay:payments.findByProjectOrderByCreatedAtDesc(p))if(pay.getMilestone()==null&&pay.getStatus()==PaymentStatus.PAID){pay.setStatus(PaymentStatus.DISBURSED);pay.setDisbursedAt(LocalDateTime.now());payments.save(pay);}
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
