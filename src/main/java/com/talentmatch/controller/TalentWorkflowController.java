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
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Controller
public class TalentWorkflowController {
 private final UserService users;private final UserRepository userRepo;private final JobService jobService;
 private final JobRepository jobs;private final ProjectService projectService;
 private final JobInvitationRepository invites;private final JobQuestionRepository questions;
 private final TalentReviewRepository reviews;private final TalentReportRepository reports;
 @Autowired private SavedFreelancerRepository savedFreelancers;
 @Autowired private MatchingService matchingService;
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
 public String talent(Authentication auth, Model model,
         @RequestParam(defaultValue = "") String q,
         @RequestParam(defaultValue = "") String field,
         @RequestParam(defaultValue = "") String skill,
         @RequestParam(required = false) Integer minExperience,
         @RequestParam(defaultValue = "") String workType,
         @RequestParam(required = false) java.math.BigDecimal rateFrom,
         @RequestParam(required = false) java.math.BigDecimal rateTo,
         @RequestParam(defaultValue = "match") String sort,
         @RequestParam(defaultValue = "false") boolean onlySaved,
         @RequestParam(defaultValue = "1") int page,
         @RequestParam(required = false) Long jobId) {
     User customerUser = me(auth);
     customer(customerUser);
     List<Job> myJobs = jobs.findByCustomerOrderByCreatedAtDesc(customerUser);
     List<Job> openJobs = myJobs.stream().filter(j -> j.getStatus() == JobStatus.OPEN && (j.getDeadline() == null || !j.getDeadline().isBefore(java.time.LocalDate.now()))).toList();
     Job selected = jobId == null ? (openJobs.isEmpty() ? null : openJobs.get(0)) : openJobs.stream().filter(job -> jobId.equals(job.getId())).findFirst().orElse(null);
     Map<Long,String> invitationStates = new HashMap<>();
     if (selected != null) invites.findByCustomerOrderByCreatedAtDesc(customerUser).stream()
             .filter(inv -> selected.getId().equals(inv.getJob().getId()))
             .forEach(inv -> invitationStates.put(inv.getFreelancer().getId(), inv.getStatus()));
     String keyword = norm(q);
     String wantedSkill = norm(skill);
     String wantedField = norm(field);
     String wantedWorkType = Set.of("REMOTE", "HYBRID", "ONSITE").contains(workType) ? workType : "";
     String order = Set.of("match", "rating", "rateAsc", "rateDesc", "experience").contains(sort) ? sort : "match";
     Set<Long> savedIds = savedFreelancers.findByCustomer(customerUser).stream()
             .map(x -> x.getFreelancer().getId()).collect(Collectors.toSet());
     List<Map<String, Object>> candidates = new ArrayList<>();
     List<User> availableProfiles = userRepo.findByRoleAndLockedFalseOrderByCreatedAtDesc(Role.FREELANCER)
             .stream().filter(f -> Boolean.TRUE.equals(f.getEnabled())).toList();
     Map<User, MatchingService.Recommendation> matches = selected == null
             ? Map.of() : matchingService.rank(selected, availableProfiles);
     for (User f : availableProfiles) {
         if (!keyword.isBlank() && !norm(f.getFullName()+" "+f.getProfessionalTitle()+" "+f.getSkills()+" "+f.getBio()).contains(keyword)) continue;
         if (!wantedSkill.isBlank() && !norm(f.getSkills()).contains(wantedSkill)) continue;
         if (!wantedField.isBlank() && !norm(f.getField()).contains(wantedField)) continue;
         if (minExperience != null && Optional.ofNullable(f.getExperienceYears()).orElse(0) < minExperience) continue;
         if (!wantedWorkType.isBlank() && !wantedWorkType.equals(f.getWorkPreference())) continue;
         if (rateFrom != null && (f.getHourlyRate() == null || f.getHourlyRate().compareTo(rateFrom) < 0)) continue;
         if (rateTo != null && (f.getHourlyRate() == null || f.getHourlyRate().compareTo(rateTo) > 0)) continue;
         if (onlySaved && !savedIds.contains(f.getId())) continue;
         MatchingService.Recommendation match = matches.get(f);
         int score = match == null ? 0 : match.score();
         Map<String, Object> row = new HashMap<>();
         row.put("user", f);
         row.put("score", score);
         row.put("reason", match == null ? "Chọn công việc đang tuyển để xem mức độ phù hợp" : match.reason());
         row.put("saved", savedIds.contains(f.getId()));
         row.put("invitationStatus", invitationStates.getOrDefault(f.getId(), "NONE"));
         if (match != null && selected != null) {
             row.put("factors", matchingFactors(selected, f, match));
             row.put("matchedSkills", match.matchedSkills());
             row.put("missingSkills", match.missingSkills());
             row.put("hasRequiredSkills", selected.getRequiredSkills() != null
                     && !selected.getRequiredSkills().isBlank());
         }
         candidates.add(row);
     }
     Comparator<Map<String, Object>> byRating = Comparator.comparingDouble(
             (Map<String, Object> r) -> Optional.ofNullable(((User) r.get("user")).getRating()).orElse(0.0));
     Comparator<Map<String, Object>> byRate = Comparator.comparing(
             (Map<String, Object> r) -> ((User) r.get("user")).getHourlyRate(),
             Comparator.nullsLast(Comparator.naturalOrder()));
     Comparator<Map<String, Object>> byExperience = Comparator.comparingInt(
             (Map<String, Object> r) -> Optional.ofNullable(((User) r.get("user")).getExperienceYears()).orElse(0));
     Comparator<Map<String, Object>> secondaryOrder = switch (order) {
         case "rating" -> byRating.reversed().thenComparing(byExperience.reversed());
         case "rateAsc" -> byRate;
         case "rateDesc" -> Comparator.comparing(
                 (Map<String, Object> r) -> ((User) r.get("user")).getHourlyRate(),
                 Comparator.nullsLast(Comparator.reverseOrder()));
         case "experience" -> byExperience.reversed();
         default -> byRating.reversed().thenComparing(byExperience.reversed());
     };
     Comparator<Map<String, Object>> byName = Comparator.comparing(
             (Map<String, Object> r) -> Optional.ofNullable(((User) r.get("user")).getFullName()).orElse(""),
             String.CASE_INSENSITIVE_ORDER);
     candidates.sort(Comparator
             .comparingInt((Map<String, Object> r) -> (int) r.get("score")).reversed()
             .thenComparing(secondaryOrder)
             .thenComparing(byName));
     int size = 10;
     int totalPages = Math.max(1, (candidates.size() + size - 1) / size);
     int currentPage = Math.max(1, Math.min(page, totalPages));
     int from = (currentPage - 1) * size;
     List<Map<String,Object>> visible = candidates.subList(from, Math.min(from + size, candidates.size()));
     long highMatchCount = candidates.stream()
             .filter(row -> (int) row.get("score") >= MatchingService.HIGH_MATCH_THRESHOLD)
             .count();
     List<Map<String,Object>> recommended = candidates.stream()
             .filter(row -> selected != null && (int)row.get("score") >= MatchingService.HIGH_MATCH_THRESHOLD && Boolean.TRUE.equals(((User)row.get("user")).getAvailable()))
             .sorted(Comparator.comparingInt((Map<String, Object> r) -> (int) r.get("score")).reversed()
                     .thenComparing(byRating.reversed())
                     .thenComparing(byExperience.reversed())
                     .thenComparing(r -> Optional.ofNullable(((User) r.get("user")).getFullName()).orElse(""),
                             String.CASE_INSENSITIVE_ORDER))
             .limit(3).toList();
     List<String> fields = availableProfiles.stream().map(User::getField)
             .filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList();
     model.addAttribute("suggestions", visible);
     model.addAttribute("recommended", recommended);
     model.addAttribute("jobs", openJobs);
     model.addAttribute("selected", selected);
     model.addAttribute("q", q.trim());
     model.addAttribute("field", field.trim());
     model.addAttribute("skill", skill.trim());
     model.addAttribute("minExperience", minExperience);
     model.addAttribute("workType", wantedWorkType);
     model.addAttribute("rateFrom", rateFrom);
     model.addAttribute("rateTo", rateTo);
     model.addAttribute("sort", order);
     model.addAttribute("onlySaved", onlySaved);
     model.addAttribute("fields", fields);
     model.addAttribute("totalCandidates", availableProfiles.size());
     model.addAttribute("filteredCount", candidates.size());
     model.addAttribute("highMatchCount", highMatchCount);
     model.addAttribute("highMatchThreshold", MatchingService.HIGH_MATCH_THRESHOLD);
     model.addAttribute("savedCount", savedIds.size());
     model.addAttribute("invitedCount", invites.findByCustomerOrderByCreatedAtDesc(customerUser).stream()
             .map(inv -> inv.getFreelancer().getId()).distinct().count());
     model.addAttribute("readyCount", availableProfiles.stream().filter(f -> Boolean.TRUE.equals(f.getAvailable())).count());
     model.addAttribute("currentPage", currentPage);
     model.addAttribute("totalPages", totalPages);
     int startPage = Math.max(1, currentPage - 2);
     int endPage = Math.min(totalPages, startPage + 4);
     startPage = Math.max(1, endPage - 4);
     model.addAttribute("pageNumbers", java.util.stream.IntStream.rangeClosed(startPage, endPage).boxed().toList());
     return "customer/talent";
 }

