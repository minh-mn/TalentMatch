package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service
public class AgreementService {
 private final AgreementRevisionRepository agreements;
 private final AgreementChangeRequestRepository changes;
 private final ProjectRepository projects;
 private final PaymentRepository payments;
 public AgreementService(AgreementRevisionRepository agreements,AgreementChangeRequestRepository changes,ProjectRepository projects,PaymentRepository payments){this.agreements=agreements;this.changes=changes;this.projects=projects;this.payments=payments;}
 public java.util.List<AgreementRevision> history(Project project){return agreements.findByProjectOrderByVersionDesc(project);}
 public java.util.List<AgreementChangeRequest> requests(Project project){return changes.findByProjectOrderByCreatedAtDesc(project);}
 public boolean signedCurrent(Project project){
    AgreementRevision a=current(project);
    return a!=null && a.getCustomerSignedAt()!=null && a.getFreelancerSignedAt()!=null && a.getAmount().compareTo(project.getTotalPrice())==0 && !changes.existsByProjectAndStatus(project,"PENDING");
 }
 private Project lock(Project p){return projects.findForUpdate(p.getId()).orElseThrow(()->new IllegalArgumentException("Dự án không tồn tại."));}
 private void authorize(Project p,User u){if(u==null||(!p.getCustomer().getId().equals(u.getId())&&!p.getFreelancer().getId().equals(u.getId())))throw new IllegalArgumentException("Bạn không thuộc hợp đồng này.");}
 private void mutable(Project p){
    if(p.getStatus()==ProjectStatus.CANCELLED||p.getStatus()==ProjectStatus.COMPLETED||p.getStatus()==ProjectStatus.DISPUTED)throw new IllegalArgumentException("Dự án đã đóng hoặc đang tranh chấp.");
    if(payments.findByProjectOrderByCreatedAtDesc(p).stream().anyMatch(t->t.getStatus()==PaymentStatus.PAID||t.getStatus()==PaymentStatus.DISBURSED))throw new IllegalArgumentException("Hợp đồng đã phát sinh thanh toán; cần quy trình phụ lục riêng.");
 }
 @Transactional
 public AgreementChangeRequest requestChange(Project given,User actor,int expectedVersion,String proposedTerms,String reason){
    Project p=lock(given);authorize(p,actor);mutable(p);
    AgreementRevision a=current(p);
    if(a==null||a.getVersion()!=expectedVersion)throw new IllegalArgumentException("Hợp đồng đã đổi phiên bản, vui lòng tải lại.");
    if(changes.existsByProjectAndStatus(p,"PENDING"))throw new IllegalArgumentException("Đang có yêu cầu sửa chờ phản hồi.");
    proposedTerms=proposedTerms==null?"":proposedTerms.trim();reason=reason==null?"":reason.trim();
    if(proposedTerms.length()<20||proposedTerms.length()>10000)throw new IllegalArgumentException("Điều khoản đề xuất cần 20–10000 ký tự.");
    if(reason.length()<10||reason.length()>1000)throw new IllegalArgumentException("Lý do cần 10–1000 ký tự.");
    AgreementChangeRequest r=new AgreementChangeRequest();r.setProject(p);r.setRequester(actor);r.setBaseVersion(a.getVersion());r.setProposedTerms(proposedTerms);r.setReason(reason);return changes.save(r);
 }
 @Transactional
 public void respondChange(Project given,Long changeId,User actor,boolean approve,String reason){
    Project p=lock(given);authorize(p,actor);mutable(p);
    AgreementChangeRequest r=changes.findById(changeId).orElseThrow(()->new IllegalArgumentException("Không tìm thấy đề nghị sửa."));
    if(!r.getProject().getId().equals(p.getId())||!"PENDING".equals(r.getStatus()))throw new IllegalArgumentException("Yêu cầu không hợp lệ hoặc đã xử lý.");
    if(r.getRequester().getId().equals(actor.getId()))throw new IllegalArgumentException("Không thể tự duyệt đề nghị của mình.");
    AgreementRevision previous=current(p);
    if(previous==null||previous.getVersion()!=r.getBaseVersion())throw new IllegalArgumentException("Phiên bản nền đã thay đổi.");
    if(approve){
       AgreementRevision a=new AgreementRevision();a.setProject(p);a.setVersion(previous.getVersion()+1);a.setTerms(r.getProposedTerms());
       a.setAmount(previous.getAmount());a.setEstimatedDays(previous.getEstimatedDays());a.setScopeOfWork(previous.getScopeOfWork());agreements.save(a);
       p.setCustomerSignedAt(null);p.setFreelancerSignedAt(null);projects.save(p);r.setStatus("ACCEPTED");
    }else{
       reason=reason==null?"":reason.trim();if(reason.length()<10||reason.length()>1000)throw new IllegalArgumentException("Lý do từ chối cần 10–1000 ký tự.");
       r.setStatus("REJECTED");r.setResponseReason(reason);
    }
    r.setRespondedAt(LocalDateTime.now());changes.save(r);
 }

