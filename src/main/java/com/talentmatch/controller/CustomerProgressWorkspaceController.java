package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/customer/progress")
public class CustomerProgressWorkspaceController {
 private final ProjectRepository projects;
 private final MilestoneRepository milestones;
 private final ProjectDeliveryRepository deliveries;
 private final UserService users;
 @org.springframework.beans.factory.annotation.Autowired(required=false)
 private ProjectService workflow;
 public CustomerProgressWorkspaceController(ProjectRepository p,MilestoneRepository m,ProjectDeliveryRepository d,UserService u){projects=p;milestones=m;deliveries=d;users=u;}
 private Project owned(Long id,User client){Project p=projects.findById(id).orElseThrow(()->new IllegalArgumentException("Dự án không tồn tại"));if(!p.getCustomer().getId().equals(client.getId()))throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem dự án");return p;}
 @GetMapping @Transactional(readOnly=true)
 public String index(Authentication auth,Model model,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String status,@RequestParam(required=false) Long selected,@RequestParam(required=false) Long milestone,@RequestParam(defaultValue="progress") String tab,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="all") String type){
  User client=users.byEmail(auth.getName());List<Project> all=projects.findByCustomerOrderByCreatedAtDesc(client);
  Map<Long,List<Milestone>> byProject=new HashMap<>();Map<Long,Integer> reported=new HashMap<>(),accepted=new HashMap<>(),pending=new HashMap<>();
  for(Project pr:all){List<Milestone> ms=milestones.findByProjectOrderByCreatedAtAsc(pr);byProject.put(pr.getId(),ms);BigDecimal total=ms.stream().map(Milestone::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal done=BigDecimal.ZERO,ok=BigDecimal.ZERO;int wait=0;
   for(Milestone m:ms){BigDecimal w=m.getAmount()==null?BigDecimal.ZERO:m.getAmount();done=done.add(w.multiply(BigDecimal.valueOf(m.getProgressPercent())).divide(BigDecimal.valueOf(100),6,RoundingMode.HALF_UP));if(m.getStatus()==MilestoneStatus.COMPLETED)ok=ok.add(w);if(m.getStatus()==MilestoneStatus.SUBMITTED)wait++;}
   reported.put(pr.getId(),ProgressMetrics.reported(ms));accepted.put(pr.getId(),ProgressMetrics.accepted(ms));pending.put(pr.getId(),wait);
  }
  final String search=q.strip().toLowerCase(Locale.ROOT);List<Project> filtered=all.stream().filter(pr->search.isEmpty()||pr.getApplication().getJob().getTitle().toLowerCase(Locale.ROOT).contains(search)||("HD-"+pr.getId()).toLowerCase(Locale.ROOT).contains(search)).filter(pr->switch(status){case "pending"->pending.get(pr.getId())>0;case "active"->pr.getStatus()==ProjectStatus.IN_PROGRESS||pr.getStatus()==ProjectStatus.WAITING_ACCEPTANCE;case "completed"->pr.getStatus()==ProjectStatus.COMPLETED;
      case "revision"->byProject.get(pr.getId()).stream().anyMatch(m->m.getRevisionNote()!=null&&!m.getRevisionNote().isBlank()&&m.getStatus()==MilestoneStatus.IN_PROGRESS);
      case "unpaid"->pr.getStatus()==ProjectStatus.WAITING_PAYMENT;
      case "disputed"->pr.getStatus()==ProjectStatus.DISPUTED;
      default->true;}).filter(pr->"all".equals(type)||("milestone".equals(type) && !byProject.get(pr.getId()).isEmpty())||("full".equals(type) && byProject.get(pr.getId()).isEmpty())).collect(Collectors.toCollection(ArrayList::new));
  int totalPages=Math.max(1,(filtered.size()+4)/5);page=Math.max(0,Math.min(page,totalPages-1));List<Project> visible=filtered.subList(page*5,Math.min(filtered.size(),(page+1)*5));
  Project choice=null;if(selected!=null)choice=visible.stream().filter(pr->pr.getId().equals(selected)).findFirst().orElse(null);if(choice==null&&!visible.isEmpty())choice=visible.get(0);
  List<Milestone> chosen=choice==null?List.of():byProject.get(choice.getId());Milestone selectedMilestone=null;if(milestone!=null)selectedMilestone=chosen.stream().filter(m->m.getId().equals(milestone)).findFirst().orElse(null);if(selectedMilestone==null&&!chosen.isEmpty())selectedMilestone=chosen.get(0);
  List<ProjectDelivery> evidence=choice==null?List.of():deliveries.findByProjectOrderBySubmittedAtDesc(choice);Milestone pick=selectedMilestone;
  List<ProjectDelivery> current=evidence.stream().filter(d->pick!=null&&d.getMilestone()!=null&&d.getMilestone().getId().equals(pick.getId())).sorted(Comparator.comparing(ProjectDelivery::getVersion).reversed()).toList();
  model.addAttribute("currentUser",client);model.addAttribute("active","progress");model.addAttribute("pageTitle","Tiến độ & Nghiệm thu");model.addAttribute("projects",visible);model.addAttribute("selectedProject",choice);model.addAttribute("milestones",chosen);model.addAttribute("chosenMilestone",pick);model.addAttribute("deliveries",current);model.addAttribute("currentDelivery",current.isEmpty()?null:current.get(0));model.addAttribute("reported",reported);model.addAttribute("accepted",accepted);model.addAttribute("pending",pending);model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("type",type);model.addAttribute("tab",tab);model.addAttribute("page",page);model.addAttribute("totalPages",totalPages);model.addAttribute("totalProjects",all.size());model.addAttribute("pendingTotal",pending.values().stream().mapToInt(Integer::intValue).sum());model.addAttribute("activeTotal",all.stream().filter(pr->pr.getStatus()==ProjectStatus.IN_PROGRESS||pr.getStatus()==ProjectStatus.WAITING_ACCEPTANCE).count());return "customer/progress";
 }
 @PostMapping("/{projectId}/milestones/{milestoneId}/decide")
 @Transactional
 public String decide(@PathVariable Long projectId,@PathVariable Long milestoneId,@RequestParam Long deliveryId,@RequestParam String decision,@RequestParam(required=false) String reason,Authentication auth,RedirectAttributes flash){
  try{User customer=users.byEmail(auth.getName());Project project=owned(projectId,customer);Milestone m=milestones.findForUpdate(milestoneId).orElseThrow(()->new IllegalArgumentException("Không tồn tại milestone"));if(!m.getProject().getId().equals(projectId))throw new IllegalArgumentException("Milestone không thuộc dự án");if(project.getStatus()==ProjectStatus.CANCELLED||project.getStatus()==ProjectStatus.DISPUTED||project.getStatus()==ProjectStatus.COMPLETED)throw new IllegalArgumentException("Dự án hiện không cho phép nghiệm thu");if(m.getStatus()!=MilestoneStatus.SUBMITTED)throw new IllegalArgumentException("Milestone không có bản bàn giao đang chờ duyệt");
   List<ProjectDelivery> records=deliveries.findByProjectAndMilestoneOrderBySubmittedAtDesc(project,m);ProjectDelivery latest=records.stream().max(Comparator.comparing(ProjectDelivery::getVersion)).orElseThrow(()->new IllegalArgumentException("Chưa có bản bàn giao hợp lệ"));if(!latest.getId().equals(deliveryId))throw new IllegalArgumentException("Bản bàn giao đã cũ, vui lòng tải lại");
   if("accept".equals(decision) && (m.getProgressPercent()!=100 || m.getDeadline()==null))throw new IllegalArgumentException("Không thể nghiệm thu: mốc phải có deadline và đạt 100% tiến độ trước khi bàn giao.");
   if(workflow==null)throw new IllegalStateException("Chưa có dịch vụ nghiệm thu.");
   if("accept".equals(decision)){
     workflow.markMilestone(milestoneId,customer,MilestoneStatus.COMPLETED);
     flash.addFlashAttribute("success","Đã nghiệm thu mốc và giải ngân mô phỏng khoản tiền được giữ cho mốc này.");
   }else if("revise".equals(decision)){
     if(reason==null||reason.strip().length()<10||reason.length()>2000)
       throw new IllegalArgumentException("Nhập yêu cầu chỉnh sửa từ 10 đến 2000 ký tự");
     workflow.requestRevision(milestoneId,customer,reason);
     flash.addFlashAttribute("success","Đã yêu cầu Freelancer chỉnh sửa và bàn giao lại.");
   }else throw new IllegalArgumentException("Quyết định không hợp lệ");
  }catch(Exception ex){flash.addFlashAttribute("error",ex.getMessage());}
  return "redirect:/customer/progress?selected="+projectId+"&milestone="+milestoneId+"&tab=delivery";
 }
}
