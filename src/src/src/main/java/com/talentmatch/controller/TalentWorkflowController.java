package com.talentmatch.controller;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Controller
public class TalentWorkflowController {
 private final UserService users;private final UserRepository userRepo;private final JobService jobService;
 private final JobRepository jobs;private final ProjectService projectService;
 private final JobInvitationRepository invites;private final JobQuestionRepository questions;
 private final TalentReviewRepository reviews;private final TalentReportRepository reports;
 public TalentWorkflowController(UserService users,UserRepository userRepo,JobService jobService,JobRepository jobs,
 ProjectService projectService,JobInvitationRepository invites,JobQuestionRepository questions,TalentReviewRepository reviews,TalentReportRepository reports){
 this.users=users;this.userRepo=userRepo;this.jobService=jobService;this.jobs=jobs;this.projectService=projectService;
 this.invites=invites;this.questions=questions;this.reviews=reviews;this.reports=reports;}
 private User me(Authentication auth){return users.byEmail(auth.getName());}
 private void customer(User u){if(u.getRole()!=Role.CUSTOMER)throw new IllegalArgumentException("Chỉ tài khoản Client được thực hiện.");}
 private Job ownedJob(Long id,User u){Job j=jobs.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy công việc."));
 if(!j.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Bạn không sở hữu công việc này.");return j;}
 private static String norm(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT);}
 @GetMapping("/customer/talent")
 public String talent(Authentication a,Model m,@RequestParam(required=false)String q,@RequestParam(required=false)String field,
 @RequestParam(required=false)Integer minExperience,@RequestParam(required=false)Long jobId){
 User u=me(a);customer(u);List<Job> mine=jobs.findByCustomerOrderByCreatedAtDesc(u);
 Job selected=jobId==null?null:ownedJob(jobId,u);
 List<Map<String,Object>> options=new ArrayList<>();
 for(User f:userRepo.findByRoleAndLockedFalseOrderByCreatedAtDesc(Role.FREELANCER)){
  if(!Boolean.TRUE.equals(f.getEnabled()))continue;
  if(!norm(q).isEmpty()&&!norm(f.getFullName()+" "+f.getSkills()+" "+f.getProfessionalTitle()).contains(norm(q)))continue;
  if(!norm(field).isEmpty()&&!norm(f.getField()).contains(norm(field)))continue;
  if(minExperience!=null&&(f.getExperienceYears()==null||f.getExperienceYears()<minExperience))continue;
  int score=0;List<String> why=new ArrayList<>();
  if(selected!=null){
   Set<String> target=Arrays.stream(Optional.ofNullable(selected.getRequiredSkills()).orElse("").split(","))
     .map(TalentWorkflowController::norm).filter(v->!v.isBlank()).collect(Collectors.toSet());
   String skills=norm(f.getSkills());int matches=0;
   for(String skill:target){if(skills.contains(skill))matches++;}
   if(matches>0){score+=Math.min(55,(int)Math.round(55.0*matches/Math.max(1,target.size())));why.add("Khớp "+matches+"/"+target.size()+" kỹ năng");}
   if(norm(selected.getCategory()).equals(norm(f.getField()))){score+=20;why.add("Đúng lĩnh vực");}
   int years=f.getExperienceYears()==null?0:f.getExperienceYears();
   if(years>=5){score+=10;why.add("Từ 5 năm kinh nghiệm");}
   else if(years>=2){score+=6;why.add("Kinh nghiệm phù hợp");}
   // Approximate cost: hourly rate * 4 billable hours/day * project estimated duration.
   // 4 hours/day is a DEMO scoring assumption, not a contractual quotation.
   if(f.getHourlyRate()!=null && selected.getBudget()!=null){
     java.math.BigDecimal estimate=f.getHourlyRate().multiply(java.math.BigDecimal.valueOf(4L * Math.max(1,Optional.ofNullable(selected.getEstimatedDurationDays()).orElse(14))));
     if(estimate.compareTo(selected.getBudget())<=0){score+=10;why.add("Chi phí ước tính trong ngân sách (giả định 4 giờ/ngày)");}
   }
   if(Boolean.TRUE.equals(f.getAvailable())){score+=5;why.add("Sẵn sàng nhận việc");}
  }else{score=(Boolean.TRUE.equals(f.getAvailable())?20:0)+(int)Math.min(40,Math.max(0,Optional.ofNullable(f.getExperienceYears()).orElse(0))*8);why.add("Chọn công việc để có điểm phù hợp cụ thể");}
  Map<String,Object> row=new HashMap<>();row.put("user",f);row.put("score",score);row.put("reason",String.join(" · ",why));options.add(row);
 }
 options.sort((x,y)->Integer.compare((int)y.get("score"),(int)x.get("score")));
 m.addAttribute("suggestions",options);m.addAttribute("jobs",mine);m.addAttribute("selected",selected);
 m.addAttribute("q",q);m.addAttribute("field",field);m.addAttribute("minExperience",minExperience);return "customer/talent";
 }
 @PostMapping("/customer/invitations") @Transactional
 public String invite(Authentication a,@RequestParam Long jobId,@RequestParam Long freelancerId,@RequestParam(required=false)String message,RedirectAttributes ra){
 try{User u=me(a);customer(u);Job job=ownedJob(jobId,u);
  if(job.getStatus()!=JobStatus.OPEN)throw new IllegalArgumentException("Chỉ được mời vào công việc đang tuyển.");
  User f=userRepo.findById(freelancerId).orElseThrow();if(f.getRole()!=Role.FREELANCER||Boolean.TRUE.equals(f.getLocked()))throw new IllegalArgumentException("Freelancer không khả dụng.");
  JobInvitation i=invites.findByJobAndFreelancer(job,f).orElseGet(JobInvitation::new);
  if(i.getId()!=null && "ACCEPTED".equals(i.getStatus()))throw new IllegalArgumentException("Freelancer đã đồng ý lời mời.");
  i.setJob(job);i.setCustomer(u);i.setFreelancer(f);i.setStatus("SENT");i.setMessage(message==null?"Mời bạn xem công việc và gửi Proposal.":message.trim());invites.save(i);
  ra.addFlashAttribute("success","Đã gửi lời mời đến "+f.getFullName());
 }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/invitations";}
 @GetMapping("/customer/invitations") public String myInvites(Authentication a,Model model){User u=me(a);customer(u);model.addAttribute("invitations",invites.findByCustomerOrderByCreatedAtDesc(u));return "customer/invitations";}
 @GetMapping("/freelancer/invitations")
 public String freelancerInvites(Authentication a, Model m,
      @RequestParam(defaultValue="") String q,
      @RequestParam(defaultValue="all") String status,
      @RequestParam(defaultValue="latest") String sort,
      @RequestParam(defaultValue="") String budget,
      @RequestParam(required=false) java.math.BigDecimal minBudget,
      @RequestParam(required=false) java.math.BigDecimal maxBudget,
      @RequestParam(defaultValue="") String workType,
      @RequestParam(defaultValue="") String category,
      @RequestParam(defaultValue="") String skill,
      @RequestParam(defaultValue="1") int page) {
  User u=me(a);
  if(u.getRole()!=Role.FREELANCER) throw new IllegalArgumentException("Chỉ Freelancer được xem lời mời.");
  List<JobInvitation> all=invites.findByFreelancerOrderByCreatedAtDesc(u);
  Map<String,Long> counts=new HashMap<>();
  counts.put("all",(long)all.size());
  counts.put("SENT",all.stream().filter(i->"SENT".equals(i.getStatus())).count());
  counts.put("ACCEPTED",all.stream().filter(i->"ACCEPTED".equals(i.getStatus())).count());
  counts.put("DECLINED",all.stream().filter(i->"DECLINED".equals(i.getStatus())).count());
  List<String> categories=all.stream().map(i->i.getJob().getCategory()).filter(Objects::nonNull).distinct().sorted().toList();
  List<JobInvitation> filtered=all.stream().filter(i->{
   Job j=i.getJob();String searchable=norm(j.getTitle()+" "+j.getDescription()+" "+j.getRequiredSkills()+" "+i.getMessage()+" "+i.getCustomer().getFullName()+" "+i.getCustomer().getCompanyName());
   if(!norm(q).isEmpty()&&!searchable.contains(norm(q)))return false;
   if(!"all".equalsIgnoreCase(status)&&!status.equalsIgnoreCase(i.getStatus()))return false;
   if(!norm(category).isEmpty()&&!norm(category).equals(norm(j.getCategory())))return false;
   if(!norm(skill).isEmpty()&&!norm(j.getRequiredSkills()).contains(norm(skill)))return false;
   if(!norm(workType).isEmpty()&&!norm(workType).equals(norm(String.valueOf(j.getWorkType()))))return false;
   java.math.BigDecimal amount=j.getBudget();
   if(amount==null && (!budget.isBlank()||minBudget!=null||maxBudget!=null))return false;
   if(amount!=null){
    if(minBudget!=null&&amount.compareTo(minBudget)<0)return false;
    if(maxBudget!=null&&amount.compareTo(maxBudget)>0)return false;
    java.math.BigDecimal m5=new java.math.BigDecimal("5000000");
    java.math.BigDecimal m15=new java.math.BigDecimal("15000000");
    java.math.BigDecimal m30=new java.math.BigDecimal("30000000");
    if("under5".equals(budget)&&amount.compareTo(m5)>=0)return false;
    if("5to15".equals(budget)&&(amount.compareTo(m5)<0||amount.compareTo(m15)>0))return false;
    if("15to30".equals(budget)&&(amount.compareTo(m15)<=0||amount.compareTo(m30)>0))return false;
    if("over30".equals(budget)&&amount.compareTo(m30)<=0)return false;
   }
   return true;
  }).collect(Collectors.toCollection(ArrayList::new));
  if("budget_high".equals(sort))filtered.sort(Comparator.comparing((JobInvitation i)->i.getJob().getBudget(),Comparator.nullsLast(Comparator.reverseOrder())));
  else if("budget_low".equals(sort))filtered.sort(Comparator.comparing((JobInvitation i)->i.getJob().getBudget(),Comparator.nullsLast(Comparator.naturalOrder())));
  else if("oldest".equals(sort))filtered.sort(Comparator.comparing(JobInvitation::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())));
  else filtered.sort(Comparator.comparing(JobInvitation::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder())));
  int total=filtered.size(),pageSize=5,totalPages=Math.max(1,(total+pageSize-1)/pageSize);
  int currentPage=Math.max(1,Math.min(page,totalPages));int start=(currentPage-1)*pageSize;
  m.addAttribute("invitations",filtered.subList(start,Math.min(start+pageSize,total)));
  m.addAttribute("totalInvitations",total);m.addAttribute("currentPage",currentPage);m.addAttribute("totalPages",totalPages);
  m.addAttribute("counts",counts);m.addAttribute("categories",categories);
  m.addAttribute("q",q);m.addAttribute("status",status);m.addAttribute("sort",sort);m.addAttribute("budget",budget);
  m.addAttribute("minBudget",minBudget);m.addAttribute("maxBudget",maxBudget);m.addAttribute("workType",workType);
  m.addAttribute("category",category);m.addAttribute("skill",skill);
  return "freelancer/invitations";
 }
 @PostMapping("/freelancer/invitations/{id}/answer") @Transactional
 public String answer(@PathVariable Long id,@RequestParam String status,Authentication a,RedirectAttributes ra){
 try{User u=me(a);JobInvitation i=invites.findById(id).orElseThrow();if(!i.getFreelancer().getId().equals(u.getId()))throw new IllegalArgumentException("Bạn không nhận được lời mời này.");
 if(!"SENT".equals(i.getStatus()))throw new IllegalArgumentException("Lời mời đã được xử lý.");
 if(!Set.of("ACCEPTED","DECLINED").contains(status))throw new IllegalArgumentException("Trạng thái không hợp lệ.");
 i.setStatus(status);invites.save(i);ra.addFlashAttribute("success","Đã phản hồi lời mời. Nếu đồng ý, hãy gửi Proposal tại trang công việc.");
 }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/freelancer/invitations";}
 @GetMapping("/jobs/{id}/discussion") public String discussion(@PathVariable Long id,Authentication a,Model m){Job job=jobService.get(id);
 m.addAttribute("job",job);m.addAttribute("questions",questions.findByJobOrderByCreatedAtDesc(job));return "jobs/discussion";}
 @PostMapping("/jobs/{id}/discussion") @Transactional public String question(@PathVariable Long id,@RequestParam String text,Authentication a,RedirectAttributes ra){
 try{User u=me(a);Job job=jobService.get(id);
  if(u.getRole()!=Role.FREELANCER||job.getStatus()!=JobStatus.OPEN)throw new IllegalArgumentException("Chỉ Freelancer được hỏi về công việc đang tuyển.");
  if(text==null||text.trim().length()<8)throw new IllegalArgumentException("Câu hỏi cần ít nhất 8 ký tự.");
  JobQuestion q=new JobQuestion();q.setJob(job);q.setAuthor(u);q.setQuestion(text.trim());questions.save(q);ra.addFlashAttribute("success","Đã gửi câu hỏi.");
 }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/jobs/"+id+"/discussion";}
 @PostMapping("/customer/questions/{id}/answer") @Transactional public String answerQuestion(@PathVariable Long id,@RequestParam String text,Authentication a,RedirectAttributes ra){
 JobQuestion q=questions.findById(id).orElseThrow();Long jobId=q.getJob().getId();
 try{ownedJob(jobId,me(a));if(text==null||text.trim().length()<5)throw new IllegalArgumentException("Câu trả lời cần ít nhất 5 ký tự.");
 q.setAnswer(text.trim());q.setAnsweredAt(LocalDateTime.now());questions.save(q);ra.addFlashAttribute("success","Đã trả lời công khai.");
 }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/jobs/"+jobId+"/discussion";}
 @PostMapping("/projects/{id}/reviews") @Transactional public String review(@PathVariable Long id,@RequestParam Integer rating,@RequestParam String comment,Authentication a,RedirectAttributes ra){
 try{Project p=projectService.get(id);User u=me(a);projectService.ensureParticipant(p,u);
 if(u.getRole()==Role.ADMIN)throw new IllegalArgumentException("Admin không thể đánh giá thay đối tác.");
 if(p.getStatus()!=ProjectStatus.COMPLETED)throw new IllegalArgumentException("Chỉ đánh giá sau khi dự án hoàn thành.");
 if(reviews.existsByProjectAndAuthor(p,u))throw new IllegalArgumentException("Bạn đã đánh giá dự án này.");
 if(rating<1||rating>5||comment==null||comment.trim().length()<10)throw new IllegalArgumentException("Đánh giá 1–5 sao và tối thiểu 10 ký tự.");
 User target=p.getCustomer().getId().equals(u.getId())?p.getFreelancer():p.getCustomer();
 TalentReview rev=new TalentReview();rev.setProject(p);rev.setAuthor(u);rev.setTarget(target);rev.setRating(rating);rev.setComment(comment.trim());reviews.save(rev);
 List<TalentReview> all=reviews.findByTarget(target);target.setRating(all.stream().mapToInt(TalentReview::getRating).average().orElse(5));userRepo.save(target);
 ra.addFlashAttribute("success","Đã lưu đánh giá và cập nhật điểm trung bình.");
 }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/projects/"+id;}
 @PostMapping("/projects/{id}/reports") @Transactional public String report(@PathVariable Long id,@RequestParam String reason,@RequestParam String description,Authentication a,RedirectAttributes ra){
 try{Project p=projectService.get(id);User u=me(a);projectService.ensureParticipant(p,u);
 if(u.getRole()==Role.ADMIN)throw new IllegalArgumentException("Admin không thể báo cáo thay đối tác.");
 if(reason==null||reason.isBlank()||description==null||description.trim().length()<15)throw new IllegalArgumentException("Vui lòng cung cấp lý do và mô tả tối thiểu 15 ký tự.");
 TalentReport tr=new TalentReport();tr.setProject(p);tr.setReporter(u);
 tr.setReportedUser(p.getCustomer().getId().equals(u.getId())?p.getFreelancer():p.getCustomer());
 tr.setReason(reason);tr.setDescription(description.trim());reports.save(tr);ra.addFlashAttribute("success","Báo cáo đã chuyển đến Admin.");
 }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/projects/"+id;}
}
