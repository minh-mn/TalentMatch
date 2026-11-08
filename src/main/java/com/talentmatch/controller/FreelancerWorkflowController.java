package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.PaymentRepository;
import com.talentmatch.service.AgreementService;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import com.talentmatch.repository.TalentReportRepository;
import com.talentmatch.service.JobService;
import com.talentmatch.service.ProjectService;
import com.talentmatch.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Các mục Sidebar Freelancer dùng dữ liệu thật và liên kết đến nghiệp vụ đang có. */
@Controller
@RequestMapping("/freelancer")
public class FreelancerWorkflowController {
    private final com.talentmatch.service.WalletDemoService wallet;
    private final AgreementService agreements;
    private final JobService jobs;
    private final ProjectService projects;
    private final UserService users;
    private final PaymentRepository payments;
    private final TalentReportRepository reports;

    public FreelancerWorkflowController(AgreementService agreements, JobService jobs, ProjectService projects, UserService users,
                                       PaymentRepository payments, TalentReportRepository reports, com.talentmatch.service.WalletDemoService wallet) {
        this.wallet=wallet;this.agreements=agreements;this.jobs=jobs; this.projects=projects; this.users=users; this.payments=payments; this.reports=reports;
    }


    @GetMapping("/contracts") @Transactional(readOnly = true)
    public String contracts(Authentication auth, Model model,
        @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="all") String status,
        @RequestParam(defaultValue="newest") String sort, @RequestParam(required=false) Long selected){
        User u=users.byEmail(auth.getName());
        List<Project> all=projects.forFreelancer(u);
        Map<Long,AgreementRevision> snapshots=new LinkedHashMap<>();
        for(Project p:all){AgreementRevision revision=agreements.current(p);if(revision!=null)snapshots.put(p.getId(),revision);}
        List<Project> filtered=new ArrayList<>();
        String keyword=q.trim().toLowerCase(Locale.ROOT);
        for(Project p:all){
            if(!keyword.isEmpty() && !(p.getApplication().getJob().getTitle()+" "+p.getCustomer().getFullName()+" TM-CT-"+p.getId()).toLowerCase(Locale.ROOT).contains(keyword))continue;
            AgreementRevision current=snapshots.get(p.getId());
            boolean both=current!=null&&current.getCustomerSignedAt()!=null&&current.getFreelancerSignedAt()!=null;
            String stage=both?"signed":current==null||current.getFreelancerSignedAt()==null?"my-signature":"partner-signature";
            if(!status.equals("all")&&!status.equals(stage)&&!(status.equals("completed")&&p.getStatus()==ProjectStatus.COMPLETED))continue;
            filtered.add(p);
        }
        filtered.sort(Comparator.comparing(Project::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())));
        if(!"oldest".equals(sort))java.util.Collections.reverse(filtered);
        Project active=null;
        if(selected!=null)for(Project p:filtered)if(p.getId().equals(selected)){active=p;break;}
        model.addAttribute("section","contracts");model.addAttribute("pageTitle","Hợp đồng & Điều khoản");
        model.addAttribute("currentUser",u);model.addAttribute("ownProjects",filtered);model.addAttribute("agreementsByProject",snapshots);
        model.addAttribute("selectedProject",active);model.addAttribute("selectedAgreement",active==null?null:snapshots.get(active.getId()));
        model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("sort",sort);
        model.addAttribute("contractTotal",all.size());
        model.addAttribute("mySignCount",all.stream().filter(p->{AgreementRevision a=snapshots.get(p.getId());return a!=null&&a.getFreelancerSignedAt()==null&&p.getStatus()!=ProjectStatus.CANCELLED;}).count());
        model.addAttribute("partnerSignCount",all.stream().filter(p->{AgreementRevision a=snapshots.get(p.getId());return a!=null&&a.getFreelancerSignedAt()!=null&&a.getCustomerSignedAt()==null;}).count());
        model.addAttribute("signedCount",all.stream().filter(p->{AgreementRevision a=snapshots.get(p.getId());return a!=null&&a.getFreelancerSignedAt()!=null&&a.getCustomerSignedAt()!=null;}).count());
        model.addAttribute("completedCount",all.stream().filter(p->p.getStatus()==ProjectStatus.COMPLETED).count());
        return "freelancer/contracts";
    }

    @GetMapping("/deliveries") @Transactional(readOnly = true)
    public String deliveries(Authentication auth, Model model,
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="all") String status,
            @RequestParam(defaultValue="newest") String sort,
            @RequestParam(defaultValue="all") String type,
            @RequestParam(required=false) Long selected) {
        User freelancer=users.byEmail(auth.getName());
        List<Project> all=projects.forFreelancer(freelancer);
        Map<Long,List<Milestone>> byProject=new LinkedHashMap<>();
        Map<Long,Integer> reported=new LinkedHashMap<>(), accepted=new LinkedHashMap<>(), milestoneCounts=new LinkedHashMap<>();
        long working=0, ready=0, waiting=0, changes=0;
        for(Project project:all){
            List<Milestone> stages=projects.milestones(project);
            byProject.put(project.getId(),stages);
            milestoneCounts.put(project.getId(),stages.size());
            // KPI đếm dự án thay vì cộng số mốc (khớp với danh sách được lọc).
            if(stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.IN_PROGRESS && m.getProgressPercent()<100 && (m.getRevisionNote()==null || m.getRevisionNote().isBlank()))
                 || stages.isEmpty() && project.getStatus()==ProjectStatus.IN_PROGRESS)working++;
            if(stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.IN_PROGRESS && m.getProgressPercent()==100))ready++;
            if(stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.SUBMITTED)
                 || stages.isEmpty() && project.getStatus()==ProjectStatus.WAITING_ACCEPTANCE)waiting++;
            if(stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.IN_PROGRESS && m.getRevisionNote()!=null && !m.getRevisionNote().isBlank()))changes++;
            reported.put(project.getId(),com.talentmatch.service.ProgressMetrics.reported(stages));
            accepted.put(project.getId(),com.talentmatch.service.ProgressMetrics.accepted(stages));
        }
        String term=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        List<Project> filtered=new ArrayList<>();
        for(Project project:all){
            if(!term.isEmpty()&&!(project.getApplication().getJob().getTitle()+" "+project.getCustomer().getFullName()+" TM-PJ-"+project.getId()).toLowerCase(Locale.ROOT).contains(term))continue;
            if("milestone".equals(type)&&byProject.get(project.getId()).isEmpty())continue;
            if("fixed".equals(type)&&!byProject.get(project.getId()).isEmpty())continue;
            List<Milestone> stages=byProject.get(project.getId());
            boolean matches=switch(status){
                case "working" -> stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.IN_PROGRESS && m.getProgressPercent()<100 && (m.getRevisionNote()==null || m.getRevisionNote().isBlank())) || (stages.isEmpty() && project.getStatus()==ProjectStatus.IN_PROGRESS);
                case "ready" -> stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.IN_PROGRESS&&m.getProgressPercent()==100);
                case "waiting" -> stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.SUBMITTED) || (stages.isEmpty() && project.getStatus()==ProjectStatus.WAITING_ACCEPTANCE);
                case "changes" -> stages.stream().anyMatch(m->m.getStatus()==MilestoneStatus.IN_PROGRESS&&m.getRevisionNote()!=null&&!m.getRevisionNote().isBlank());
                default -> true;
            };
            if(matches)filtered.add(project);
        }
        filtered.sort(Comparator.comparing(Project::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())));
        if(!"oldest".equals(sort))java.util.Collections.reverse(filtered);
        Project active=null;
        if(selected!=null)for(Project project:filtered)if(project.getId().equals(selected)){active=project;break;}
        model.addAttribute("section","deliveries");model.addAttribute("pageTitle","Tiến độ & Bàn giao");
        model.addAttribute("currentUser",freelancer);model.addAttribute("ownProjects",filtered);
        model.addAttribute("selectedProject",active);model.addAttribute("selectedMilestones",active==null?List.of():byProject.get(active.getId()));
        model.addAttribute("reported",reported);model.addAttribute("accepted",accepted);
        model.addAttribute("milestoneCounts",milestoneCounts);
        model.addAttribute("progressMetricNote","Tiến độ theo tỷ trọng giá trị mỗi mốc; đã nghiệm thu chỉ tính mốc được Client chấp nhận.");
        model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("sort",sort);model.addAttribute("type",type);
        model.addAttribute("workingCount",working);model.addAttribute("readyCount",ready);
        model.addAttribute("waitingCount",waiting);model.addAttribute("changesCount",changes);
        return "freelancer/progress-deliveries";
    }

    /** Tổng hợp tiền theo dự án từ giao dịch thành công và bút toán ví (không cộng giao dịch chờ/thất bại). */
    public record ProjectFinance(java.math.BigDecimal paidHeld, java.math.BigDecimal disbursedGross,
                                 java.math.BigDecimal fee, java.math.BigDecimal net) {}

    @GetMapping("/earnings") @Transactional
    public String earnings(Authentication auth, Model model,
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="all") String status,
            @RequestParam(defaultValue="all") String project,
            @RequestParam(defaultValue="newest") String sort,
            @RequestParam(defaultValue="projects") String tab) {
        User freelancer=users.byEmail(auth.getName());
        wallet.sync(freelancer);
        var walletSummary=wallet.summary(freelancer);
        model.addAttribute("walletSummary",walletSummary);
        model.addAttribute("withdrawEnabled",walletSummary.available().compareTo(new BigDecimal("10000"))>=0);
        model.addAttribute("withdrawMax",walletSummary.available().max(BigDecimal.ZERO).toBigInteger().toString());
        model.addAttribute("walletBanks",wallet.banks(freelancer));
        List<WalletWithdrawal> allWithdrawals=wallet.withdrawals(freelancer);
        List<WalletWithdrawal> filteredWithdrawals=new ArrayList<>();
        for(WalletWithdrawal withdrawal:allWithdrawals){
            if("withdrawals".equals(tab)){
                if(!"all".equals(status)&&!withdrawal.getStatus().name().equals(status))continue;
                String needle=q==null?"":q.trim().toLowerCase(Locale.ROOT);
                String bankText=withdrawal.getBank()==null?"":withdrawal.getBank().getBankName()+" "+withdrawal.getBank().getMasked();
                String haystack=("WD-"+withdrawal.getId()+" "+bankText).toLowerCase(Locale.ROOT);
                if(!needle.isBlank()&&!haystack.contains(needle))continue;
            }
            filteredWithdrawals.add(withdrawal);
        }
        filteredWithdrawals.sort(Comparator.comparing(WalletWithdrawal::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())));
        if(!"oldest".equals(sort))java.util.Collections.reverse(filteredWithdrawals);
        model.addAttribute("walletWithdrawals",filteredWithdrawals);
        List<Project> allProjects=projects.forFreelancer(freelancer);
        List<Payment> allPayments=new ArrayList<>();
        for(Project item:allProjects) allPayments.addAll(payments.findByProjectOrderByCreatedAtDesc(item));
        Map<Long,ProjectFinance> projectFinance=new LinkedHashMap<>();
        for(Project p:allProjects){
            BigDecimal paidHeld=BigDecimal.ZERO, disbursed=BigDecimal.ZERO;
            for(Payment item:payments.findByProjectOrderByCreatedAtDesc(p)){
                if(item.getAmount()==null)continue;
                if(item.getStatus()==PaymentStatus.PAID)paidHeld=paidHeld.add(item.getAmount());
                if(item.getStatus()==PaymentStatus.DISBURSED)disbursed=disbursed.add(item.getAmount());
            }
            BigDecimal fee=BigDecimal.ZERO, net=BigDecimal.ZERO;
            for(WalletCredit credit:wallet.creditsForProject(freelancer,p)){
                fee=fee.add(credit.getFee());net=net.add(credit.getNet());
            }
            projectFinance.put(p.getId(),new ProjectFinance(paidHeld,disbursed,fee,net));
        }
        model.addAttribute("projectFinance",projectFinance);
        BigDecimal released=BigDecimal.ZERO, held=BigDecimal.ZERO;
        for(Payment item:allPayments){
            if(item.getAmount()==null)continue;
            if(item.getStatus()==PaymentStatus.DISBURSED)released=released.add(item.getAmount());
            if(item.getStatus()==PaymentStatus.PAID)held=held.add(item.getAmount());
        }
        String keyword=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        List<Payment> rows=new ArrayList<>();
        for(Payment item:allPayments){
            if(!"all".equals(project) && !String.valueOf(item.getProject().getId()).equals(project))continue;
            if("transactions".equals(tab) && !"all".equals(status) && !item.getStatus().name().equals(status))continue;
            String search=(String.valueOf(item.getTransactionCode())+" "+item.getProject().getApplication().getJob().getTitle()+" TM-PJ-"+item.getProject().getId()+" "+item.getProject().getCustomer().getFullName()).toLowerCase(Locale.ROOT);
            if(!keyword.isBlank()&&!search.contains(keyword))continue;
            rows.add(item);
        }
        rows.sort(Comparator.comparing(Payment::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())));
        if(!"oldest".equals(sort))java.util.Collections.reverse(rows);
        List<Project> visibleProjects=new ArrayList<>();
        for(Project item:allProjects){
            if(!"all".equals(project)&&!String.valueOf(item.getId()).equals(project))continue;
            if(!keyword.isBlank()&&!(item.getApplication().getJob().getTitle()+" TM-PJ-"+item.getId()+" "+item.getCustomer().getFullName()).toLowerCase(Locale.ROOT).contains(keyword))continue;
            if("projects".equals(tab)&&!"all".equals(status)&&!item.getStatus().name().equals(status))continue;
            visibleProjects.add(item);
        }
        visibleProjects.sort(Comparator.comparing(Project::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())));
        if(!"oldest".equals(sort))java.util.Collections.reverse(visibleProjects);
        model.addAttribute("currentUser",freelancer);
        model.addAttribute("allProjects",allProjects);
        model.addAttribute("ownProjects",visibleProjects);
        model.addAttribute("payments",rows);
        model.addAttribute("disbursedGross",released);
        model.addAttribute("heldGross",held);
        model.addAttribute("paymentCount",allPayments.size());
        model.addAttribute("q",q);model.addAttribute("status",status);
        model.addAttribute("projectFilter",project);model.addAttribute("sort",sort);
        model.addAttribute("tab",java.util.Set.of("projects","transactions","withdrawals","banks").contains(tab)?tab:"projects");
        return "freelancer/earnings";
    }

    /** Dedicated finance detail: owner-only, does not expose the legacy project dashboard. */
    @GetMapping("/earnings/projects/{id}")
    @Transactional(readOnly = true)
    public String earningsProject(@org.springframework.web.bind.annotation.PathVariable Long id,
                                  Authentication auth, Model model) {
        User freelancer = users.byEmail(auth.getName());
        Project p = projects.get(id);
        if (!p.getFreelancer().getId().equals(freelancer.getId()))
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem dữ liệu tài chính dự án này.");
        List<Payment> tx = payments.findByProjectOrderByCreatedAtDesc(p);
        BigDecimal paid = BigDecimal.ZERO, released = BigDecimal.ZERO;
        for (Payment payment : tx) {
            if (payment.getAmount() == null) continue;
            if (payment.getStatus() == PaymentStatus.PAID) paid = paid.add(payment.getAmount());
            if (payment.getStatus() == PaymentStatus.DISBURSED) released = released.add(payment.getAmount());
        }
        model.addAttribute("currentUser", freelancer);
        model.addAttribute("project", p);
        model.addAttribute("agreement", agreements.current(p));
        model.addAttribute("milestones", projects.milestones(p));
        model.addAttribute("payments", tx);
        model.addAttribute("heldGross", paid);
        model.addAttribute("releasedGross", released);
        return "freelancer/earnings-project-detail";
    }

    @GetMapping("/support") @Transactional(readOnly = true)
    public String support(Authentication auth, Model model) { return screen("support", auth, model); }

    private String screen(String section, Authentication auth, Model model) {
        User freelancer = users.byEmail(auth.getName());
        List<Project> ownProjects = projects.forFreelancer(freelancer);
        model.addAttribute("section", section);
        model.addAttribute("pageTitle", switch (section) {
            case "quotes" -> "Báo giá & Thương lượng";
            case "contracts" -> "Hợp đồng & Điều khoản";
            case "deliveries" -> "Tiến độ & Bàn giao";
            case "earnings" -> "Thanh toán & Thu nhập";
            default -> "Khiếu nại & Hỗ trợ";
        });
        model.addAttribute("ownProjects", ownProjects);
        if ("quotes".equals(section)) {
            model.addAttribute("applications", jobs.applications(freelancer));
        } else if ("deliveries".equals(section)) {
            List<DeliveryRow> rows = new ArrayList<>();
            for (Project p : ownProjects) {
                List<Milestone> stages = projects.milestones(p);
                long done = stages.stream().filter(m -> m.getStatus() == MilestoneStatus.COMPLETED).count();
                rows.add(new DeliveryRow(p, stages.size(), done,
                        stages.isEmpty() ? 0 : (int) Math.round(100.0 * done / stages.size())));
            }
            model.addAttribute("deliveryRows", rows);
        } else if ("earnings".equals(section)) {
            List<Payment> history = new ArrayList<>();
            for (Project p : ownProjects) history.addAll(payments.findByProjectOrderByCreatedAtDesc(p));
            history.sort(Comparator.comparing(Payment::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            BigDecimal income = history.stream().filter(p -> p.getStatus() == PaymentStatus.DISBURSED)
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pending = history.stream().filter(p -> p.getStatus() == PaymentStatus.PAID)
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("payments", history);
            model.addAttribute("disbursedTotal", income);
            model.addAttribute("pendingTotal", pending);
        } else if ("support".equals(section)) {
            model.addAttribute("myReports", reports.findByReporterOrderByCreatedAtDesc(freelancer));
        }
        return "freelancer/workflow-v104";
    }

    public record DeliveryRow(Project project, int milestoneCount, long completedCount, int progress) { }
}
