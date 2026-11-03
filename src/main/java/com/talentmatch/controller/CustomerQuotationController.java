package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Ba cap xem bao gia, chi tra ve du lieu thuoc Client dang dang nhap. */
@Controller
@RequestMapping("/customer/quotations")
public class CustomerQuotationController {
    private final UserService users;
    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final QuoteService quotes;
    private final ProjectRepository projects;
    private final ProjectService projectService;

    public CustomerQuotationController(UserService users, JobRepository jobs,
            JobApplicationRepository applications, QuoteService quotes, ProjectRepository projects, ProjectService projectService) {
        this.users=users; this.jobs=jobs; this.applications=applications; this.quotes=quotes; this.projects=projects; this.projectService=projectService;
    }
    private User client(Authentication auth) {
        if(auth==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        User u=users.byEmail(auth.getName());
        if(u==null||u.getRole()!=Role.CUSTOMER)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return u;
    }
    private Job ownJob(Long jobId, User u) {
        Job job=jobs.findById(jobId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!job.getCustomer().getId().equals(u.getId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return job;
    }
    private List<JobApplication> candidates(Job job){return applications.findByJobOrderByCreatedAtDesc(job);}
    private Map<Long,QuoteRevision> revisions(List<JobApplication> apps){
        Map<Long,QuoteRevision> map=new LinkedHashMap<>();
        for(JobApplication a:apps)quotes.latest(a).ifPresent(r->map.put(a.getId(),r));
        return map;
    }
    private void common(Model model,User u){model.addAttribute("viewer",u);model.addAttribute("isClient",true);}
    /** Danh sách từng báo giá, tránh trùng bản ghi khi một ứng tuyển có nhiều phiên bản. */
    @GetMapping
    public String index(Authentication auth,Model model,
        @RequestParam(defaultValue="")String q,
        @RequestParam(defaultValue="all")String status,
        @RequestParam(defaultValue="all")String job,
        @RequestParam(defaultValue="all")String freelancer,
        @RequestParam(defaultValue="newest")String sort,
        @RequestParam(defaultValue="1")int page,
        @RequestParam(defaultValue="5")int size){
        User u=client(auth);common(model,u);
        List<Job> owned=jobs.findByCustomerOrderByCreatedAtDesc(u);
        List<JobApplication> all=new ArrayList<>();
        for(Job j:owned)all.addAll(candidates(j));
        Map<Long,QuoteRevision> latest=revisions(all);
        long waiting=latest.values().stream().filter(r->r.getStatus()==QuoteStatus.PENDING&&quotes.isRecipient(r,u)).count();
        long accepted=latest.values().stream().filter(r->r.getStatus()==QuoteStatus.ACCEPTED).count();
        long negotiating=latest.values().stream().filter(r->r.getStatus()==QuoteStatus.PENDING&&r.getVersion()>1).count();
        long rejected=latest.values().stream().filter(r->r.getStatus()==QuoteStatus.REJECTED||r.getStatus()==QuoteStatus.WITHDRAWN).count();
        String term=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        List<JobApplication> filtered=all.stream()
            .filter(a->term.isBlank()||(a.getJob().getTitle()+" "+a.getFreelancer().getFullName()+" "+a.getJob().getId()).toLowerCase(Locale.ROOT).contains(term))
            .filter(a->"all".equals(job)||String.valueOf(a.getJob().getId()).equals(job))
            .filter(a->"all".equals(freelancer)||String.valueOf(a.getFreelancer().getId()).equals(freelancer))
            .filter(a->{QuoteRevision r=latest.get(a.getId());
                if("all".equals(status))return true;
                if("negotiating".equals(status))return r!=null&&r.getStatus()==QuoteStatus.PENDING&&r.getVersion()>1;
                if("waiting".equals(status))return r!=null&&r.getStatus()==QuoteStatus.PENDING&&quotes.isRecipient(r,u);
                if("accepted".equals(status))return r!=null&&r.getStatus()==QuoteStatus.ACCEPTED;
                if("rejected".equals(status))return r!=null&&(r.getStatus()==QuoteStatus.REJECTED||r.getStatus()==QuoteStatus.WITHDRAWN);
                return false;
            }).collect(Collectors.toCollection(ArrayList::new));
        Comparator<JobApplication> byPrice=Comparator.comparing(a->latest.containsKey(a.getId())?latest.get(a.getId()).getAmount():a.getProposedPrice(),Comparator.nullsLast(Comparator.naturalOrder()));
        if("priceLow".equals(sort))filtered.sort(byPrice);
        else if("priceHigh".equals(sort))filtered.sort(byPrice.reversed());
        else {
            filtered.sort(Comparator.comparing(JobApplication::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())));
            if(!"oldest".equals(sort))Collections.reverse(filtered);
        }
        int count=filtered.size();int perPage=size==10||size==20?size:5;
        int pages=Math.max(1,(count+perPage-1)/perPage);int current=Math.max(1,Math.min(page,pages));
        model.addAttribute("quotesList",filtered.subList((current-1)*perPage,Math.min(current*perPage,count)));
        model.addAttribute("latestById",latest);model.addAttribute("ownedJobs",owned);
        model.addAttribute("freelancerOptions",all.stream().map(JobApplication::getFreelancer).collect(Collectors.toMap(User::getId,x->x,(a,b)->a,LinkedHashMap::new)).values());
        model.addAttribute("totalQuotes",latest.size());model.addAttribute("waitingQuotes",waiting);
        model.addAttribute("acceptedQuotes",accepted);model.addAttribute("negotiatingQuotes",negotiating);
        model.addAttribute("rejectedQuotes",rejected);model.addAttribute("projectCount",all.stream().map(a->a.getJob().getId()).distinct().count());
        model.addAttribute("totalCount",count);model.addAttribute("currentPage",current);
        model.addAttribute("totalPages",pages);model.addAttribute("pageSize",perPage);
        model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("jobFilter",job);
        model.addAttribute("freelancerFilter",freelancer);model.addAttribute("sort",sort);
        return "customer/quotations";
    }
    @GetMapping("/jobs/{jobId}")
    public String job(@PathVariable Long jobId,Authentication auth,Model model,
                      @RequestParam(defaultValue="newest")String sort){
        User u=client(auth);Job j=ownJob(jobId,u);common(model,u);
        List<JobApplication> list=new ArrayList<>(candidates(j));Map<Long,QuoteRevision> versions=revisions(list);
        Comparator<JobApplication> byMoney=Comparator.comparing(a->versions.containsKey(a.getId())?versions.get(a.getId()).getAmount():a.getProposedPrice(),Comparator.nullsLast(Comparator.naturalOrder()));
        if("priceLow".equals(sort))list.sort(byMoney);
        if("priceHigh".equals(sort))list.sort(byMoney.reversed());
        if("days".equals(sort))list.sort(Comparator.comparingInt(a->versions.containsKey(a.getId())?versions.get(a.getId()).getEstimatedDays():a.getEstimatedDays()));
        model.addAttribute("job",j);model.addAttribute("candidates",list);model.addAttribute("latestById",versions);
        model.addAttribute("sort",sort);model.addAttribute("replyCount",versions.values().stream().filter(r->r.getStatus()==QuoteStatus.PENDING&&quotes.isRecipient(r,u)).count());
        model.addAttribute("minPrice",list.stream().map(a->versions.containsKey(a.getId())?versions.get(a.getId()).getAmount():a.getProposedPrice()).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null));
        model.addAttribute("minDays",list.stream().mapToInt(a->versions.containsKey(a.getId())?versions.get(a.getId()).getEstimatedDays():a.getEstimatedDays()).min().orElse(0));
        return "customer/quotations-job";
    }
    @GetMapping("/jobs/{jobId}/applications/{applicationId}")
    public String detail(@PathVariable Long jobId,@PathVariable Long applicationId,Authentication auth,Model model){
        User u=client(auth);Job job=ownJob(jobId,u);
        JobApplication a=quotes.application(applicationId,u);
        if(!a.getJob().getId().equals(job.getId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        common(model,u);List<QuoteRevision> history=quotes.history(a,u);
        QuoteRevision latest=history.isEmpty()?null:history.get(0);
        Project project=projects.findByApplication(a).orElse(null);
        boolean eligible=a.getStatus()!=ApplicationStatus.WITHDRAWN&&a.getStatus()!=ApplicationStatus.REJECTED
            &&(job.getStatus()==JobStatus.OPEN||job.getStatus()==JobStatus.PAUSED)&&project==null;
        model.addAttribute("job",job);model.addAttribute("application",a);model.addAttribute("history",history);
        model.addAttribute("contractJobId",job.getId());
        model.addAttribute("contractApplicationId",a.getId());
        model.addAttribute("quote",latest);model.addAttribute("project",project);
        model.addAttribute("canRespond",eligible&&latest!=null&&latest.getStatus()==QuoteStatus.PENDING&&quotes.isRecipient(latest,u));
        model.addAttribute("canCounter",eligible&&latest!=null&&(latest.getStatus()==QuoteStatus.PENDING||latest.getStatus()==QuoteStatus.REJECTED));
        // V132: prepare presentation values on the server; avoid long SpEL chains breaking page rendering.
        java.text.NumberFormat moneyFormat=java.text.NumberFormat.getNumberInstance(new java.util.Locale("vi", "VN"));
        model.addAttribute("jobBudgetLabel",job.getBudget()==null?"Thỏa thuận":moneyFormat.format(job.getBudget())+"đ");
        model.addAttribute("jobDateLabel",job.getCreatedAt()==null?"—":job.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("deadlineLabel",job.getDeadline()==null?"Chưa xác định":job.getDeadline().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("freelancerName",a.getFreelancer().getFullName()==null?"Freelancer":a.getFreelancer().getFullName());
        model.addAttribute("quoteAmountLabel",latest==null?"Chưa có báo giá":moneyFormat.format(latest.getAmount())+"đ");
        model.addAttribute("quoteDaysLabel",latest==null?"—":latest.getEstimatedDays()+" ngày");
        model.addAttribute("quoteVersionLabel",latest==null?"—":"v"+latest.getVersion());
        model.addAttribute("quoteTimeLabel",latest==null?"—":latest.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        String statusLabel="Chưa khởi tạo";
        if(latest!=null){
            switch(latest.getStatus()){
                case ACCEPTED: statusLabel="Đã chốt báo giá"; break;
                case REJECTED: statusLabel="Đã từ chối"; break;
                case PENDING: statusLabel=quotes.isRecipient(latest,u)?"Chờ Client phản hồi":"Chờ Freelancer phản hồi"; break;
                default: statusLabel="Đang xử lý";
            }
        }
        model.addAttribute("quoteStatusLabel",statusLabel);
        java.util.List<java.util.Map<String,String>> quoteTimeline=new java.util.ArrayList<>();
        for(QuoteRevision revision:history){
            java.util.Map<String,String> item=new java.util.HashMap<>();
            item.put("author",revision.getAuthor().getFullName());
            item.put("version","v"+revision.getVersion());
            item.put("amount",moneyFormat.format(revision.getAmount())+"đ");
            item.put("date",revision.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            item.put("days",revision.getEstimatedDays()+" ngày");
            item.put("status",revision.getStatus().name());
            item.put("reason",revision.getRejectionReason()==null?"":revision.getRejectionReason());
            quoteTimeline.add(item);
        }
        model.addAttribute("quoteTimeline",quoteTimeline);

        return "customer/quotations-detail";
    }
    /** Select the freelancer ONLY after an accepted quote; always return to a valid route. */
    @PostMapping("/create-contract")
    public String createContract(@RequestParam("jobId") Long jobId, @RequestParam("applicationId") Long applicationId,
                                 Authentication auth, RedirectAttributes flash) {
        User user = client(auth);
        Job job = ownJob(jobId, user);
        JobApplication application = quotes.application(applicationId, user);
        if (!application.getJob().getId().equals(job.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try {
            Project existing = projects.findByApplication(application).orElse(null);
            if (existing != null) {
                flash.addFlashAttribute("success", "Dự án đã tồn tại, đang mở hợp đồng hiện có.");
                return "redirect:/projects/" + existing.getId();
            }
            QuoteRevision accepted = quotes.accepted(application).orElseThrow(() ->
                new IllegalArgumentException("Báo giá chưa được cả hai bên chấp nhận."));
            if (accepted.getStatus() != QuoteStatus.ACCEPTED)
                throw new IllegalArgumentException("Báo giá chưa được chốt.");
            Project created = projectService.acceptApplication(applicationId, user);
            flash.addFlashAttribute("success", "Đã chọn Freelancer và tạo dự thảo hợp đồng. Hai bên cần ký xác nhận trước thanh toán.");
            return "redirect:/projects/" + created.getId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/quotations/jobs/" + jobId + "/applications/" + applicationId;
        }
    }

    /** Điều hướng nút Trao đổi sang Tin nhắn; không sử dụng trang exchange rỗng.
     * Chỉ có hội thoại trực tiếp theo project khi Client đã chọn Freelancer.
     */
    @GetMapping("/jobs/{jobId}/applications/{applicationId}/exchange")
    public String exchange(@PathVariable Long jobId, @PathVariable Long applicationId,
                           Authentication auth, org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        User u = client(auth);
        Job j = ownJob(jobId, u);
        JobApplication a = quotes.application(applicationId, u);
        if (!a.getJob().getId().equals(j.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Optional<Project> project = projects.findByApplication(a);
        if (project.isPresent())
            return "redirect:/customer/messages?projectId=" + project.get().getId();
        flash.addFlashAttribute("error", "Chưa có cuộc trò chuyện dự án cho ứng tuyển này. " +
                "Hiện tại Tin nhắn hỗ trợ các dự án đã chọn Freelancer. " +
                "Bạn vẫn có thể trao đổi giá bằng nút Xem chi tiết báo giá.");
        return "redirect:/customer/messages";
    }

}
