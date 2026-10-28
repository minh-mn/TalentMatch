package com.talentmatch.controller;

import com.talentmatch.dto.ChartPoint;
import com.talentmatch.dto.PieSlice;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

/** Workspace ADMIN - tách quyền bởi SecurityConfig /admin/** và ràng buộc nghiệp vụ tại từng action. */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private final UserRepository users;
    private final JobRepository jobs;
    private final ProjectRepository projects;
    private final PaymentRepository payments;
    private final TalentReportRepository reports;
    private final AdminAuditLogRepository audits;
    private final DashboardService dashboard;

    public AdminController(UserRepository users, JobRepository jobs, ProjectRepository projects,
                           PaymentRepository payments, TalentReportRepository reports,
                           AdminAuditLogRepository audits, DashboardService dashboard){
        this.users=users; this.jobs=jobs; this.projects=projects; this.payments=payments;
        this.reports=reports; this.audits=audits; this.dashboard=dashboard;
    }

    private String norm(String text){return text==null?"":text.strip().toLowerCase(Locale.ROOT);}
    private boolean contains(String haystack,String search){return norm(haystack).contains(norm(search));}
    private void paginate(Model m,List<?> data,Integer page){
        int total=data.size();int size=12;int pages=Math.max(1,(total+size-1)/size);
        int current=Math.min(pages,Math.max(1,page==null?1:page));
        int from=(current-1)*size;
        m.addAttribute("rows",data.subList(Math.min(from,total),Math.min(from+size,total)));
        m.addAttribute("page",current);m.addAttribute("pages",pages);
        m.addAttribute("total",total);m.addAttribute("hasPrev",current>1);m.addAttribute("hasNext",current<pages);
    }
    private void audit(Authentication actor,String action,String target,Long id,String details){
        if(actor==null)throw new IllegalArgumentException("Bạn cần đăng nhập Admin.");
        User admin=users.findByEmailIgnoreCase(actor.getName()).orElseThrow();
        if(admin.getRole()!=Role.ADMIN)throw new IllegalArgumentException("Chỉ Admin có quyền kiểm duyệt.");
        AdminAuditLog log=new AdminAuditLog();log.setActorId(admin.getId());
        log.setActorEmail(admin.getEmail());log.setAction(action);log.setTargetType(target);
        log.setTargetId(id);log.setDetails(details==null?"":details.substring(0,Math.min(details.length(),600)));
        audits.save(log);
    }
    private void flash(RedirectAttributes ra,Exception e){
        // Những thao tác có ghi nhiều bảng phải cùng rollback khi nhật ký không lưu được.
        try{TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();}catch(org.springframework.transaction.NoTransactionException ignored){}
        ra.addFlashAttribute("error",e.getMessage()==null?"Không thể xử lý yêu cầu.":e.getMessage());
    }

    @GetMapping public String root(){return "redirect:/admin/dashboard";}

    @GetMapping("/dashboard")
    public String dashboard(Model m,
                            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue="projects") String metric,
                            @RequestParam(defaultValue="status") String pieMode){
        var range=dashboard.normalizeRange(from,to);
        metric="revenue".equals(metric)?"revenue":"projects";
        pieMode="category".equals(pieMode)?"category":"status";
        User admin=users.findByRoleOrderByCreatedAtDesc(Role.ADMIN).stream().findFirst().orElse(null);
        List<ChartPoint> bars=dashboard.bar(admin,range.from(),range.to(),metric);
        List<PieSlice> pie=dashboard.pie(admin,range.from(),range.to(),pieMode);
        double max=bars.stream().mapToDouble(ChartPoint::value).max().orElse(1.0);
        List<User> latestUsers=users.findAll().stream().filter(u->u.getCreatedAt()!=null)
            .sorted(Comparator.comparing(User::getCreatedAt).reversed()).limit(7).toList();
        Map<String,Object> stats=new LinkedHashMap<>(dashboard.adminStats());
        stats.put("freelancers",users.findByRoleOrderByCreatedAtDesc(Role.FREELANCER).size());
        stats.put("customers",users.findByRoleOrderByCreatedAtDesc(Role.CUSTOMER).size());
        stats.put("openJobs",jobs.countByStatus(JobStatus.OPEN));
        stats.put("disputes",projects.countByStatus(ProjectStatus.DISPUTED));
        stats.put("pendingReports",reports.findAll().stream().filter(r->"PENDING".equals(r.getStatus())).count());
        stats.put("completed",projects.countByStatus(ProjectStatus.COMPLETED));
        stats.put("locked",users.findAll().stream().filter(u->Boolean.TRUE.equals(u.getLocked())).count());
        m.addAttribute("stats",stats);m.addAttribute("latestUsers",latestUsers);
        m.addAttribute("latestReports",reports.findAllByOrderByCreatedAtDesc().stream().limit(4).toList());
        m.addAttribute("barData",bars);m.addAttribute("barMax",max<=0?1:max);
        m.addAttribute("pieProjectCount",pie.stream().mapToLong(PieSlice::count).sum());
        m.addAttribute("pieData",pie);m.addAttribute("pieGradient",gradient(pie));
        m.addAttribute("from",range.from());m.addAttribute("to",range.to());m.addAttribute("metric",metric);m.addAttribute("pieMode",pieMode);
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(Model m,@RequestParam(required=false)String q,@RequestParam(required=false)Role role,
                        @RequestParam(defaultValue="all")String state,@RequestParam(defaultValue="1")Integer page){
        Predicate<User> byState=u->switch(state){
            case "locked"->Boolean.TRUE.equals(u.getLocked());
            case "active"->Boolean.TRUE.equals(u.getEnabled())&&!Boolean.TRUE.equals(u.getLocked());
            case "unverified"->!Boolean.TRUE.equals(u.getEnabled());
            default->true;
        };
        var all=users.findAll().stream().filter(u->role==null||u.getRole()==role)
            .filter(byState).filter(u->contains(u.getFullName()+" "+u.getEmail()+" "+u.getCompanyName(),q))
            .sorted(Comparator.comparing(User::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        paginate(m,all,page);m.addAttribute("q",q);m.addAttribute("role",role);m.addAttribute("state",state);
        return "admin/users";
    }

    @Transactional
    @PostMapping("/users/{id}/lock")
    public String lock(@PathVariable Long id,@RequestParam boolean locked,Authentication actor,RedirectAttributes ra){
        try{
            User u=users.findById(id).orElseThrow(()->new IllegalArgumentException("Không có tài khoản."));
            if(u.getRole()==Role.ADMIN)throw new IllegalArgumentException("Không thể khóa hoặc mở khóa tài khoản Admin.");
            u.setLocked(locked);users.save(u);
            audit(actor,locked?"LOCK_USER":"UNLOCK_USER","USER",id, u.getEmail());
            ra.addFlashAttribute("success",locked?"Đã khóa tài khoản.":"Đã mở khóa tài khoản.");
        }catch(Exception e){flash(ra,e);}
        return "redirect:/admin/users";
    }

    @GetMapping("/jobs")
    public String jobs(Model m,@RequestParam(required=false)String q,@RequestParam(required=false)JobStatus status,
                       @RequestParam(defaultValue="1")Integer page){
        var all=jobs.findAll().stream().filter(j->status==null||j.getStatus()==status)
            .filter(j->contains(j.getTitle()+" "+j.getCategory()+" "+j.getCustomer().getFullName()+" "+j.getCustomer().getCompanyName(),q))
            .sorted(Comparator.comparing(Job::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        paginate(m,all,page);m.addAttribute("q",q);m.addAttribute("status",status);
        m.addAttribute("openCount",jobs.countByStatus(JobStatus.OPEN));
        return "admin/jobs";
    }

    @Transactional
    @PostMapping("/jobs/{id}/status")
    public String jobStatus(@PathVariable Long id,@RequestParam JobStatus status,Authentication actor,RedirectAttributes ra){
        try{
            Job j=jobs.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy công việc."));
            JobStatus old=j.getStatus();
            if(status==JobStatus.PAUSED && old!=JobStatus.OPEN)throw new IllegalArgumentException("Chỉ tạm ẩn công việc đang mở.");
            if(status==JobStatus.OPEN && old!=JobStatus.PAUSED)throw new IllegalArgumentException("Chỉ mở lại công việc tạm ẩn.");
            if(status!=JobStatus.OPEN && status!=JobStatus.PAUSED)throw new IllegalArgumentException("Admin chỉ được tạm ẩn hoặc mở lại tin; Client quản lý đóng/hủy tin.");
            j.setStatus(status);if(status==JobStatus.PAUSED)j.setFeatured(false);jobs.save(j);
            audit(actor,"JOB_STATUS","JOB",id,old+" -> "+status);
            ra.addFlashAttribute("success",status==JobStatus.PAUSED?"Đã tạm ẩn tin tuyển dụng.":"Đã mở lại tin tuyển dụng.");
        }catch(Exception e){flash(ra,e);}
        return "redirect:/admin/jobs";
    }

    @Transactional
    @PostMapping("/jobs/{id}/flags")
    public String jobFlags(@PathVariable Long id,@RequestParam(defaultValue="false") boolean featured,
                           @RequestParam(defaultValue="false") boolean verified,Authentication actor,RedirectAttributes ra){
        try{
            Job job=jobs.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy tin."));
            if(job.getStatus()!=JobStatus.OPEN&&featured)throw new IllegalArgumentException("Tin nổi bật phải đang mở.");
            job.setFeatured(featured);job.setVerified(verified);jobs.save(job);
            audit(actor,"JOB_MODERATION","JOB",id,"Nổi bật: "+featured+", duyệt nội dung: "+verified);
            ra.addFlashAttribute("success","Đã cập nhật trạng thái kiểm duyệt tin.");
        }catch(Exception e){flash(ra,e);}
        return "redirect:/admin/jobs";
    }

    @GetMapping("/projects")
    public String projects(Model m,@RequestParam(required=false)String q,@RequestParam(required=false)ProjectStatus status,
                           @RequestParam(defaultValue="1")Integer page){
        var all=projects.findAll().stream().filter(p->status==null||p.getStatus()==status)
            .filter(p->contains(p.getApplication().getJob().getTitle()+" "+p.getCustomer().getFullName()+" "+p.getFreelancer().getFullName(),q))
            .sorted(Comparator.comparing(Project::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        paginate(m,all,page);m.addAttribute("q",q);m.addAttribute("status",status);
        return "admin/projects";
    }

    @GetMapping("/payments")
    public String payments(Model m,@RequestParam(required=false)String q,@RequestParam(required=false)PaymentStatus status,
                           @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,
                           @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,
                           @RequestParam(defaultValue="1")Integer page){
        var all=payments.findAll().stream().filter(p->status==null||p.getStatus()==status)
            .filter(p->contains(p.getTransactionCode()+" "+p.getPayer().getEmail()+" "+p.getProject().getApplication().getJob().getTitle(),q))
            .filter(p->p.getCreatedAt()!=null&&(from==null||!p.getCreatedAt().toLocalDate().isBefore(from))
                &&(to==null||!p.getCreatedAt().toLocalDate().isAfter(to)))
            .sorted(Comparator.comparing(Payment::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        BigDecimal paid=all.stream().filter(p->p.getStatus()==PaymentStatus.PAID||p.getStatus()==PaymentStatus.DISBURSED)
            .map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal disbursed=all.stream().filter(p->p.getStatus()==PaymentStatus.DISBURSED)
            .map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        paginate(m,all,page);m.addAttribute("q",q);m.addAttribute("status",status);
        m.addAttribute("from",from);m.addAttribute("to",to);
        m.addAttribute("paidTotal",paid);m.addAttribute("disbursedTotal",disbursed);
        return "admin/payments";
    }

    @GetMapping("/reports")
    public String reports(Model m,@RequestParam(required=false)String q,@RequestParam(defaultValue="all")String status,
                          @RequestParam(defaultValue="1")Integer page){
        var all=reports.findAllByOrderByCreatedAtDesc().stream()
            .filter(r->"all".equals(status)||r.getStatus().equals(status))
            .filter(r->contains(r.getReason()+" "+r.getDescription()+" "+r.getReporter().getFullName()+" "+r.getReportedUser().getFullName(),q)).toList();
        paginate(m,all,page);m.addAttribute("q",q);m.addAttribute("status",status);
        m.addAttribute("pendingCount",reports.findAll().stream().filter(r->"PENDING".equals(r.getStatus())).count());
        return "admin/reports";
    }

    @Transactional
    @PostMapping("/reports/{id}/review")
    public String reviewReport(@PathVariable Long id,@RequestParam String status,
                               @RequestParam(required=false)String note,Authentication actor,RedirectAttributes ra){
        try{
            if(!Set.of("REVIEWED","RESOLVED","REJECTED").contains(status))throw new IllegalArgumentException("Trạng thái xử lý không hợp lệ.");
            if(!"REVIEWED".equals(status)&&(note==null||note.strip().length()<5))
                throw new IllegalArgumentException("Cần ghi chú ít nhất 5 ký tự khi kết luận báo cáo.");
            TalentReport r=reports.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy báo cáo."));
            if("RESOLVED".equals(r.getStatus())||"REJECTED".equals(r.getStatus()))
                throw new IllegalArgumentException("Báo cáo đã kết luận; không chỉnh lại trạng thái.");
            r.setStatus(status);r.setAdminNote(note==null?null:note.strip());reports.save(r);
            audit(actor,"REPORT_REVIEW","REPORT",id,status+": "+note);
            ra.addFlashAttribute("success","Đã lưu kết quả xử lý báo cáo.");
        }catch(Exception e){flash(ra,e);}
        return "redirect:/admin/reports";
    }

    @GetMapping("/audit")
    public String auditLog(Model m,@RequestParam(defaultValue="1")Integer page){
        var all=audits.findAll().stream()
            .sorted(Comparator.comparing(AdminAuditLog::getCreatedAt,Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        paginate(m,all,page);return "admin/audit";
    }

    private String gradient(List<PieSlice> slices){
        if(slices.isEmpty())return "conic-gradient(#edf0fb 0 100%)";
        String[] colors={"#7645ed","#3980f1","#17b89e","#f7b042","#fb7185","#64748b"};
        double start=0;StringBuilder b=new StringBuilder("conic-gradient(");
        for(int i=0;i<slices.size();i++){
            double end=start+slices.get(i).percent();
            if(i>0)b.append(',');
            b.append(colors[i%colors.length]).append(' ').append(String.format(Locale.US,"%.2f",start))
               .append("% ").append(String.format(Locale.US,"%.2f",end)).append('%');start=end;
        }
        return b.append(')').toString();
    }
}
