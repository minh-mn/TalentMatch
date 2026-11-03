package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class QuoteService {
    private final QuoteRevisionRepository revisions;
    private final JobApplicationRepository applications;
    private final ProjectRepository projects;
    private final NotificationService notifications;
    public QuoteService(QuoteRevisionRepository revisions,JobApplicationRepository applications,
                        ProjectRepository projects,NotificationService notifications){
        this.revisions=revisions;this.applications=applications;this.projects=projects;this.notifications=notifications;
    }
    public void ensureParticipant(JobApplication application,User user){
        if(user==null || (!application.getFreelancer().getId().equals(user.getId())
                && !application.getJob().getCustomer().getId().equals(user.getId())))
            throw new IllegalArgumentException("Bạn không tham gia thương lượng này.");
    }
    public boolean isFreelancer(JobApplication a,User u){return a.getFreelancer().getId().equals(u.getId());}
    public boolean isRecipient(QuoteRevision q,User u){return !q.getAuthor().getId().equals(u.getId());}
    public List<QuoteRevision> history(JobApplication a,User u){ensureParticipant(a,u);return revisions.findByApplicationOrderByVersionDesc(a);}
    public Optional<QuoteRevision> latest(JobApplication a){return revisions.findTopByApplicationOrderByVersionDesc(a);}
    public Optional<QuoteRevision> accepted(JobApplication a){return revisions.findTopByApplicationAndStatusOrderByVersionDesc(a,QuoteStatus.ACCEPTED);}
    public JobApplication application(Long id,User user){
        JobApplication a=applications.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy đơn ứng tuyển."));
        ensureParticipant(a,user);return a;
    }
    @Transactional
    public QuoteRevision createInitial(JobApplication application){
        JobApplication locked=applications.findForUpdate(application.getId()).orElseThrow();
        if(revisions.findTopByApplicationOrderByVersionDesc(locked).isPresent())
            return revisions.findTopByApplicationOrderByVersionDesc(locked).orElseThrow();
        QuoteRevision q=new QuoteRevision();q.setApplication(locked);q.setAuthor(locked.getFreelancer());
        q.setVersion(1);q.setAmount(locked.getProposedPrice());q.setEstimatedDays(locked.getEstimatedDays());
        String proposal=locked.getProposal()==null?"":locked.getProposal().trim();
        q.setScope(proposal.length()>5000?proposal.substring(0,5000):proposal);
        q.setRevisionPolicy("Các thay đổi ngoài phạm vi cần được hai bên thống nhất trước khi thực hiện.");
        q.setStatus(QuoteStatus.PENDING);
        return revisions.save(q);
    }
    // Chỉ chủ đơn Freelancer được bổ sung v1 cho dữ liệu ứng tuyển cũ chưa qua JobService.apply.
    @Transactional
    public QuoteRevision initializeHistorical(Long applicationId, User actor){
        JobApplication a=lockedActive(applicationId,actor);
        if(!isFreelancer(a,actor))throw new IllegalArgumentException("Chỉ Freelancer mới được tạo báo giá ban đầu.");
        if(latest(a).isPresent())throw new IllegalArgumentException("Báo giá đã có phiên bản, không thể khởi tạo lại.");
        return createInitial(a);
    }
    @Transactional
    public QuoteRevision counter(Long applicationId,User actor,BigDecimal amount,int days,String scope,String policy){
        JobApplication a=lockedActive(applicationId,actor);
        if(amount==null||amount.signum()<=0||amount.precision()>15||amount.scale()>2)
            throw new IllegalArgumentException("Giá báo phải là số dương, tối đa 2 chữ số thập phân.");
        if(days<1||days>3650)throw new IllegalArgumentException("Thời gian phải từ 1 đến 3650 ngày.");
        scope=checked(scope,20,5000,"Phạm vi công việc");
        policy=checked(policy,10,1000,"Chính sách chỉnh sửa");
        QuoteRevision previous=createInitial(a);
        if(previous.getStatus()==QuoteStatus.ACCEPTED)throw new IllegalArgumentException("Báo giá đã chốt, không thể thương lượng lại.");
        // Cho phép bên nhận đề xuất đối ứng; tác giả có thể gửi bản thay thế khi vẫn đang chờ.
        if(previous.getStatus()==QuoteStatus.PENDING){
            previous.setStatus(QuoteStatus.SUPERSEDED);previous.setResolvedAt(LocalDateTime.now());revisions.save(previous);
        }
        QuoteRevision newRevision=new QuoteRevision();newRevision.setApplication(a);newRevision.setAuthor(actor);
        newRevision.setVersion(previous.getVersion()+1);newRevision.setAmount(amount);newRevision.setEstimatedDays(days);
        newRevision.setScope(scope);newRevision.setRevisionPolicy(policy);newRevision.setStatus(QuoteStatus.PENDING);
        revisions.save(newRevision);
        User recipient=isFreelancer(a,actor)?a.getJob().getCustomer():a.getFreelancer();
        notifications.send(recipient,"Báo giá mới cho "+a.getJob().getTitle(),
                "Đề nghị thương lượng phiên bản "+newRevision.getVersion()+" đang chờ bạn phản hồi.",
                "/quotations/application/"+a.getId());
        return newRevision;
    }
    @Transactional
    public QuoteRevision resolve(Long applicationId,Long quoteId,User actor,boolean approve,String reason){
        JobApplication a=lockedActive(applicationId,actor);
        QuoteRevision q=revisions.findById(quoteId).orElseThrow(()->new IllegalArgumentException("Báo giá không tồn tại."));
        if(!q.getApplication().getId().equals(a.getId()))throw new IllegalArgumentException("Báo giá không thuộc đơn ứng tuyển.");
        QuoteRevision last=revisions.findTopByApplicationOrderByVersionDesc(a).orElseThrow();
        if(!last.getId().equals(q.getId())||q.getStatus()!=QuoteStatus.PENDING)
            throw new IllegalArgumentException("Chỉ phiên bản đang chờ mới nhất mới được phản hồi.");
        if(!isRecipient(q,actor))throw new IllegalArgumentException("Không thể tự chấp nhận hoặc từ chối báo giá của mình.");
        if(approve){
            q.setStatus(QuoteStatus.ACCEPTED);
            a.setProposedPrice(q.getAmount());a.setEstimatedDays(q.getEstimatedDays());applications.save(a);
        }else{
            reason=reason==null?"":reason.trim();
            if(reason.length()<10||reason.length()>1000)throw new IllegalArgumentException("Lý do từ chối cần 10–1000 ký tự.");
            q.setStatus(QuoteStatus.REJECTED);q.setRejectionReason(reason);
        }
        q.setResolvedAt(LocalDateTime.now());revisions.save(q);
        User recipient=q.getAuthor();
        notifications.send(recipient,approve?"Báo giá được chấp nhận":"Báo giá bị từ chối",
                "Đề nghị v"+q.getVersion()+" của "+a.getJob().getTitle()+" đã được phản hồi.",
                "/quotations/application/"+a.getId());
        return q;
    }
    private JobApplication lockedActive(Long id,User actor){
        JobApplication a=applications.findForUpdate(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy đơn ứng tuyển."));
        ensureParticipant(a,actor);
        if(a.getStatus()==ApplicationStatus.WITHDRAWN||a.getStatus()==ApplicationStatus.REJECTED)
            throw new IllegalArgumentException("Đơn ứng tuyển đã đóng hoặc bị rút.");
        if(a.getJob().getStatus()!=JobStatus.OPEN && a.getJob().getStatus()!=JobStatus.PAUSED)
            throw new IllegalArgumentException("Công việc không còn mở thương lượng.");
        if(projects.findByApplication(a).isPresent())throw new IllegalArgumentException("Dự án đã được tạo, báo giá không thể thay đổi.");
        if(accepted(a).isPresent())throw new IllegalArgumentException("Đề nghị đã được chốt; hãy chuyển sang hợp đồng.");
        return a;
    }
    private String checked(String text,int min,int max,String field){
        text=text==null?"":text.trim();if(text.length()<min||text.length()>max)
            throw new IllegalArgumentException(field+" cần từ "+min+" đến "+max+" ký tự.");return text;
    }
}
