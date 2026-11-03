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
    public AdminDisputeController(ProjectRepository projects, DisputeRepository disputes,
           ProjectDeliveryRepository deliveries, PaymentRepository payments,
           UserRepository users, AdminAuditLogRepository audits) {
        this.projects=projects; this.disputes=disputes; this.deliveries=deliveries;
        this.payments=payments; this.users=users; this.audits=audits;
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
        long active=rows.stream().filter(x->"Đang xử lý".equals(x.get("state"))).count();
        long done=rows.stream().filter(x->"Đã có kết luận".equals(x.get("state"))).count();
        long legacy=rows.stream().filter(x->x.get("disputeId")==null).count();
        String query=q.strip().toLowerCase(Locale.ROOT);
        var filtered=rows.stream().filter(x->query.isEmpty() || (String.valueOf(x.get("search"))).contains(query))
              .filter(x->"all".equals(status) || status.equals(x.get("filter"))).toList();
        int totalPages=Math.max(1,(filtered.size()+4)/5);
        int current=Math.max(1,Math.min(page,totalPages));
        var visible=filtered.subList((current-1)*5,Math.min(current*5,filtered.size()));
        Map<String,Object> detail=null;
        if(selected!=null)for(var row:rows)if(selected.equals(row.get("key"))) {detail=row;break;}
        if(detail==null && !visible.isEmpty()) detail=visible.get(0);
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
        String state=d==null?"Đang xử lý":("OPEN".equalsIgnoreCase(d.getStatus()) || "PENDING".equalsIgnoreCase(d.getStatus()) ? "Đang xử lý" : (d.getResolvedAt()!=null?"Đã có kết luận":"Cần rà soát"));
        r.put("key",key);r.put("disputeId",d==null?null:d.getId());r.put("projectId",p.getId());
        r.put("title",p.getApplication().getJob().getTitle());r.put("client",p.getCustomer().getFullName());
        r.put("freelancer",p.getFreelancer().getFullName());
        r.put("reason",d==null?p.getCancellationReason():d.getReason());
        r.put("evidence",d==null?null:d.getEvidenceNotes());
        r.put("state",state);r.put("filter","Đang xử lý".equals(state)?"open":"Đã có kết luận".equals(state)?"resolved":"review");
        r.put("created",d==null?p.getCreatedAt():d.getCreatedAt());
        r.put("resolution",d==null?null:d.getAdminResolutionNote());
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
        return "redirect:/admin/disputes";
    }
    /** Block the legacy direct PAID -> REFUNDED mutation path. */
    @PostMapping("/{id}/resolve")
    public String deprecatedResolve(RedirectAttributes redirect){
        redirect.addFlashAttribute("error","Không được thay đổi trạng thái hoàn tiền trực tiếp. Hãy xử lý qua module tài chính có đối soát.");
        return "redirect:/admin/disputes";
    }
}
