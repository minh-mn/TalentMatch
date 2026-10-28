package com.talentmatch.controller;

import com.talentmatch.dto.*;
import com.talentmatch.model.*;
import com.talentmatch.repository.JobRepository;
import com.talentmatch.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/customer")
public class CustomerController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CustomerController.class);
    private static final Set<String> PRICE_TYPES = Set.of("FIXED", "NEGOTIABLE");
    private static final Set<String> WORK_TYPES = Set.of("REMOTE", "HYBRID", "ONSITE");
    private static final Set<String> LEVELS = Set.of("JUNIOR", "MIDDLE", "SENIOR", "ENTRY", "INTERMEDIATE", "EXPERT");
    private static final BigDecimal MAX_BUDGET = new BigDecimal("100000000000");
    private final UserService users; private final JobService jobs; private final ProjectService projects;
    private final PaymentService payments; private final DashboardService dashboard; private final JobRepository jobRepository;
    public CustomerController(UserService users,JobService jobs,ProjectService projects,PaymentService payments,DashboardService dashboard,JobRepository jobRepository){this.users=users;this.jobs=jobs;this.projects=projects;this.payments=payments;this.dashboard=dashboard;this.jobRepository=jobRepository;}

    @GetMapping("/dashboard")
    public String dashboard(
            Authentication auth,
            Model model,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "projects") String metric,
            @RequestParam(defaultValue = "status") String pieMode) {

        User u = users.byEmail(auth.getName());

        if (u == null) {
            throw new IllegalStateException(
                    "Không tìm thấy tài khoản Client đang đăng nhập.");
        }

        // Tên doanh nghiệp
        String companyName = u.getCompanyName();

        if (companyName == null || companyName.isBlank()) {
            companyName = u.getFullName();
        }

        if (companyName == null || companyName.isBlank()) {
            companyName = "Doanh nghiệp";
        }

        companyName = companyName.trim();

        model.addAttribute("companyDisplayName", companyName);
        model.addAttribute(
                "companyInitial",
                companyName.substring(0, 1).toUpperCase()
        );

        // Thống kê
        model.addAttribute("stats", dashboard.customerStats(u));

        // Danh sách công việc gần đây
        List<Job> allJobs =
                jobRepository.findByCustomerOrderByCreatedAtDesc(u);

        model.addAttribute(
                "jobs",
                allJobs.stream().limit(5).toList()
        );

        // Tổng số ứng viên và số ứng viên theo từng công việc
        java.util.Map<Long, Long> jobApplicantCounts =
                new java.util.HashMap<>();

        long applicantTotal = 0;

        for (Job job : allJobs) {
            long count = jobs.applicants(job).size();
            jobApplicantCounts.put(job.getId(), count);
            applicantTotal += count;
        }

        model.addAttribute("jobApplicantCounts", jobApplicantCounts);
        model.addAttribute("applicantTotal", applicantTotal);

        // Danh sách dự án
        model.addAttribute(
                "projects",
                projects.forCustomer(u).stream().limit(5).toList()
        );

        // Biểu đồ
        addCharts(model, u, from, to, metric, pieMode);

        return "customer/dashboard";
    }

    @GetMapping("/profile")
    public String profile(Authentication auth, Model model) {
        model.addAttribute("profile", users.byEmail(auth.getName()));
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            Authentication auth,
            @ModelAttribute User profile,
            RedirectAttributes ra) {
        try {
            users.updateProfile(auth.getName(), profile);
            ra.addFlashAttribute(
                    "success",
                    "Cập nhật hồ sơ khách hàng thành công."
            );
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/profile";
    }
    @GetMapping("/jobs")
    public String jobs(Authentication auth,@RequestParam(defaultValue="ALL") String status,Model model){
        User u=users.byEmail(auth.getName());
        List<Job> all=jobRepository.findByCustomerOrderByCreatedAtDesc(u);
        List<Job> filtered=all;
        if(!"ALL".equalsIgnoreCase(status)){
            try{JobStatus target=JobStatus.valueOf(status.toUpperCase());filtered=all.stream().filter(j->j.getStatus()==target).toList();}catch(Exception ignored){}
        }
        model.addAttribute("jobs",filtered);
        java.util.Map<Long,Long> applicantCounts=new java.util.HashMap<>();
        all.forEach(j->applicantCounts.put(j.getId(),(long)jobs.applicants(j).size()));
        model.addAttribute("applicantCounts",applicantCounts);
        model.addAttribute("status",status);
        model.addAttribute("allCount",all.size());
        model.addAttribute("openCount",all.stream().filter(j->j.getStatus()==JobStatus.OPEN).count());
        model.addAttribute("pausedCount",all.stream().filter(j->j.getStatus()==JobStatus.PAUSED).count());
        model.addAttribute("closedCount",all.stream().filter(j->j.getStatus()==JobStatus.CLOSED).count());
        model.addAttribute("cancelledCount",all.stream().filter(j->j.getStatus()==JobStatus.CANCELLED).count());
        return "customer/jobs";
    }
    @GetMapping("/applicants") public String allApplicants(Authentication auth,Model model){
        User customer=users.byEmail(auth.getName());
        var all=jobRepository.findByCustomerOrderByCreatedAtDesc(customer);
        var rows=all.stream().flatMap(job->jobs.applicants(job).stream()).sorted(java.util.Comparator.comparing(JobApplication::getCreatedAt).reversed()).toList();
        model.addAttribute("applications",rows);model.addAttribute("allJobs",all);return "customer/all-applicants";
    }
    @GetMapping("/jobs/new") public String newJob(Model model){model.addAttribute("job",new Job());model.addAttribute("editing",false);return "customer/job-form";}

    /**
     * Đăng công việc. Chỉ sao chép các trường được phép từ form (cleanCopy) để Client không thể tự đặt
     * featured/verified/escrowFunded/status/customer qua tham số POST. Lỗi nhập liệu thì hiển thị lại form
     * với dữ liệu đã nhập thay vì chuyển hướng làm mất dữ liệu.
     */
    @PostMapping("/jobs")
    public String createJob(Authentication auth,@ModelAttribute("job") Job form,BindingResult binding,Model model,RedirectAttributes ra){
        try{
            if(binding.hasErrors())throw new IllegalArgumentException(bindingMessage(binding));
            Job job=cleanCopy(form);
            validateJob(job,null);
            job.setCustomer(users.byEmail(auth.getName()));
            job.setStatus(JobStatus.OPEN);
            Job saved=jobs.save(job);
            ra.addFlashAttribute("success","Đăng công việc thành công.");
            return "redirect:/customer/jobs/"+saved.getId()+"/applicants";
        }catch(IllegalArgumentException e){
            model.addAttribute("error",e.getMessage());
        }catch(Exception e){
            log.error("Không thể đăng công việc", e);
            model.addAttribute("error","Không thể lưu công việc lúc này. Vui lòng kiểm tra lại dữ liệu và thử lại.");
        }
        model.addAttribute("editing",false);
        return "customer/job-form";
    }
    @GetMapping("/jobs/{id}/edit") public String editJob(@PathVariable Long id,Authentication auth,Model model){Job j=ownedJob(id,auth);model.addAttribute("job",j);model.addAttribute("editing",true);return "customer/job-form";}
    @PostMapping("/jobs/{id}")
    public String updateJob(@PathVariable Long id,Authentication auth,@ModelAttribute("job") Job form,BindingResult binding,Model model,RedirectAttributes ra){
        Job j;
        try{
            j=ownedJob(id,auth);
            if(j.getStatus()==JobStatus.CLOSED||j.getStatus()==JobStatus.CANCELLED)throw new IllegalArgumentException("Không thể sửa công việc đã đóng/hủy.");
        }catch(Exception e){ra.addFlashAttribute("error",e.getMessage());return "redirect:/customer/jobs";}
        try{
            if(binding.hasErrors())throw new IllegalArgumentException(bindingMessage(binding));
            Job c=cleanCopy(form);
            validateJob(c,j.getDeadline());
            j.setTitle(c.getTitle());j.setCategory(c.getCategory());j.setDescription(c.getDescription());j.setRequiredSkills(c.getRequiredSkills());
            j.setBudget(c.getBudget());j.setPriceType(c.getPriceType());j.setDeadline(c.getDeadline());j.setWorkType(c.getWorkType());
            j.setExperienceLevel(c.getExperienceLevel());j.setAcceptanceCriteria(c.getAcceptanceCriteria());j.setEstimatedDurationDays(c.getEstimatedDurationDays());
            jobs.save(j);
            ra.addFlashAttribute("success","Cập nhật công việc thành công.");
            return "redirect:/customer/jobs";
        }catch(IllegalArgumentException e){
            model.addAttribute("error",e.getMessage());
        }catch(Exception e){
            log.error("Không thể cập nhật công việc {}", id, e);
            model.addAttribute("error","Không thể lưu công việc lúc này. Vui lòng thử lại.");
        }
        form.setId(id);
        model.addAttribute("editing",true);
        return "customer/job-form";
    }
    @PostMapping("/jobs/{id}/status") public String jobStatus(@PathVariable Long id,@RequestParam JobStatus status,Authentication auth,RedirectAttributes ra){try{Job j=ownedJob(id,auth);if(status==JobStatus.CLOSED&&jobs.applicants(j).stream().noneMatch(a->a.getStatus()==ApplicationStatus.ACCEPTED))throw new IllegalArgumentException("Hãy chọn Freelancer trước khi đóng tuyển theo luồng dự án, hoặc dùng PAUSED/CANCELLED.");j.setStatus(status);jobs.save(j);ra.addFlashAttribute("success","Đã cập nhật trạng thái công việc.");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/jobs";}
    @GetMapping("/jobs/{id}/applicants") public String applicants(@PathVariable Long id,Authentication auth,Model model){Job j=ownedJob(id,auth);model.addAttribute("job",j);model.addAttribute("applications",jobs.applicants(j));return "customer/applicants";}
    @PostMapping("/applications/{id}/shortlist") public String shortlist(@PathVariable Long id,@RequestParam boolean value,Authentication auth,RedirectAttributes ra){try{projects.shortlist(id,users.byEmail(auth.getName()),value);ra.addFlashAttribute("success",value?"Đã đưa ứng viên vào danh sách ưu tiên.":"Đã từ chối ứng viên.");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/jobs";}
    @PostMapping("/applications/{id}/accept") public String accept(@PathVariable Long id,Authentication auth,RedirectAttributes ra){try{Project p=projects.acceptApplication(id,users.byEmail(auth.getName()));ra.addFlashAttribute("success","Đã chọn Freelancer. Dự án đã được tạo và sẵn sàng thanh toán.");return "redirect:/projects/"+p.getId();}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());return "redirect:/customer/jobs";}}
    @GetMapping("/projects") public String projects(Authentication auth,Model model){model.addAttribute("projects",projects.forCustomer(users.byEmail(auth.getName())));return "customer/projects";}
    @GetMapping("/history") public String history(Authentication auth,Model model){User u=users.byEmail(auth.getName());model.addAttribute("jobs",jobRepository.findByCustomerOrderByCreatedAtDesc(u));model.addAttribute("projects",projects.forCustomer(u));model.addAttribute("payments",payments.history(u));return "customer/history";}

    private Job ownedJob(Long id,Authentication auth){Job j=jobs.get(id);User u=users.byEmail(auth.getName());if(!j.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Bạn không sở hữu công việc này.");return j;}
    private static String trimToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String pick(String v,Set<String> allowed,String fallback,String label){
        String t=trimToNull(v);
        if(t==null)return fallback;
        t=t.toUpperCase();
        if(!allowed.contains(t))throw new IllegalArgumentException(label+" không hợp lệ.");
        return t;
    }
    /** Bản sao chỉ gồm các trường Client được phép nhập (whitelist), đã chuẩn hóa. */
    private Job cleanCopy(Job f){
        Job j=new Job();
        j.setTitle(trimToNull(f.getTitle()));
        j.setCategory(trimToNull(f.getCategory()));
        j.setDescription(trimToNull(f.getDescription()));
        j.setRequiredSkills(trimToNull(f.getRequiredSkills()));
        j.setBudget(f.getBudget());
        j.setPriceType(pick(f.getPriceType(),PRICE_TYPES,"FIXED","Cách tính phí"));
        j.setDeadline(f.getDeadline());
        j.setWorkType(pick(f.getWorkType(),WORK_TYPES,"REMOTE","Hình thức làm việc"));
        j.setExperienceLevel(pick(f.getExperienceLevel(),LEVELS,"MIDDLE","Cấp kinh nghiệm"));
        j.setAcceptanceCriteria(trimToNull(f.getAcceptanceCriteria()));
        j.setEstimatedDurationDays(f.getEstimatedDurationDays()==null?14:f.getEstimatedDurationDays());
        return j;
    }
    private String bindingMessage(BindingResult br){
        var f=br.getFieldError();
        if(f==null)return "Dữ liệu biểu mẫu không hợp lệ.";
        return switch(f.getField()){
            case "deadline"->"Hạn hoàn thành không hợp lệ. Vui lòng chọn lại ngày.";
            case "budget"->"Ngân sách không hợp lệ. Hãy nhập một số lớn hơn 0.";
            case "estimatedDurationDays"->"Số ngày dự kiến không hợp lệ (từ 1 đến 3650).";
            default->"Giá trị của trường \""+f.getField()+"\" không hợp lệ.";
        };
    }
    /** previousDeadline: khi sửa, deadline cũ đã qua mà không đổi thì vẫn cho lưu các thay đổi khác. */
    private void validateJob(Job j,LocalDate previousDeadline){
        if(j.getTitle()==null||j.getTitle().length()<5)throw new IllegalArgumentException("Tên công việc phải có ít nhất 5 ký tự.");
        if(j.getTitle().length()>220)throw new IllegalArgumentException("Tên công việc tối đa 220 ký tự.");
        if(j.getCategory()==null)throw new IllegalArgumentException("Vui lòng chọn lĩnh vực.");
        if(j.getCategory().length()>120)throw new IllegalArgumentException("Lĩnh vực không hợp lệ.");
        if(j.getDescription()==null||j.getDescription().length()<30)throw new IllegalArgumentException("Mô tả công việc nên có ít nhất 30 ký tự.");
        if(j.getRequiredSkills()!=null&&j.getRequiredSkills().length()>1000)throw new IllegalArgumentException("Kỹ năng yêu cầu tối đa 1000 ký tự.");
        if(j.getBudget()==null||j.getBudget().signum()<=0)throw new IllegalArgumentException("Ngân sách phải lớn hơn 0.");
        if(j.getBudget().compareTo(MAX_BUDGET)>0)throw new IllegalArgumentException("Ngân sách tối đa là 100.000.000.000đ.");
        if(j.getEstimatedDurationDays()<1||j.getEstimatedDurationDays()>3650)throw new IllegalArgumentException("Số ngày dự kiến phải từ 1 đến 3650.");
        if(j.getDeadline()!=null&&j.getDeadline().isBefore(LocalDate.now())&&!j.getDeadline().equals(previousDeadline))throw new IllegalArgumentException("Deadline không được ở quá khứ.");
    }
    private void addCharts(Model model,User u,LocalDate from,LocalDate to,String metric,String pieMode){List<ChartPoint> bars=dashboard.bar(u,from,to,metric);double max=bars.stream().mapToDouble(ChartPoint::value).max().orElse(1);model.addAttribute("barData",bars);model.addAttribute("barMax",max<=0?1:max);List<PieSlice> pie=dashboard.pie(u,from,to,pieMode);model.addAttribute("pieData",pie);model.addAttribute("pieGradient",gradient(pie));model.addAttribute("from",from);model.addAttribute("to",to);model.addAttribute("metric",metric);model.addAttribute("pieMode",pieMode);}
    private String gradient(List<PieSlice>s){if(s.isEmpty())return "conic-gradient(#eef0f7 0 100%)";String[]c={"#6c3bff","#2f6fed","#13bfa6","#ffb020","#f0447d","#7b8aa4"};double start=0;StringBuilder b=new StringBuilder("conic-gradient(");for(int i=0;i<s.size();i++){double end=start+s.get(i).percent();if(i>0)b.append(',');b.append(c[i%c.length]).append(' ').append(String.format(java.util.Locale.US,"%.2f",start)).append("% ").append(String.format(java.util.Locale.US,"%.2f",end)).append('%');start=end;}return b.append(')').toString();}
}