 private List<Map<String, Object>> matchingFactors(Job job, User freelancer,
                                                    MatchingService.Recommendation match) {
     String skillDetail = match.matchedSkills().isEmpty()
             ? "Chưa có kỹ năng yêu cầu nào khớp hoàn toàn."
             : "Khớp: " + String.join(", ", match.matchedSkills()) + ".";
     if (!match.missingSkills().isEmpty()) {
         skillDetail += " Chưa thấy trong hồ sơ: " + String.join(", ", match.missingSkills()) + ".";
     }
     String experienceDetail = match.requiredExperienceYears() == 0
             ? "Cấp độ kinh nghiệm của tin chưa được nhận diện; tiêu chí này không cộng điểm."
             : "Hồ sơ có " + Optional.ofNullable(freelancer.getExperienceYears()).orElse(0)
                     + " năm; yêu cầu tối thiểu " + match.requiredExperienceYears() + " năm.";
     String budgetDetail;
     if (!match.budgetEvaluated()) {
         budgetDetail = "Chưa đủ dữ liệu đơn giá hoặc ngân sách để ước tính.";
     } else {
         java.text.NumberFormat money = java.text.NumberFormat.getNumberInstance(new Locale("vi", "VN"));
         int days = Math.max(1, Optional.ofNullable(job.getEstimatedDurationDays()).orElse(14));
         budgetDetail = money.format(freelancer.getHourlyRate()) + "đ/giờ × 4 giờ/ngày × "
                 + days + " ngày = " + money.format(match.estimatedBudget()) + "đ; ngân sách tin: "
                 + money.format(job.getBudget()) + "đ.";
     }
     return List.of(
             factor("Kỹ năng yêu cầu", match.skillPoints(), 50,
                     skillDetail),
             factor("Nội dung tin và hồ sơ", match.textPoints(), 10,
                     "Mức độ tương thích giữa nội dung công việc với kỹ năng, chức danh và phần giới thiệu của Freelancer."),
             factor("Lĩnh vực", match.fieldPoints(), 20,
                     "Lĩnh vực công việc: " + safe(job.getCategory()) + " · Lĩnh vực Freelancer: "
                             + safe(freelancer.getField()) + "."),
             factor("Kinh nghiệm", match.experiencePoints(), 10, experienceDetail),
             factor("Ngân sách ước tính", match.budgetPoints(), 5, budgetDetail),
             factor("Sẵn sàng nhận việc", match.availabilityPoints(), 5,
                     Boolean.TRUE.equals(freelancer.getAvailable())
                             ? "Freelancer đang bật trạng thái sẵn sàng."
                             : "Freelancer chưa bật trạng thái sẵn sàng.")
     );
 }

