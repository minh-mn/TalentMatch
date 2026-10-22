package com.talentmatch.controller;

import com.talentmatch.dto.*;
import com.talentmatch.model.*;
import com.talentmatch.repository.JobRepository;
import com.talentmatch.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Controller
@RequestMapping("/customer")
public class CustomerController {
    private final UserService users; private final JobService jobs; private final ProjectService projects;
    private final PaymentService payments; private final DashboardService dashboard; private final JobRepository jobRepository;
    public CustomerController(UserService users,JobService jobs,ProjectService projects,PaymentService payments,DashboardService dashboard,JobRepository jobRepository){this.users=users;this.jobs=jobs;this.projects=projects;this.payments=payments;this.dashboard=dashboard;this.jobRepository=jobRepository;}

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth,Model model,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,
                            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,
                            @RequestParam(defaultValue="projects")String metric,@RequestParam(defaultValue="status")String pieMode){
        User u = users.byEmail(auth.getName());
        List<Job> allJobs = jobRepository.findByCustomerOrderByCreatedAtDesc(u);
        List<Project> allProjects = projects.forCustomer(u);
        model.addAttribute("companyDisplayName", u.getCompanyName() != null && !u.getCompanyName().isBlank() ? u.getCompanyName() : u.getFullName());
        model.addAttribute("companyInitial", (u.getCompanyName() != null && !u.getCompanyName().isBlank() ? u.getCompanyName() : u.getFullName()).substring(0, 1).toUpperCase());
        model.addAttribute("stats", dashboard.customerStats(u));
        model.addAttribute("applicantTotal", allJobs.stream().mapToLong(job -> jobs.applicants(job).size()).sum());
        model.addAttribute("jobs", allJobs.stream().limit(5).toList());
        model.addAttribute("projects", allProjects.stream().limit(5).toList());
        model.addAttribute("jobApplicantCounts", allJobs.stream().collect(java.util.stream.Collectors.toMap(Job::getId, job -> jobs.applicants(job).size())));
        model.addAttribute("monthlyCostByCategory", Map.of());
        addCharts(model, u, from, to, metric, pieMode);
        return "customer/dashboard";
    }
    @GetMapping("/profile") public String profile(Authentication auth,Model model){model.addAttribute("profile",users.byEmail(auth.getName()));return "customer/profile";}
    @PostMapping("/profile") public String updateProfile(Authentication auth,@ModelAttribute User profile,RedirectAttributes ra){try{users.updateProfile(auth.getName(),profile);ra.addFlashAttribute("success","Cập nhật hồ sơ khách hàng thành công.");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/profile";}

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
    @PostMapping("/jobs") public String createJob(Authentication auth,@ModelAttribute Job form,RedirectAttributes ra){try{User u=users.byEmail(auth.getName());form.setId(null);form.setCustomer(u);form.setStatus(JobStatus.OPEN);validateJob(form);Job saved=jobs.save(form);ra.addFlashAttribute("success","Đăng công việc thành công.");return "redirect:/customer/jobs/"+saved.getId()+"/applicants";}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());return "redirect:/customer/jobs/new";}}
    @GetMapping("/jobs/{id}/edit") public String editJob(@PathVariable Long id,Authentication auth,Model model){Job j=ownedJob(id,auth);model.addAttribute("job",j);model.addAttribute("editing",true);return "customer/job-form";}
    @PostMapping("/jobs/{id}") public String updateJob(@PathVariable Long id,Authentication auth,@ModelAttribute Job form,RedirectAttributes ra){try{Job j=ownedJob(id,auth);if(j.getStatus()==JobStatus.CLOSED||j.getStatus()==JobStatus.CANCELLED)throw new IllegalArgumentException("Không thể sửa công việc đã đóng/hủy.");j.setTitle(form.getTitle());j.setCategory(form.getCategory());j.setDescription(form.getDescription());j.setRequiredSkills(form.getRequiredSkills());j.setBudget(form.getBudget());j.setPriceType(form.getPriceType());j.setDeadline(form.getDeadline());j.setWorkType(form.getWorkType());j.setExperienceLevel(form.getExperienceLevel());j.setAcceptanceCriteria(form.getAcceptanceCriteria());j.setEstimatedDurationDays(form.getEstimatedDurationDays());validateJob(j);jobs.save(j);ra.addFlashAttribute("success","Cập nhật công việc thành công.");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/jobs";}
    @PostMapping("/jobs/{id}/status") public String jobStatus(@PathVariable Long id,@RequestParam JobStatus status,Authentication auth,RedirectAttributes ra){try{Job j=ownedJob(id,auth);if(status==JobStatus.CLOSED&&jobs.applicants(j).stream().noneMatch(a->a.getStatus()==ApplicationStatus.ACCEPTED))throw new IllegalArgumentException("Hãy chọn Freelancer trước khi đóng tuyển theo luồng dự án, hoặc dùng PAUSED/CANCELLED.");j.setStatus(status);jobs.save(j);ra.addFlashAttribute("success","Đã cập nhật trạng thái công việc.");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/jobs";}
    @GetMapping("/jobs/{id}/applicants") public String applicants(@PathVariable Long id,Authentication auth,Model model){Job j=ownedJob(id,auth);model.addAttribute("job",j);model.addAttribute("applications",jobs.applicants(j));return "customer/applicants";}
    @PostMapping("/applications/{id}/shortlist") public String shortlist(@PathVariable Long id,@RequestParam boolean value,Authentication auth,RedirectAttributes ra){try{projects.shortlist(id,users.byEmail(auth.getName()),value);ra.addFlashAttribute("success",value?"Đã đưa ứng viên vào danh sách ưu tiên.":"Đã từ chối ứng viên.");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/customer/jobs";}
    @PostMapping("/applications/{id}/accept") public String accept(@PathVariable Long id,Authentication auth,RedirectAttributes ra){try{Project p=projects.acceptApplication(id,users.byEmail(auth.getName()));ra.addFlashAttribute("success","Đã chọn Freelancer. Dự án đã được tạo và sẵn sàng thanh toán.");return "redirect:/projects/"+p.getId();}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());return "redirect:/customer/jobs";}}
    @GetMapping("/projects") public String projects(Authentication auth,Model model){model.addAttribute("projects",projects.forCustomer(users.byEmail(auth.getName())));return "customer/projects";}
    @GetMapping("/history") public String history(Authentication auth,Model model){User u=users.byEmail(auth.getName());model.addAttribute("jobs",jobRepository.findByCustomerOrderByCreatedAtDesc(u));model.addAttribute("projects",projects.forCustomer(u));model.addAttribute("payments",payments.history(u));return "customer/history";}

    private Job ownedJob(Long id,Authentication auth){Job j=jobs.get(id);User u=users.byEmail(auth.getName());if(!j.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Bạn không sở hữu công việc này.");return j;}
    private void validateJob(Job j){if(j.getTitle()==null||j.getTitle().trim().length()<5)throw new IllegalArgumentException("Tên công việc phải có ít nhất 5 ký tự.");if(j.getCategory()==null||j.getCategory().isBlank())throw new IllegalArgumentException("Vui lòng chọn lĩnh vực.");if(j.getDescription()==null||j.getDescription().trim().length()<30)throw new IllegalArgumentException("Mô tả công việc nên có ít nhất 30 ký tự.");if(j.getBudget()==null||j.getBudget().signum()<=0)throw new IllegalArgumentException("Ngân sách phải lớn hơn 0.");if(j.getDeadline()!=null&&j.getDeadline().isBefore(LocalDate.now()))throw new IllegalArgumentException("Deadline không được ở quá khứ.");}
    private void addCharts(Model model,User u,LocalDate from,LocalDate to,String metric,String pieMode){List<ChartPoint> bars=dashboard.bar(u,from,to,metric);double max=bars.stream().mapToDouble(ChartPoint::value).max().orElse(1);model.addAttribute("barData",bars);model.addAttribute("barMax",max<=0?1:max);List<PieSlice> pie=dashboard.pie(u,from,to,pieMode);model.addAttribute("pieData",pie);model.addAttribute("pieGradient",gradient(pie));model.addAttribute("from",from);model.addAttribute("to",to);model.addAttribute("metric",metric);model.addAttribute("pieMode",pieMode);}
    private String gradient(List<PieSlice>s){if(s.isEmpty())return "conic-gradient(#eef0f7 0 100%)";String[]c={"#6c3bff","#2f6fed","#13bfa6","#ffb020","#f0447d","#7b8aa4"};double start=0;StringBuilder b=new StringBuilder("conic-gradient(");for(int i=0;i<s.size();i++){double end=start+s.get(i).percent();if(i>0)b.append(',');b.append(c[i%c.length]).append(' ').append(String.format(java.util.Locale.US,"%.2f",start)).append("% ").append(String.format(java.util.Locale.US,"%.2f",end)).append('%');start=end;}return b.append(')').toString();}
}