 @Transactional
 public AgreementRevision createDraft(Project project,QuoteRevision accepted){
    if(accepted==null||accepted.getStatus()!=QuoteStatus.ACCEPTED
        ||!accepted.getApplication().getId().equals(project.getApplication().getId()))
         throw new IllegalArgumentException("Chỉ báo giá đã chốt của đúng đơn mới được tạo hợp đồng.");
    var existing=agreements.findTopByProjectOrderByVersionDesc(project);
    if(existing.isPresent())return existing.get();
    AgreementRevision a=new AgreementRevision();a.setProject(project);a.setVersion(1);
    a.setAmount(accepted.getAmount());a.setEstimatedDays(accepted.getEstimatedDays());
    a.setScopeOfWork(accepted.getScope());
    a.setTerms("Thời hạn: "+accepted.getEstimatedDays()+" ngày. Chính sách chỉnh sửa: "+accepted.getRevisionPolicy()
        +". Việc ký kết và thanh toán tuân theo các điều kiện của nền tảng TalentMatch.");
    return agreements.save(a);
 }
 @Transactional
 public void recordSignature(Project project, User actor, int expectedVersion){
    Project locked=lock(project);authorize(locked,actor);mutable(locked);
    AgreementRevision current=agreements.lockVersion(locked.getId(),expectedVersion)
        .orElseThrow(()->new IllegalArgumentException("Phiên bản hợp đồng không tồn tại, vui lòng tải lại."));
    AgreementRevision newest=agreements.findTopByProjectOrderByVersionDesc(locked).orElseThrow();
    if(!newest.getId().equals(current.getId()))throw new IllegalArgumentException("Phiên bản hợp đồng đã thay đổi.");
    if(changes.existsByProjectAndStatus(locked,"PENDING"))throw new IllegalArgumentException("Hợp đồng đang chờ xử lý yêu cầu sửa.");
    project=locked;
    if(current.getVersion()!=expectedVersion)
        throw new IllegalArgumentException("Phiên bản hợp đồng đã thay đổi. Vui lòng tải lại trang trước khi ký.");
    if(current.getAmount().compareTo(project.getTotalPrice())!=0)
        throw new IllegalArgumentException("Giá hợp đồng không khớp giá trị dự án.");
    if(project.getStatus()==ProjectStatus.CANCELLED||project.getStatus()==ProjectStatus.COMPLETED||project.getStatus()==ProjectStatus.DISPUTED)
        throw new IllegalArgumentException("Không thể ký hợp đồng của dự án đã đóng hoặc đang tranh chấp.");
    LocalDateTime now=LocalDateTime.now();
    if(actor.getId().equals(project.getCustomer().getId())){
        if(current.getCustomerSignedAt()!=null)throw new IllegalArgumentException("Client đã ký phiên bản này.");
        current.setCustomerSignedAt(now);project.setCustomerSignedAt(now);
    } else if(actor.getId().equals(project.getFreelancer().getId())){
        if(current.getFreelancerSignedAt()!=null)throw new IllegalArgumentException("Freelancer đã ký phiên bản này.");
        current.setFreelancerSignedAt(now);project.setFreelancerSignedAt(now);
    }else throw new IllegalArgumentException("Bạn không có quyền ký hợp đồng này.");
    agreements.save(current);projects.save(project);
 }
 public AgreementRevision current(Project project){return agreements.findTopByProjectOrderByVersionDesc(project).orElse(null);}
}
