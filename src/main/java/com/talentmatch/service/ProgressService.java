package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class ProgressService {
 private final ProgressUpdateRepository updates;
 private final MilestoneRepository milestones;
 private final NotificationService notifications;
 public ProgressService(ProgressUpdateRepository updates,MilestoneRepository milestones,NotificationService notifications){
     this.updates=updates;this.milestones=milestones;this.notifications=notifications;
 }
 public List<ProgressUpdate> history(Project project){return updates.findByProjectOrderByCreatedAtDesc(project);}
 @Transactional
 public ProgressUpdate report(Project project,Long milestoneId,User actor,int percent,String note,String evidenceUrl){
     Milestone m=milestones.findForUpdate(milestoneId).orElseThrow(()->new IllegalArgumentException("Không tìm thấy milestone."));
     if(!m.getProject().getId().equals(project.getId()))throw new IllegalArgumentException("Milestone không thuộc dự án.");
     if(actor==null||actor.getRole()!=Role.FREELANCER||!project.getFreelancer().getId().equals(actor.getId()))
         throw new IllegalArgumentException("Chỉ Freelancer được báo cáo tiến độ.");
     if((project.getStatus()!=ProjectStatus.IN_PROGRESS && project.getStatus()!=ProjectStatus.WAITING_ACCEPTANCE) || !project.isAgreementSigned() ||m.getStatus()!=MilestoneStatus.IN_PROGRESS)
         throw new IllegalArgumentException("Milestone chưa ở giai đoạn đang thực hiện.");
     if(percent<m.getProgressPercent()||percent>100)throw new IllegalArgumentException("Tiến độ không thể giảm hoặc vượt 100%.");
     if(note==null||note.trim().length()<10||note.length()>3000)throw new IllegalArgumentException("Ghi chú tiến độ cần 10–3000 ký tự.");
     if(evidenceUrl!=null&&!evidenceUrl.isBlank()&&(!evidenceUrl.startsWith("https://")||evidenceUrl.length()>500))
         throw new IllegalArgumentException("Minh chứng phải là liên kết HTTPS tối đa 500 ký tự.");
     m.setProgressPercent(percent);milestones.save(m);
     ProgressUpdate update=new ProgressUpdate();update.setProject(project);update.setMilestone(m);update.setAuthor(actor);
     update.setPercent(percent);update.setNote(note.trim());update.setEvidenceUrl(evidenceUrl==null?null:evidenceUrl.trim());
     updates.save(update);
     notifications.send(project.getCustomer(),"Cập nhật tiến độ dự án",actor.getFullName()+" cập nhật tiến độ "+percent+"%.","/projects/"+project.getId());
     return update;
 }
}
