package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only contract oversight. Admin cannot sign, edit, cancel or release funds. */
@Controller
@RequestMapping("/admin/contracts")
public class AdminContractsController {
    private final ProjectRepository projects;
    private final AgreementRevisionRepository revisions;
    private final PaymentRepository payments;
    private final AdminAuditLogRepository audits;
    private final UserRepository users;
    public AdminContractsController(ProjectRepository projects, AgreementRevisionRepository revisions,
                                    PaymentRepository payments, AdminAuditLogRepository audits, UserRepository users) {
        this.projects=projects; this.revisions=revisions; this.payments=payments; this.audits=audits; this.users=users;
    }
    public record Row(Long id, String title, String client, String freelancer, java.math.BigDecimal amount,
                      String state, String stateLabel, int version, boolean clientSigned, boolean freelancerSigned,
                      java.time.LocalDateTime date, boolean hasRevision) {}
    private Row toRow(Project p, AgreementRevision a) {
        String state = p.getStatus().name();
        boolean signedC=a!=null && a.getCustomerSignedAt()!=null;
        boolean signedF=a!=null && a.getFreelancerSignedAt()!=null;
        String stateLabel = switch(state) {
            case "COMPLETED" -> "Hoàn thành"; case "CANCELLED" -> "Đã hủy";
            case "DISPUTED" -> "Tranh chấp"; case "IN_PROGRESS" -> "Đang thực hiện";
            default -> signedC&&signedF ? "Đã ký · chờ thanh toán" : "Chờ ký";
        };
        return new Row(p.getId(),p.getApplication().getJob().getTitle(),
            p.getCustomer().getCompanyName()!=null&&!p.getCustomer().getCompanyName().isBlank()?p.getCustomer().getCompanyName():p.getCustomer().getFullName(),
            p.getFreelancer().getFullName(),a!=null?a.getAmount():p.getTotalPrice(),state,stateLabel,
            a!=null?a.getVersion():0,signedC,signedF,p.getCreatedAt(),a!=null);
    }
    @GetMapping
    @Transactional(readOnly=true)
    public String list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String status,
                       @RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="1") int page,
                       @RequestParam(required=false) Long selected, @RequestParam(required=false) Integer version,Model model,Authentication auth) {
        if(auth==null) return "redirect:/login";
        List<Project> allProjects=projects.findAll();
        List<Row> all=new ArrayList<>();
        Map<Long,List<AgreementRevision>> byProject=new HashMap<>();
        for(Project project:allProjects) {
            List<AgreementRevision> history=revisions.findByProjectOrderByVersionDesc(project);
            byProject.put(project.getId(),history);
            all.add(toRow(project,history.isEmpty()?null:history.get(0)));
        }
        long signed=all.stream().filter(r->r.clientSigned()&&r.freelancerSigned()).count();
        model.addAttribute("countAll",all.size()); model.addAttribute("countActive",all.stream().filter(r->"IN_PROGRESS".equals(r.state())).count());
        model.addAttribute("countCompleted",all.stream().filter(r->"COMPLETED".equals(r.state())).count());
        model.addAttribute("countDisputed",all.stream().filter(r->"DISPUTED".equals(r.state())).count());
        model.addAttribute("countUnsigned",all.size()-signed);
        String needle=q.strip().toLowerCase(Locale.ROOT);
        List<Row> filtered=all.stream().filter(r->needle.isEmpty()||(String.valueOf(r.id()).contains(needle)||r.title().toLowerCase(Locale.ROOT).contains(needle)||r.client().toLowerCase(Locale.ROOT).contains(needle)||r.freelancer().toLowerCase(Locale.ROOT).contains(needle)))
            .filter(r->switch(status){case "signed"->r.clientSigned()&&r.freelancerSigned();case "unsigned"->!r.clientSigned()||!r.freelancerSigned();case "active"->"IN_PROGRESS".equals(r.state());case "completed"->"COMPLETED".equals(r.state());case "disputed"->"DISPUTED".equals(r.state());case "cancelled"->"CANCELLED".equals(r.state());default->true;})
            .sorted("oldest".equals(sort)?Comparator.comparing(Row::date,Comparator.nullsLast(Comparator.naturalOrder())):Comparator.comparing(Row::date,Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
        int pages=Math.max(1,(filtered.size()+4)/5); page=Math.max(1,Math.min(page,pages));
        List<Row> rows=filtered.subList((page-1)*5,Math.min(page*5,filtered.size()));
        model.addAttribute("rows",rows);model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("sort",sort);model.addAttribute("page",page);model.addAttribute("pages",pages);model.addAttribute("total",filtered.size());
        Long chosen=selected!=null&&byProject.containsKey(selected)?selected:(!rows.isEmpty()?rows.get(0).id():null);
        if(chosen!=null) {
            Project project=allProjects.stream().filter(a->a.getId().equals(chosen)).findFirst().orElse(null);
            List<AgreementRevision> history=byProject.getOrDefault(chosen,List.of());
            AgreementRevision selectedRevision=history.stream().filter(a->version!=null&&a.getVersion()==version).findFirst().orElse(history.isEmpty()?null:history.get(0));
            model.addAttribute("selectedProject",project);
            model.addAttribute("selectedRow",toRow(project,selectedRevision));
            model.addAttribute("selectedRevision",selectedRevision);
            model.addAttribute("history",history);
            model.addAttribute("relatedPayments",payments.findAll().stream().filter(pay->pay.getProject()!=null&&pay.getProject().getId().equals(chosen)).toList());
        }
        return "admin/contracts";
    }
}