 private Map<String, Object> factor(String label, int points, int maximum, String detail) {
     Map<String, Object> item = new HashMap<>();
     item.put("label", label);
     item.put("points", points);
     item.put("maximum", maximum);
     item.put("percent", maximum == 0 ? 0 : points * 100 / maximum);
     item.put("detail", detail);
     return item;
 }

 private static String safe(String value) {
     return value == null || value.isBlank() ? "Chưa cập nhật" : value;
 }

 @GetMapping("/customer/favorites")
 @Transactional(readOnly = true)
 public String favoriteFreelancers(Authentication auth, Model model,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "all") String availability,
        @RequestParam(defaultValue = "") String field,
        @RequestParam(defaultValue = "all") String rate,
        @RequestParam(defaultValue = "newest") String sort,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) Long jobId) {
     User client = me(auth); customer(client);
     List<SavedFreelancer> saved = savedFreelancers.findByCustomer(client);
     List<Job> openJobs = jobs.findByCustomerOrderByCreatedAtDesc(client).stream()
             .filter(j -> j.getStatus() == JobStatus.OPEN).toList();
     Job selectedJob = jobId == null ? (openJobs.isEmpty() ? null : openJobs.get(0)) :
             openJobs.stream().filter(j -> j.getId().equals(jobId)).findFirst().orElse(null);
     String keyword = norm(q), wantedField = norm(field);
     List<SavedFreelancer> results = saved.stream().filter(record -> {
         User f = record.getFreelancer();
         if (f.getRole()!=Role.FREELANCER || Boolean.TRUE.equals(f.getLocked()) || !Boolean.TRUE.equals(f.getEnabled())) return false;
         if (!keyword.isEmpty() && !norm(f.getFullName()+" "+f.getProfessionalTitle()+" "+f.getSkills()+" "+f.getBio()).contains(keyword)) return false;
         if (!wantedField.isEmpty() && !norm(f.getField()).contains(wantedField) && !norm(f.getSkills()).contains(wantedField)) return false;
         if ("online".equals(availability) && !Boolean.TRUE.equals(f.getAvailable())) return false;
         if ("offline".equals(availability) && Boolean.TRUE.equals(f.getAvailable())) return false;
         java.math.BigDecimal value=f.getHourlyRate();
         if ("under200".equals(rate) && (value==null || value.compareTo(new java.math.BigDecimal("200000"))>=0))return false;
         if ("200to300".equals(rate) && (value==null || value.compareTo(new java.math.BigDecimal("200000"))<0 || value.compareTo(new java.math.BigDecimal("300000"))>0))return false;
         if ("300to400".equals(rate) && (value==null || value.compareTo(new java.math.BigDecimal("300000"))<0 || value.compareTo(new java.math.BigDecimal("400000"))>0))return false;
         if ("over400".equals(rate) && (value==null || value.compareTo(new java.math.BigDecimal("400000"))<=0))return false;
         return true;
     }).collect(Collectors.toCollection(ArrayList::new));
     Comparator<SavedFreelancer> byRate=Comparator.comparing(r->r.getFreelancer().getHourlyRate(),Comparator.nullsLast(Comparator.naturalOrder()));
     switch(sort) {
         case "rateAsc" -> results.sort(byRate);
         case "rateDesc" -> results.sort(byRate.reversed());
         case "rating" -> results.sort(Comparator.comparingDouble((SavedFreelancer r)->Optional.ofNullable(r.getFreelancer().getRating()).orElse(0.0)).reversed());
         default -> results.sort(Comparator.comparing(SavedFreelancer::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder())));
     }
     int pages=Math.max(1,(results.size()+9)/10),current=Math.max(1,Math.min(page,pages)),start=(current-1)*10;
     model.addAttribute("favorites",results.subList(start,Math.min(results.size(),start+10)));
     model.addAttribute("totalSaved",saved.size());model.addAttribute("filteredCount",results.size());
     model.addAttribute("onlineCount",saved.stream().filter(r->Boolean.TRUE.equals(r.getFreelancer().getAvailable())).count());
     model.addAttribute("q",q);model.addAttribute("availability",availability);model.addAttribute("field",field);
     model.addAttribute("rate",rate);model.addAttribute("sort",sort);model.addAttribute("page",current);model.addAttribute("pages",pages);
     model.addAttribute("openJobs",openJobs);model.addAttribute("selectedJob",selectedJob);
     model.addAttribute("fields",saved.stream().map(r->r.getFreelancer().getField()).filter(Objects::nonNull).filter(f->!f.isBlank()).distinct().sorted().toList());
     return "customer/favorites";
 }
 @PostMapping("/customer/talent/favorites/{freelancerId}") @Transactional
 public String toggleSavedFreelancer(Authentication auth, @PathVariable Long freelancerId,
         @RequestParam(required = false) Long jobId,
         @RequestParam(defaultValue = "") String q,
         @RequestParam(defaultValue = "") String skill,
         @RequestParam(defaultValue = "") String field,
         @RequestParam(defaultValue = "match") String sort,
         @RequestParam(defaultValue = "") String workType,
         @RequestParam(required = false) Integer minExperience,
         @RequestParam(required = false) java.math.BigDecimal rateFrom,
         @RequestParam(required = false) java.math.BigDecimal rateTo,
         @RequestParam(defaultValue = "false") boolean onlySaved,
         @RequestParam(defaultValue = "1") int page,
         @RequestParam(defaultValue = "false") boolean backToProfile,
         @RequestParam(defaultValue = "false") boolean backToFavorites,
         RedirectAttributes ra) {
     User customerUser = me(auth);
     customer(customerUser);
     User freelancer = userRepo.findById(freelancerId).orElseThrow(() ->
             new IllegalArgumentException("Không tìm thấy Freelancer."));
     if (freelancer.getRole() != Role.FREELANCER || Boolean.TRUE.equals(freelancer.getLocked()))
         throw new IllegalArgumentException("Freelancer không khả dụng.");
     Optional<SavedFreelancer> existing = savedFreelancers.findByCustomerAndFreelancer(customerUser, freelancer);
     if (existing.isPresent()) {
         savedFreelancers.delete(existing.get());
         ra.addFlashAttribute("success", "Đã bỏ lưu Freelancer.");
     } else {
         SavedFreelancer record = new SavedFreelancer();
         record.setCustomer(customerUser);
         record.setFreelancer(freelancer);
         savedFreelancers.save(record);
         ra.addFlashAttribute("success", "Đã lưu Freelancer yêu thích.");
     }
     if (backToProfile) return "redirect:/customer/freelancers/" + freelancerId;
     if (backToFavorites) return "redirect:/customer/favorites";
     if (jobId != null) ra.addAttribute("jobId", jobId);
     if (!q.isBlank()) ra.addAttribute("q", q);
     if (!skill.isBlank()) ra.addAttribute("skill", skill);
     if (!field.isBlank()) ra.addAttribute("field", field);
     if (!sort.isBlank()) ra.addAttribute("sort", sort);
     if (!workType.isBlank()) ra.addAttribute("workType", workType);
     if (minExperience != null) ra.addAttribute("minExperience", minExperience);
     if (rateFrom != null) ra.addAttribute("rateFrom", rateFrom);
     if (rateTo != null) ra.addAttribute("rateTo", rateTo);
     if (onlySaved) ra.addAttribute("onlySaved", "true");
     if (page > 1) ra.addAttribute("page", page);
     return "redirect:/customer/talent";
 }
 @PostMapping("/customer/invitations") @Transactional
 public String invite(Authentication a,@RequestParam Long jobId,@RequestParam Long freelancerId,@RequestParam(required=false)String message,RedirectAttributes ra){
 try{User u=me(a);customer(u);Job job=ownedJob(jobId,u);
  if(job.getStatus()!=JobStatus.OPEN || (job.getDeadline()!=null && job.getDeadline().isBefore(java.time.LocalDate.now())))throw new IllegalArgumentException("Chỉ được mời vào công việc đang tuyển.");
  User f=userRepo.findById(freelancerId).orElseThrow();if(f.getRole()!=Role.FREELANCER||Boolean.TRUE.equals(f.getLocked())||!Boolean.TRUE.equals(f.getEnabled())||!Boolean.TRUE.equals(f.getAvailable()))throw new IllegalArgumentException("Freelancer không khả dụng.");
  JobInvitation i=invites.findByJobAndFreelancer(job,f).orElseGet(JobInvitation::new);
  if(i.getId()!=null && "SENT".equals(i.getStatus()))throw new IllegalArgumentException("Lời mời đang chờ phản hồi.");
  if(i.getId()!=null && "ACCEPTED".equals(i.getStatus()))throw new IllegalArgumentException("Freelancer đã đồng ý lời mời.");
  i.setJob(job);i.setCustomer(u);i.setFreelancer(f);i.setStatus("SENT");i.setMessage(message==null?"Mời bạn xem công việc và gửi Proposal.":message.trim());invites.save(i);
  ra.addFlashAttribute("success","Đã gửi lời mời đến "+f.getFullName());
 }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/invitations";}
 @GetMapping("/customer/invitations")
 @Transactional(readOnly = true)
 public String myInvites(Authentication auth, Model model,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) Long jobId,
        @RequestParam(defaultValue = "all") String status,
        @RequestParam(defaultValue = "newest") String sort,
        @RequestParam(defaultValue = "1") int page) {
    User owner = me(auth);
    customer(owner);
    List<JobInvitation> all = invites.findByCustomerOrderByCreatedAtDesc(owner);
    List<Job> ownJobs = jobs.findByCustomerOrderByCreatedAtDesc(owner);
    String chosenStatus = Set.of("all", "SENT", "ACCEPTED", "DECLINED").contains(status) ? status : "all";
    String chosenSort = Set.of("newest", "oldest", "budget_high", "budget_low").contains(sort) ? sort : "newest";
    Long selectedJob = jobId != null && ownJobs.stream().anyMatch(j -> jobId.equals(j.getId())) ? jobId : null;
    String keyword = norm(q);
    List<JobInvitation> result = all.stream().filter(inv -> {
        if (selectedJob != null && !selectedJob.equals(inv.getJob().getId())) return false;
        if (!"all".equals(chosenStatus) && !chosenStatus.equals(inv.getStatus())) return false;
        if (keyword.isBlank()) return true;
        String skills = inv.getFreelancer().getSkills();
        return norm(inv.getFreelancer().getFullName() + " " + inv.getFreelancer().getProfessionalTitle()
                + " " + skills + " " + inv.getJob().getTitle() + " " + inv.getJob().getCategory()).contains(keyword);
    }).collect(Collectors.toCollection(ArrayList::new));
    if ("oldest".equals(chosenSort)) {
        result.sort(Comparator.comparing(JobInvitation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    } else if ("budget_high".equals(chosenSort)) {
        result.sort(Comparator.comparing((JobInvitation i) -> i.getJob().getBudget(),
                Comparator.nullsLast(Comparator.reverseOrder())));
    } else if ("budget_low".equals(chosenSort)) {
        result.sort(Comparator.comparing((JobInvitation i) -> i.getJob().getBudget(),
                Comparator.nullsLast(Comparator.naturalOrder())));
    } else {
        result.sort(Comparator.comparing(JobInvitation::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
    }
    int pageSize = 10;
    int totalPages = Math.max(1, (result.size() + pageSize - 1) / pageSize);
    int currentPage = Math.max(1, Math.min(page, totalPages));
    int from = (currentPage - 1) * pageSize;
    List<JobInvitation> visible = result.subList(from, Math.min(from + pageSize, result.size()));
    // A chat thread exists only after the Freelancer has been selected for a real project.
    Map<Long, Long> projectByInvitation = new HashMap<>();
    List<Project> customerProjects = projectService.forCustomer(owner);
    for (JobInvitation invitation : visible) {
        customerProjects.stream().filter(project ->
                project.getFreelancer().getId().equals(invitation.getFreelancer().getId()) &&
                project.getApplication().getJob().getId().equals(invitation.getJob().getId()))
                .findFirst().ifPresent(project -> projectByInvitation.put(invitation.getId(), project.getId()));
    }
    model.addAttribute("invitations", visible);
    model.addAttribute("projectByInvitation", projectByInvitation);
    model.addAttribute("invitationCount", all.size());
    model.addAttribute("acceptedCount", all.stream().filter(i -> "ACCEPTED".equals(i.getStatus())).count());
    model.addAttribute("pendingCount", all.stream().filter(i -> "SENT".equals(i.getStatus())).count());
    model.addAttribute("declinedCount", all.stream().filter(i -> "DECLINED".equals(i.getStatus())).count());
    model.addAttribute("respondedCount", all.stream().filter(i -> !"SENT".equals(i.getStatus())).count());
    model.addAttribute("sentJobs", ownJobs);
    model.addAttribute("q", q.trim());
    model.addAttribute("jobId", selectedJob);
    model.addAttribute("status", chosenStatus);
    model.addAttribute("sort", chosenSort);
    model.addAttribute("totalFiltered", result.size());
    model.addAttribute("currentPage", currentPage);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("pageNumbers", java.util.stream.IntStream.rangeClosed(
            Math.max(1, Math.min(currentPage - 2, totalPages - 4)),
            Math.min(totalPages, Math.max(5, currentPage + 2))).boxed().toList());
    return "customer/invitations";
 }

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
  int total=filtered.size(),pageSize=10,totalPages=Math.max(1,(total+pageSize-1)/pageSize);
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
