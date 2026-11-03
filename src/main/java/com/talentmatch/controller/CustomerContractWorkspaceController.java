package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.ProjectRepository;
import com.talentmatch.service.AgreementService;
import com.talentmatch.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

/** Client-side contract overview. All records are scoped to the authenticated customer. */
@Controller
@RequestMapping("/customer/contracts")
public class CustomerContractWorkspaceController {
    private final ProjectRepository projects;
    private final AgreementService agreements;
    private final UserService users;
    public CustomerContractWorkspaceController(ProjectRepository projects, AgreementService agreements, UserService users) {
        this.projects=projects; this.agreements=agreements; this.users=users;
    }
    @GetMapping
    @Transactional(readOnly=true)
    public String index(Authentication authentication, Model model,
         @RequestParam(defaultValue="") String q,
         @RequestParam(defaultValue="all") String status,
         @RequestParam(defaultValue="newest") String sort,
         @RequestParam(required=false) Long selected,
         @RequestParam(defaultValue="0") int page) {
        User customer=users.byEmail(authentication.getName());
        List<Project> own=projects.findByCustomerOrderByCreatedAtDesc(customer);
        Map<Long,AgreementRevision> revisions=new LinkedHashMap<>();
        Map<Long,Boolean> pending=new HashMap<>();
        for(Project p:own) {
            revisions.put(p.getId(),agreements.current(p));
            pending.put(p.getId(),agreements.requests(p).stream().anyMatch(r->"PENDING".equals(r.getStatus())));
        }
        List<Project> contracts=own.stream().filter(p->revisions.get(p.getId())!=null).collect(Collectors.toList());
        int total=contracts.size();
        int missingDrafts=own.size()-contracts.size();
        long waiting=contracts.stream().filter(p->isAwaiting(p,revisions.get(p.getId()))).count();
        long active=contracts.stream().filter(p->agreements.signedCurrent(p)&&p.getStatus()!=ProjectStatus.COMPLETED&&p.getStatus()!=ProjectStatus.CANCELLED&&p.getStatus()!=ProjectStatus.DISPUTED).count();
        long completed=contracts.stream().filter(p->p.getStatus()==ProjectStatus.COMPLETED).count();
        String query=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        if(!Set.of("all","waiting","active","completed","cancelled").contains(status))status="all";
        final String selectedStatus=status;
        List<Project> filtered=contracts.stream().filter(p->{
            if(!query.isEmpty()){
                String name=p.getApplication().getJob().getTitle();
                String freelancer=p.getFreelancer().getFullName();
                if(!(name!=null&&name.toLowerCase(Locale.ROOT).contains(query))&&
                   !(freelancer!=null&&freelancer.toLowerCase(Locale.ROOT).contains(query))&&
                   !("HD-"+p.getId()).toLowerCase(Locale.ROOT).contains(query))return false;
            }
            return switch(selectedStatus){
                case "waiting" -> isAwaiting(p,revisions.get(p.getId()));
                case "active" -> agreements.signedCurrent(p)&&p.getStatus()!=ProjectStatus.COMPLETED&&p.getStatus()!=ProjectStatus.CANCELLED&&p.getStatus()!=ProjectStatus.DISPUTED;
                case "completed" -> p.getStatus()==ProjectStatus.COMPLETED;
                case "cancelled" -> p.getStatus()==ProjectStatus.CANCELLED;
                default -> true;
            };
        }).collect(Collectors.toCollection(ArrayList::new));
        if("oldest".equals(sort))Collections.reverse(filtered);
        else if("priceHigh".equals(sort))filtered.sort((a,b)->b.getTotalPrice().compareTo(a.getTotalPrice()));
        else if("priceLow".equals(sort))filtered.sort(Comparator.comparing(Project::getTotalPrice));
        else sort="newest";
        int size=5; int totalPages=Math.max(1,(filtered.size()+size-1)/size);
        page=Math.max(0,Math.min(page,totalPages-1));
        List<Project> visible=filtered.subList(page*size,Math.min(filtered.size(),(page+1)*size));
        Project chosen=null;
        if(selected!=null) chosen=contracts.stream().filter(p->p.getId().equals(selected)).findFirst().orElse(null);
        if(chosen==null&&!visible.isEmpty())chosen=visible.get(0);
        AgreementRevision agreement=chosen==null?null:revisions.get(chosen.getId());
        boolean hasPending=chosen!=null&&pending.getOrDefault(chosen.getId(),false);
        model.addAttribute("currentUser",customer);
        model.addAttribute("active","contracts");
        model.addAttribute("pageTitle","Hợp đồng & Điều khoản");
        model.addAttribute("contractTotal",total);
        model.addAttribute("missingDrafts",missingDrafts);
        model.addAttribute("waitingCount",waiting);
        model.addAttribute("activeCount",active);
        model.addAttribute("completedCount",completed);
        model.addAttribute("contracts",visible);
        model.addAttribute("allCount",filtered.size());
        model.addAttribute("revisions",revisions);
        model.addAttribute("selectedProject",chosen);
        model.addAttribute("selectedAgreement",agreement);
        model.addAttribute("pendingChange",hasPending);
        model.addAttribute("agreementHistory",chosen==null?List.of():agreements.history(chosen));
        model.addAttribute("changeRequests",chosen==null?List.of():agreements.requests(chosen));
        model.addAttribute("q",q==null?"":q);
        model.addAttribute("status",status);
        model.addAttribute("sort",sort);
        model.addAttribute("page",page);
        model.addAttribute("totalPages",totalPages);
        return "customer/contracts";
    }
    private boolean isAwaiting(Project p,AgreementRevision a){
        return a!=null&&p.getStatus()!=ProjectStatus.CANCELLED&&p.getStatus()!=ProjectStatus.COMPLETED
                &&(a.getCustomerSignedAt()==null||a.getFreelancerSignedAt()==null);
    }
}
