package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;
import java.util.stream.*;

@Controller
@RequestMapping("/admin/disputes")
public class AdminDisputeController {
    private final ProjectRepository projects;
    private final DisputeRepository disputes;
    private final ProjectDeliveryRepository deliveries;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final AdminAuditLogRepository audits;
    private final com.talentmatch.service.NotificationService notifications;
    public AdminDisputeController(ProjectRepository projects, DisputeRepository disputes,
           ProjectDeliveryRepository deliveries, PaymentRepository payments,
           UserRepository users, AdminAuditLogRepository audits,
           com.talentmatch.service.NotificationService notifications) {
        this.projects=projects; this.disputes=disputes; this.deliveries=deliveries;
        this.payments=payments; this.users=users; this.audits=audits; this.notifications=notifications;
    }
    @GetMapping
    @Transactional(readOnly=true)
    public String list(@RequestParam(defaultValue="") String q,
                       @RequestParam(defaultValue="all") String status,
                       @RequestParam(defaultValue="1") int page,
                       @RequestParam(required=false) Long selected, Model model) {
        // Dispute records and projects marked DISPUTED are both included; no mock rows.
        var rows = new ArrayList<Map<String,Object>>();
        var allDisputes = disputes.findAll();
        var byProject = allDisputes.stream().collect(Collectors.groupingBy(d->d.getProject().getId()));
        for(Project project:projects.findAll()) {
            var found=byProject.getOrDefault(project.getId(), List.of());
            if(found.isEmpty() && project.getStatus()!=ProjectStatus.DISPUTED) continue;
            if(found.isEmpty()) rows.add(build(project,null));
            else for(Dispute d:found) rows.add(build(project,d));
        }
        rows.sort((a,b)->{
            var x=(java.time.LocalDateTime)a.get("created"); var y=(java.time.LocalDateTime)b.get("created");
            return Comparator.nullsLast(Comparator.<java.time.LocalDateTime>reverseOrder()).compare(x,y);
        });
        int total=rows.size();
        long active=rows.stream().filter(x->"open".equals(x.get("filter"))||"review".equals(x.get("filter"))).count();
        long done=rows.stream().filter(x->"resolved".equals(x.get("filter"))).count();
        long legacy=rows.stream().filter(x->x.get("disputeId")==null).count();
        String query=q.strip().toLowerCase(Locale.ROOT);
        var filtered=rows.stream().filter(x->query.isEmpty() || (String.valueOf(x.get("search"))).contains(query))
              .filter(x->"all".equals(status) || status.equals(x.get("filter"))).toList();
        int totalPages=Math.max(1,(filtered.size()+4)/5);
        int current=Math.max(1,Math.min(page,totalPages));
        var visible=filtered.subList((current-1)*5,Math.min(current*5,filtered.size()));
        Map<String,Object> detail=null;
        if(selected!=null)for(var row:rows)if(selected.equals(row.get("key"))) {detail=row;break;}

        if(detail!=null){
            Project project=projects.findById((Long)detail.get("projectId")).orElseThrow();
            model.addAttribute("selectedDeliveries",deliveries.findByProjectOrderBySubmittedAtDesc(project));
            model.addAttribute("selectedPayments",payments.findByProjectOrderByCreatedAtDesc(project));
        }else{
            model.addAttribute("selectedDeliveries",List.of());
            model.addAttribute("selectedPayments",List.of());
        }
        model.addAttribute("rows",visible); model.addAttribute("detail",detail);
        model.addAttribute("total",total); model.addAttribute("active",active);
        model.addAttribute("done",done); model.addAttribute("legacy",legacy);
        model.addAttribute("matching",filtered.size());model.addAttribute("page",current);
        model.addAttribute("pages",totalPages);model.addAttribute("q",q);model.addAttribute("status",status);
        return "admin/disputes";
    }
    private Map<String,Object> build(Project p,Dispute d){
        Map<String,Object> r=new HashMap<>();
        Long key=d==null ? -p.getId() : d.getId();
        String raw=d==null?"LEGACY":String.valueOf(d.getStatus()).toUpperCase(Locale.ROOT);
        String state=switch(raw){
            case "OPEN", "PENDING" -> "Mới tiếp nhận";
            case "UNDER_REVIEW", "IN_REVIEW" -> "Đang xác minh";
            case "NEEDS_EVIDENCE" -> "Cần bổ sung chứng cứ";
            case "RESOLVED", "CLOSED" -> "Đã có kết luận";
            default -> "Cần đối soát hồ sơ";
        };
        r.put("key",key);r.put("disputeId",d==null?null:d.getId());r.put("projectId",p.getId());
        r.put("title",p.getApplication().getJob().getTitle());r.put("client",p.getCustomer().getFullName());
        r.put("freelancer",p.getFreelancer().getFullName());
        r.put("reason",d==null?p.getCancellationReason():d.getReason());
        r.put("evidence",d==null?null:d.getEvidenceNotes());
        r.put("state",state);
        r.put("rawStatus",raw);
        r.put("filter",raw.equals("RESOLVED")||raw.equals("CLOSED")?"resolved":raw.equals("OPEN")||raw.equals("PENDING")?"open":"review");
        r.put("created",d==null?p.getCreatedAt():d.getCreatedAt());
        r.put("resolution",d==null?null:d.getAdminResolutionNote());
        r.put("openedBy",d==null?"Dữ liệu dự án cũ":d.getOpenedBy().getFullName());
        r.put("resolvedAt",d==null?null:d.getResolvedAt());
        r.put("projectStatus",p.getStatus().name());r.put("amount",p.getTotalPrice());
        r.put("search",("TC-"+key+" "+p.getId()+" "+p.getApplication().getJob().getTitle()+" "+p.getCustomer().getFullName()+" "+p.getFreelancer().getFullName()).toLowerCase(Locale.ROOT));
        return r;
    }
    /** Non-financial audit note only. Never changes a project or payment status. */
    @PostMapping("/{projectId}/note")
    @Transactional
    public String note(@PathVariable Long projectId,@RequestParam String note,
                       Authentication actor,RedirectAttributes redirect){
        if(note==null||note.strip().length()<10||note.length()>600){redirect.addFlashAttribute("error","Ghi chú cần từ 10 đến 600 ký tự.");return "redirect:/admin/disputes";}
        User admin=users.findByEmailIgnoreCase(actor.getName()).orElseThrow();
        if(admin.getRole()!=Role.ADMIN) throw new org.springframework.security.access.AccessDeniedException("Không đủ quyền");
        Project p=projects.findById(projectId).orElseThrow();
        boolean valid=p.getStatus()==ProjectStatus.DISPUTED || !disputes.findByProjectOrderByCreatedAtDesc(p).isEmpty();
        if(!valid){redirect.addFlashAttribute("error","Dự án không có hồ sơ tranh chấp.");return "redirect:/admin/disputes";}
        AdminAuditLog log=new AdminAuditLog();log.setActorId(admin.getId());log.setActorEmail(admin.getEmail());
        log.setAction("DISPUTE_REVIEW_NOTE");log.setTargetType("PROJECT");log.setTargetId(projectId);
        log.setDetails(note.strip());audits.save(log);
        redirect.addFlashAttribute("success","Đã lưu ghi chú kiểm tra vào nhật ký quản trị.");
        Long selectedId=disputes.findByProjectOrderByCreatedAtDesc(p).stream().findFirst().map(Dispute::getId).orElse(-p.getId());
        return "redirect:/admin/disputes?selected="+selectedId;
    }
    /** Administrative dispute workflow: never mutates Payment/Project money or closes project freeze. */
    @PostMapping("/{disputeId}/transition")
    @Transactional
    public String transition(@PathVariable Long disputeId,
                             @RequestParam String target,
                             @RequestParam String note,
                             Authentication actor, RedirectAttributes redirect){
        Dispute dispute=disputes.findById(disputeId).orElseThrow(()->new IllegalArgumentException("Không tìm thấy hồ sơ tranh chấp."));
        User admin=users.findByEmailIgnoreCase(actor.getName()).orElseThrow();
        if(admin.getRole()!=Role.ADMIN)throw new org.springframework.security.access.AccessDeniedException("Không đủ quyền.");
        if(note==null||note.strip().length()<10||note.length()>600){
            redirect.addFlashAttribute("error","Lý do xử lý cần 10 đến 600 ký tự.");
            return "redirect:/admin/disputes?selected="+disputeId;
        }
        String current=dispute.getStatus()==null?"OPEN":dispute.getStatus().toUpperCase(Locale.ROOT);
        boolean allowed=switch(target){
            case "UNDER_REVIEW" -> Set.of("OPEN","PENDING","NEEDS_EVIDENCE").contains(current);
            case "NEEDS_EVIDENCE" -> Set.of("OPEN","PENDING","UNDER_REVIEW").contains(current);
            case "RESOLVED" -> Set.of("UNDER_REVIEW","IN_REVIEW").contains(current);
            default -> false;
        };
        if(!allowed){redirect.addFlashAttribute("error","Không thể chuyển từ "+current+" sang "+target+".");return "redirect:/admin/disputes?selected="+disputeId;}
        String summary=note.strip();
        if("RESOLVED".equals(target)){
            // This is an administrative finding only, NOT a financial settlement.
            dispute.setAdminResolutionNote(summary);
            dispute.setResolvedBy(admin);
            dispute.setResolvedAt(java.time.LocalDateTime.now());
        }
        dispute.setStatus(target);
        disputes.save(dispute);
        AdminAuditLog log=new AdminAuditLog();log.setActorId(admin.getId());log.setActorEmail(admin.getEmail());
        log.setAction("DISPUTE_STATUS");log.setTargetType("DISPUTE");log.setTargetId(disputeId);
        log.setDetails(current+" -> "+target+" | "+summary);audits.save(log);
        String stateLabel=switch(target){case "UNDER_REVIEW"->"Đang xác minh";case "NEEDS_EVIDENCE"->"Cần bổ sung chứng cứ";case "RESOLVED"->"Đã có kết luận";default->target;};
        Project project=dispute.getProject();
        String notification="Hồ sơ TC-"+disputeId+" được cập nhật: "+stateLabel+". Hãy kiểm tra thông báo hoặc liên hệ bộ phận hỗ trợ.";
        notifications.send(project.getCustomer(),"Cập nhật tranh chấp TalentMatch",notification,"/notifications");
        notifications.send(project.getFreelancer(),"Cập nhật tranh chấp TalentMatch",notification,"/notifications");
        redirect.addFlashAttribute("success","Đã cập nhật hồ sơ và ghi nhật ký. Việc hoàn tiền/giải ngân phải thực hiện qua quy trình tài chính riêng.");
        return "redirect:/admin/disputes?selected="+disputeId;
    }

    /** Block the legacy direct PAID -> REFUNDED mutation path. */
    @PostMapping("/{id}/resolve")
    public String deprecatedResolve(RedirectAttributes redirect){
        redirect.addFlashAttribute("error","Không được thay đổi trạng thái hoàn tiền trực tiếp. Hãy xử lý qua module tài chính có đối soát.");
        return "redirect:/admin/disputes";
    }
}
