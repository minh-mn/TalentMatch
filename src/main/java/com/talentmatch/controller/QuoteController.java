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
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class QuoteController {
 private final QuoteService quotes;
 private final JobApplicationRepository applications;
 private final JobRepository jobs;
 private final UserService users;
 private final ProjectRepository projects;
 public QuoteController(QuoteService quotes,JobApplicationRepository applications,JobRepository jobs,UserService users,ProjectRepository projects){
     this.quotes=quotes;this.applications=applications;this.jobs=jobs;this.users=users;this.projects=projects;
 }
 @GetMapping({"/freelancer/quotes","/freelancer/quotations"})
 public String list(Authentication auth,Model model,@RequestParam(defaultValue="")String q,
      @RequestParam(defaultValue="all")String status,@RequestParam(defaultValue="newest")String sort,
      @RequestParam(defaultValue="1")int page,@RequestParam(required=false)Long selected){
      User user=users.byEmail(auth.getName());
      requireRole(user);
      List<JobApplication> own=owned(user);
      // Khong ghi du lieu khi mo trang GET: bao gia khoi tao o luong ung tuyen / khi thao tac.
      Map<Long,QuoteRevision> latest=new LinkedHashMap<>();
      for(JobApplication a:own)quotes.latest(a).ifPresent(v->latest.put(a.getId(),v));
      String word=q==null?"":q.toLowerCase(Locale.ROOT).trim();
      List<JobApplication> filtered=own.stream().filter(a->word.isBlank()||
          (a.getJob().getTitle()+" "+a.getJob().getCategory()+" "+a.getFreelancer().getFullName()+" "+a.getJob().getCustomer().getFullName())
               .toLowerCase(Locale.ROOT).contains(word))
        .filter(a->{
            QuoteRevision revision=latest.get(a.getId());
            if("all".equals(status))return true;
            if(revision==null)return false;
            if("NEGOTIATING".equals(status))return revision.getStatus()==QuoteStatus.PENDING&&revision.getVersion()>1;
            if("WAITING".equals(status))return revision.getStatus()==QuoteStatus.PENDING&&revision.getVersion()==1;
            return revision.getStatus().name().equals(status);
        })
        .collect(Collectors.toCollection(ArrayList::new));
      filtered.sort(Comparator.comparing((JobApplication a)->latest.containsKey(a.getId())?latest.get(a.getId()).getCreatedAt():a.getUpdatedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder())));
      if(!"oldest".equals(sort))Collections.reverse(filtered);
      if("priceHigh".equals(sort))filtered.sort(Comparator.comparing((JobApplication a)->latest.containsKey(a.getId())?latest.get(a.getId()).getAmount():a.getProposedPrice(),Comparator.reverseOrder()));
      if("priceLow".equals(sort))filtered.sort(Comparator.comparing((JobApplication a)->latest.containsKey(a.getId())?latest.get(a.getId()).getAmount():a.getProposedPrice()));
      int total=filtered.size(),pages=Math.max(1,(total+4)/5),current=Math.min(pages,Math.max(1,page)),from=(current-1)*5;
      List<JobApplication> visible=filtered.subList(from,Math.min(from+5,total));
      // Chi mo chi tiet khi nguoi dung chu dong bam vao bao gia trong danh sach hien tai.
      Long active=selected;
      JobApplication activeApp=null;
      if(active!=null){
          for(JobApplication a:visible){if(a.getId().equals(active)){activeApp=a;break;}}
          if(activeApp==null)active=null;
      }
      model.addAttribute("viewer",user);model.addAttribute("isClient",user.getRole()==Role.CUSTOMER);
      model.addAttribute("applications",visible);model.addAttribute("latestById",latest);model.addAttribute("selectedId",active);
      model.addAttribute("negotiatingCount",latest.values().stream().filter(r->r.getStatus()==QuoteStatus.PENDING&&r.getVersion()>1).count());
      model.addAttribute("waitingCount",latest.values().stream().filter(r->r.getStatus()==QuoteStatus.PENDING&&r.getVersion()==1).count());
      model.addAttribute("currentPage",current);model.addAttribute("totalPages",pages);model.addAttribute("totalCount",total);
      model.addAttribute("allCount",own.size());model.addAttribute("searchTerm",q);model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("sort",sort);
      for(QuoteStatus state:QuoteStatus.values())model.addAttribute("count"+state.name(),latest.values().stream().filter(r->r.getStatus()==state).count());
      addDetail(model,user,activeApp);
      return "quotations/dashboard";
 }
 @GetMapping("/quotations/application/{applicationId}")
 public String detail(@PathVariable Long applicationId,Authentication auth,Model model){
     User user=users.byEmail(auth.getName());requireRole(user);
     JobApplication application=quotes.application(applicationId,user);
     return "redirect:"+(user.getRole()==Role.CUSTOMER?"/customer/quotations/jobs/"+application.getJob().getId()+"/applications/"+application.getId():"/freelancer/quotes?selected="+application.getId());
 }
 @PostMapping("/quotations/application/{id}/initialize")
 public String initialize(@PathVariable Long id,Authentication auth,RedirectAttributes flash){
     try{
         User user=users.byEmail(auth.getName());requireRole(user);
         quotes.initializeHistorical(id,user);
         flash.addFlashAttribute("success","Đã khởi tạo báo giá phiên bản v1 từ hồ sơ ứng tuyển.");
     }catch(RuntimeException e){flash.addFlashAttribute("error",e.getMessage());}
     return redirect(auth,id);
 }
 @PostMapping("/quotations/application/{id}/counter")
 public String counter(@PathVariable Long id,Authentication auth,
    @RequestParam BigDecimal amount,@RequestParam int estimatedDays,
    @RequestParam String scope,@RequestParam String revisionPolicy,RedirectAttributes flash){
    try{User user=users.byEmail(auth.getName());requireRole(user);
      quotes.counter(id,user,amount,estimatedDays,scope,revisionPolicy);
      flash.addFlashAttribute("success","Đã gửi đề nghị mới. Phiên bản cũ vẫn được lưu trong lịch sử.");
    }catch(RuntimeException e){flash.addFlashAttribute("error",e.getMessage());}
    return redirect(auth,id);
 }
 @PostMapping("/quotations/application/{id}/respond/{revisionId}")
 public String respond(@PathVariable Long id,@PathVariable Long revisionId,
      @RequestParam String action,@RequestParam(required=false)String reason,Authentication auth,RedirectAttributes flash){
      try{User user=users.byEmail(auth.getName());requireRole(user);
       if(!"accept".equals(action)&&!"reject".equals(action))throw new IllegalArgumentException("Thao tác không hợp lệ.");
       quotes.resolve(id,revisionId,user,"accept".equals(action),reason);
       flash.addFlashAttribute("success","accept".equals(action)?"Đã chốt báo giá. Client có thể tạo dự án và hợp đồng.":"Đã từ chối báo giá.");
      }catch(RuntimeException e){flash.addFlashAttribute("error",e.getMessage());}
      return redirect(auth,id);
 }
 private String redirect(Authentication auth,Long id){User u=users.byEmail(auth.getName());
      if(u.getRole()==Role.CUSTOMER){JobApplication a=quotes.application(id,u);return "redirect:/customer/quotations/jobs/"+a.getJob().getId()+"/applications/"+id;}return "redirect:/freelancer/quotes?selected="+id;}
 private void requireRole(User u){if(u.getRole()!=Role.CUSTOMER&&u.getRole()!=Role.FREELANCER)
      throw new IllegalArgumentException("Chỉ Client và Freelancer được sử dụng tính năng này.");}
 private List<JobApplication> owned(User user){
      if(user.getRole()==Role.FREELANCER)return applications.findByFreelancerOrderByCreatedAtDesc(user);
      List<JobApplication> all=new ArrayList<>();for(Job job:jobs.findByCustomerOrderByCreatedAtDesc(user))
           all.addAll(applications.findByJobOrderByCreatedAtDesc(job));return all;
 }
 private void addDetail(Model model,User viewer,JobApplication a){
      model.addAttribute("selectedApplication",a);
      if(a==null){model.addAttribute("history",List.of());return;}
      List<QuoteRevision> history=quotes.history(a,viewer);
      QuoteRevision latest=history.isEmpty()?null:history.get(0);
      model.addAttribute("history",history);model.addAttribute("selectedQuote",latest);
      boolean eligible=a.getStatus()!=ApplicationStatus.WITHDRAWN&&a.getStatus()!=ApplicationStatus.REJECTED
          &&(a.getJob().getStatus()==JobStatus.OPEN||a.getJob().getStatus()==JobStatus.PAUSED)
          &&projects.findByApplication(a).isEmpty();
      model.addAttribute("canInitialize",latest==null&&eligible&&a.getFreelancer().getId().equals(viewer.getId()));
      model.addAttribute("firstQuote",history.isEmpty()?null:history.get(history.size()-1));
      model.addAttribute("canRespond",latest!=null&&latest.getStatus()==QuoteStatus.PENDING&&quotes.isRecipient(latest,viewer));
      model.addAttribute("canCounter",latest!=null&&(latest.getStatus()==QuoteStatus.PENDING||latest.getStatus()==QuoteStatus.REJECTED)&&eligible);
      model.addAttribute("project",projects.findByApplication(a).orElse(null));
 }
}