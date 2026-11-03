package com.talentmatch.controller;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

/** Admin oversight only: never updates acceptance or payment statuses. */
@Controller
@RequestMapping("/admin/progress")
public class AdminProgressController {
 private final ProjectRepository projects;
 private final MilestoneRepository milestones;
 private final ProjectDeliveryRepository deliveries;
 public AdminProgressController(ProjectRepository p,MilestoneRepository m,ProjectDeliveryRepository d){projects=p;milestones=m;deliveries=d;}
 public record ProjectRow(Long id,String title,String client,String freelancer,String state,int reported,int accepted,int waiting, LocalDate deadline){}
 public record MilestoneRow(Long id,String title,String status,int percent,LocalDate deadline,BigDecimal amount,String revision){}
 public record DeliveryRow(Long id,String milestone,String fileName,String url,int version,LocalDateTime submittedAt,String note){}
 private int percent(BigDecimal numerator,BigDecimal denominator){return denominator.signum()==0?0:Math.min(100,Math.max(0,numerator.multiply(BigDecimal.valueOf(100)).divide(denominator,0,RoundingMode.HALF_UP).intValue()));}
 @GetMapping @Transactional(readOnly=true)
 public String index(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String status,@RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="1") int page,@RequestParam(required=false) Long selected,@RequestParam(defaultValue="progress") String tab,Model model){
  List<Project> all=projects.findAll();Map<Long,List<Milestone>> byProject=new HashMap<>();List<ProjectRow> records=new ArrayList<>();LocalDate today=LocalDate.now();
  for(Project project:all){List<Milestone> ms=milestones.findByProjectOrderByCreatedAtAsc(project);byProject.put(project.getId(),ms);
   BigDecimal total=BigDecimal.ZERO,work=BigDecimal.ZERO,accepted=BigDecimal.ZERO;int waiting=0;LocalDate deadline=null;
   for(Milestone m:ms){BigDecimal value=m.getAmount()==null?BigDecimal.ZERO:m.getAmount().max(BigDecimal.ZERO);total=total.add(value);work=work.add(value.multiply(BigDecimal.valueOf(Math.max(0,Math.min(100,m.getProgressPercent())))).divide(BigDecimal.valueOf(100),6,RoundingMode.HALF_UP));if(m.getStatus()==MilestoneStatus.COMPLETED)accepted=accepted.add(value);if(m.getStatus()==MilestoneStatus.SUBMITTED)waiting++;if(m.getDeadline()!=null&&(deadline==null||m.getDeadline().isAfter(deadline)))deadline=m.getDeadline();}
   String client=project.getCustomer()==null?"—":project.getCustomer().getCompanyName();if(client==null||client.isBlank())client=project.getCustomer()==null?"—":project.getCustomer().getFullName();
   records.add(new ProjectRow(project.getId(),project.getApplication().getJob().getTitle(),client,project.getFreelancer().getFullName(),project.getStatus().name(),percent(work,total),percent(accepted,total),waiting,deadline));
  }
  model.addAttribute("totalProjects",records.size());model.addAttribute("activeProjects",records.stream().filter(r->r.state().equals("IN_PROGRESS")||r.state().equals("WAITING_ACCEPTANCE")).count());model.addAttribute("pendingProjects",records.stream().filter(r->r.waiting()>0).count());model.addAttribute("completedProjects",records.stream().filter(r->r.state().equals("COMPLETED")).count());model.addAttribute("issueProjects",records.stream().filter(r->r.state().equals("DISPUTED")||(r.deadline()!=null&&r.deadline().isBefore(today)&&r.accepted()<100)).count());
  String needle=q.strip().toLowerCase(Locale.ROOT);
  List<ProjectRow> filtered=records.stream().filter(r->needle.isEmpty()||r.title().toLowerCase(Locale.ROOT).contains(needle)||r.client().toLowerCase(Locale.ROOT).contains(needle)||r.freelancer().toLowerCase(Locale.ROOT).contains(needle)||String.valueOf(r.id()).contains(needle))
   .filter(r->switch(status){case "active"->r.state().equals("IN_PROGRESS")||r.state().equals("WAITING_ACCEPTANCE");case "pending"->r.waiting()>0;case "completed"->r.state().equals("COMPLETED");case "disputed"->r.state().equals("DISPUTED");case "overdue"->r.deadline()!=null&&r.deadline().isBefore(today)&&r.accepted()<100;default->true;}).sorted("oldest".equals(sort)?Comparator.comparing(ProjectRow::id):Comparator.comparing(ProjectRow::id).reversed()).toList();
  int pages=Math.max(1,(filtered.size()+4)/5);page=Math.max(1,Math.min(pages,page));List<ProjectRow> rows=filtered.subList((page-1)*5,Math.min(filtered.size(),page*5));
  ProjectRow chosen=records.stream().filter(r->Objects.equals(r.id(),selected)).findFirst().orElse(rows.isEmpty()?null:rows.get(0));
  model.addAttribute("rows",rows);model.addAttribute("selectedRow",chosen);model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("sort",sort);model.addAttribute("page",page);model.addAttribute("pages",pages);model.addAttribute("results",filtered.size());model.addAttribute("tab",tab);model.addAttribute("active","progress");model.addAttribute("pageTitle","Tiến độ & Nghiệm thu");
  if(chosen!=null){Project project=all.stream().filter(a->a.getId().equals(chosen.id())).findFirst().orElseThrow();List<Milestone> ms=byProject.get(chosen.id());model.addAttribute("chosenMilestones",ms.stream().map(m->new MilestoneRow(m.getId(),m.getTitle(),m.getStatus().name(),m.getProgressPercent(),m.getDeadline(),m.getAmount(),m.getRevisionNote())).toList());model.addAttribute("deliveries",deliveries.findByProjectOrderBySubmittedAtDesc(project).stream().map(d->new DeliveryRow(d.getId(),d.getMilestone()==null?"Toàn dự án":d.getMilestone().getTitle(),d.getFileName(),d.getExternalUrl(),d.getVersion(),d.getSubmittedAt(),d.getNote())).toList());}
  return "admin/progress";
 }
}
