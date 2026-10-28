package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class DeliveryService {
    private static final long MAX_FILE_BYTES=15L*1024*1024;
    private static final Set<String> EXTENSIONS=Set.of("pdf","zip","png","jpg","jpeg","docx","xlsx","txt","fig","psd");
    private final ProjectDeliveryRepository deliveries;
    private final MilestoneRepository milestones;
    private final ProjectRepository projects;
    private final NotificationService notifications;
    private final Path root;
    public DeliveryService(ProjectDeliveryRepository deliveries,MilestoneRepository milestones,ProjectRepository projects,
                           NotificationService notifications,@Value("${app.upload.dir:./talentmatch-uploads}") String directory){
        this.deliveries=deliveries;this.milestones=milestones;this.projects=projects;this.notifications=notifications;
        this.root=Path.of(directory).toAbsolutePath().normalize();
    }
    public List<ProjectDelivery> list(Project p){return deliveries.findByProjectOrderBySubmittedAtDesc(p);}
    public boolean hasDelivery(Project p,Milestone m){return deliveries.existsByProjectAndMilestone(p,m);}
    public ProjectDelivery getOwnedFile(Long deliveryId,User actor){
        ProjectDelivery delivery=deliveries.findById(deliveryId).orElseThrow(()->new IllegalArgumentException("Không có bản bàn giao."));
        Project p=delivery.getProject();
        if(!actor.getId().equals(p.getCustomer().getId()) && !actor.getId().equals(p.getFreelancer().getId()) && actor.getRole()!=Role.ADMIN)
            throw new IllegalArgumentException("Bạn không có quyền truy cập file này.");
        return delivery;
    }
    public Path file(ProjectDelivery delivery){
        if(delivery.getStorageKey()==null)throw new IllegalArgumentException("Bản bàn giao không có file đính kèm.");
        Path target=root.resolve(delivery.getStorageKey()).normalize();
        if(!target.getParent().equals(root))throw new IllegalArgumentException("Đường dẫn file không hợp lệ.");
        return target;
    }
    @Transactional
    public ProjectDelivery submit(Project p,Long milestoneId,User user,String externalUrl,String note,MultipartFile file){
        if(!p.getFreelancer().getId().equals(user.getId()))throw new IllegalArgumentException("Chỉ Freelancer được bàn giao sản phẩm.");
        if(!p.isAgreementSigned())throw new IllegalArgumentException("Hợp đồng cần được cả hai bên xác nhận.");
        if(p.getStatus()!=ProjectStatus.IN_PROGRESS && p.getStatus()!=ProjectStatus.WAITING_ACCEPTANCE)
            throw new IllegalArgumentException("Dự án chưa ở trạng thái cho phép bàn giao.");
        Milestone m=null;
        if(milestoneId!=null){
            m=milestones.findById(milestoneId).orElseThrow(()->new IllegalArgumentException("Milestone không tồn tại."));
            if(!m.getProject().getId().equals(p.getId()))throw new IllegalArgumentException("Milestone không thuộc dự án.");
            if(m.getStatus()!=MilestoneStatus.IN_PROGRESS&&m.getStatus()!=MilestoneStatus.SUBMITTED)
                throw new IllegalArgumentException("Milestone chưa sẵn sàng bàn giao.");
        }else if(!milestones.findByProjectOrderByCreatedAtAsc(p).isEmpty()){
            throw new IllegalArgumentException("Dự án có Milestone. Hãy bàn giao theo từng giai đoạn.");
        }
        boolean withFile=file!=null&&!file.isEmpty();
        String link=externalUrl==null?"":externalUrl.strip();
        if(!link.isBlank() && (!link.startsWith("https://")||link.length()>500||link.chars().anyMatch(c->c<=32)))
            throw new IllegalArgumentException("Đường dẫn sản phẩm phải là URL HTTPS hợp lệ (tối đa 500 ký tự).");
        if(!withFile&&link.isBlank())throw new IllegalArgumentException("Hãy đính kèm file hoặc cung cấp liên kết sản phẩm HTTPS.");
        String safeName=null, key=null;
        if(withFile){
            if(file.getSize()>MAX_FILE_BYTES)throw new IllegalArgumentException("File bàn giao tối đa 15 MB.");
            String name=Optional.ofNullable(file.getOriginalFilename()).orElse("san-pham");
            name=name.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
            if(name.isBlank()||name.length()>255||name.contains("\u0000"))throw new IllegalArgumentException("Tên file không hợp lệ.");
            int dot=name.lastIndexOf('.');
            String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
            if(!EXTENSIONS.contains(ext))throw new IllegalArgumentException("Định dạng file không hỗ trợ. Dùng PDF, ZIP, ảnh hoặc tài liệu Office.");
            safeName=name;
            key=UUID.randomUUID().toString()+"."+ext;
            try{
                Files.createDirectories(root);
                Files.copy(file.getInputStream(),root.resolve(key));
            }catch(IOException e){throw new IllegalStateException("Không thể lưu file bàn giao.",e);}
        }
        List<ProjectDelivery> previous=deliveries.findByProjectAndMilestoneOrderBySubmittedAtDesc(p,m);
        int version=previous.stream().mapToInt(ProjectDelivery::getVersion).max().orElse(0)+1;
        ProjectDelivery d=new ProjectDelivery();d.setProject(p);d.setMilestone(m);d.setSubmitter(user);
        d.setVersion(version);d.setStorageKey(key);d.setFileName(safeName);
        d.setExternalUrl(link.isBlank()?null:link);d.setNote(note==null?"":note.strip().substring(0,Math.min(5000,note.strip().length())));
        d=deliveries.save(d);
        if(m!=null){m.setStatus(MilestoneStatus.SUBMITTED);milestones.save(m);}
        p.setStatus(ProjectStatus.WAITING_ACCEPTANCE);projects.save(p);
        notifications.send(p.getCustomer(),"Freelancer đã bàn giao sản phẩm","Bản bàn giao v"+version+" đang chờ bạn nghiệm thu: "+p.getApplication().getJob().getTitle(),"/projects/"+p.getId());
        return d;
    }
}
