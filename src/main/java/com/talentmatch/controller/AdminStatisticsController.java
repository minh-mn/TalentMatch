package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** Báo cáo quản trị đọc dữ liệu thật. Khoảng thời gian [từ, đến] bao gồm cả ngày kết thúc. */
@Controller
@RequestMapping("/admin/statistics")
public class AdminStatisticsController {
    private final UserRepository users;
    private final JobRepository jobs;
    private final ProjectRepository projects;
    private final PaymentRepository payments;
    private final WalletCreditRepository walletCredits;
    private final TalentReportRepository reports;

    public AdminStatisticsController(UserRepository users, JobRepository jobs, ProjectRepository projects,
                                     PaymentRepository payments, WalletCreditRepository walletCredits,
                                     TalentReportRepository reports) {
        this.users=users; this.jobs=jobs; this.projects=projects;
        this.payments=payments; this.walletCredits=walletCredits; this.reports=reports;
    }
    private record Range(LocalDate from, LocalDate to) {
        boolean includes(LocalDateTime value){return value!=null && !value.toLocalDate().isBefore(from) && !value.toLocalDate().isAfter(to);}
    }
    private Range range(LocalDate from, LocalDate to){
        LocalDate end=to==null?LocalDate.now():to;
        LocalDate start=from==null?end.minusMonths(5).withDayOfMonth(1):from;
        if(start.isAfter(end))throw new IllegalArgumentException("Ngày bắt đầu không được sau ngày kết thúc.");
        if(start.isBefore(end.minusYears(5)))throw new IllegalArgumentException("Chỉ hỗ trợ báo cáo trong tối đa 5 năm.");
        return new Range(start,end);
    }
    private BigDecimal sum(Collection<BigDecimal> amounts){return amounts.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static Map<String,Object> item(Object... kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
    private String paymentLabel(PaymentStatus status){return switch(status){
        case PENDING -> "Chờ xử lý";case PAID -> "Đã trả, đang giữ";case DISBURSED -> "Đã giải ngân";
        case FAILED -> "Thất bại";case REFUNDED -> "Đã hoàn tiền";};}
    private String projectLabel(ProjectStatus status){return switch(status){
        case WAITING_PAYMENT -> "Chờ thanh toán";case IN_PROGRESS -> "Đang thực hiện";
        case WAITING_ACCEPTANCE -> "Chờ nghiệm thu";case COMPLETED -> "Đã hoàn thành";
        case CANCELLED -> "Đã hủy";case DISPUTED -> "Tranh chấp";};}

    @GetMapping
    @Transactional(readOnly=true)
    public String view(Model model,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        Range r=range(from,to);
        var allUsers=users.findAll();var allProjects=projects.findAll();var allJobs=jobs.findAll();
        var allPayments=payments.findAll();var credits=walletCredits.findAll();var allReports=reports.findAll();
        var userRows=allUsers.stream().filter(x->r.includes(x.getCreatedAt())).toList();
        var projectRows=allProjects.stream().filter(x->r.includes(x.getCreatedAt())).toList();
        var jobRows=allJobs.stream().filter(x->r.includes(x.getCreatedAt())).toList();
        // Giao dịch được nhóm theo thời điểm phát sinh; số tiền đã thanh toán chỉ tính khi có paidAt.
        var transactionRows=allPayments.stream().filter(x->r.includes(x.getCreatedAt())).toList();
        var paidRows=allPayments.stream().filter(x->r.includes(x.getPaidAt()) && (x.getStatus()==PaymentStatus.PAID||x.getStatus()==PaymentStatus.DISBURSED)).toList();
        var creditRows=credits.stream().filter(x->r.includes(x.getCreatedAt())).toList();
        var reportRows=allReports.stream().filter(x->r.includes(x.getCreatedAt())).toList();
        BigDecimal collected=sum(paidRows.stream().map(Payment::getAmount).toList());
        BigDecimal fee=sum(creditRows.stream().map(WalletCredit::getFee).toList());
        BigDecimal net=sum(creditRows.stream().map(WalletCredit::getNet).toList());
        BigDecimal disbursedGross=sum(creditRows.stream().map(WalletCredit::getGross).toList());
        // Khoản còn giữ là ảnh chụp trạng thái tại hiện tại, KHÔNG giới hạn theo ngày phát sinh.
        BigDecimal currentlyHeld=sum(allPayments.stream().filter(x->x.getStatus()==PaymentStatus.PAID).map(Payment::getAmount).toList());
        model.addAttribute("from",r.from); model.addAttribute("to",r.to);
        model.addAttribute("userCount",userRows.size());model.addAttribute("newFreelancers",userRows.stream().filter(x->x.getRole()==Role.FREELANCER).count());
        model.addAttribute("newClients",userRows.stream().filter(x->x.getRole()==Role.CUSTOMER).count());
        model.addAttribute("jobCount",jobRows.size());model.addAttribute("projectCount",projectRows.size());
        model.addAttribute("completedProjects",projectRows.stream().filter(x->x.getStatus()==ProjectStatus.COMPLETED).count());
        model.addAttribute("disputes",projectRows.stream().filter(x->x.getStatus()==ProjectStatus.DISPUTED).count());
        model.addAttribute("transactionCount",transactionRows.size());model.addAttribute("reportCount",reportRows.size());
        model.addAttribute("collected",collected);model.addAttribute("fee",fee);model.addAttribute("net",net);
        model.addAttribute("disbursedGross",disbursedGross);model.addAttribute("currentlyHeld",currentlyHeld);
        model.addAttribute("inconsistentDisbursements",allPayments.stream().filter(x->x.getStatus()==PaymentStatus.DISBURSED && credits.stream().noneMatch(c->c.getPayment().getId().equals(x.getId()))).count());
        List<Map<String,Object>> statuses=new ArrayList<>();
        for(ProjectStatus status:ProjectStatus.values()){
            long count=projectRows.stream().filter(p->p.getStatus()==status).count();
            statuses.add(item("key",status.name(),"label",projectLabel(status),"count",count,
                "percent",projectRows.isEmpty()?0:Math.round(count*100.0/projectRows.size())));
        }
        model.addAttribute("projectStatuses",statuses);
        List<Map<String,Object>> paymentStatuses=new ArrayList<>();
        for(PaymentStatus status:PaymentStatus.values()){
            long count=transactionRows.stream().filter(p->p.getStatus()==status).count();
            paymentStatuses.add(item("key",status.name(),"label",paymentLabel(status),"count",count));
        }
        model.addAttribute("paymentStatuses",paymentStatuses);
        List<Map<String,Object>> timeline=new ArrayList<>();
        YearMonth cursor=YearMonth.from(r.from),last=YearMonth.from(r.to);
        while(!cursor.isAfter(last)){
            YearMonth month=cursor;
            long projectsInMonth=projectRows.stream().filter(x->YearMonth.from(x.getCreatedAt()).equals(month)).count();
            BigDecimal feeInMonth=sum(creditRows.stream().filter(x->YearMonth.from(x.getCreatedAt()).equals(month)).map(WalletCredit::getFee).toList());
            BigDecimal grossInMonth=sum(creditRows.stream().filter(x->YearMonth.from(x.getCreatedAt()).equals(month)).map(WalletCredit::getGross).toList());
            timeline.add(item("label",String.format("%02d/%d",month.getMonthValue(),month.getYear()),"projects",projectsInMonth,
                "fees",feeInMonth,"gross",grossInMonth));
            cursor=cursor.plusMonths(1);
        }
        long maxProjects=Math.max(1,timeline.stream().mapToLong(x->(Long)x.get("projects")).max().orElse(0));
        BigDecimal maxFees=timeline.stream().map(x->(BigDecimal)x.get("fees")).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        for(var x:timeline){
            x.put("projectHeight",Math.max(2,Math.round(((Long)x.get("projects"))*100.0/maxProjects)));
            x.put("feeHeight",maxFees.signum()==0?2:Math.max(2,((BigDecimal)x.get("fees")).multiply(BigDecimal.valueOf(100)).divide(maxFees,0,java.math.RoundingMode.HALF_UP).intValue()));
        }
        model.addAttribute("timeline",timeline);
        Map<String,Long> categories=jobRows.stream().collect(Collectors.groupingBy(x->x.getCategory()==null?"Khác":x.getCategory(),Collectors.counting()));
        model.addAttribute("categories",categories.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(6)
            .map(e->item("label",e.getKey(),"count",e.getValue())).toList());
        model.addAttribute("latestProjects",projectRows.stream().sorted(Comparator.comparing(Project::getCreatedAt).reversed()).limit(8).toList());
        return "admin/statistics";
    }
    private static String csv(String value){return "\""+(value==null?"":value.replace("\"","\"\"").replace("\r"," ").replace("\n"," "))+"\"";}
    /** Xuất chỉ dữ liệu tổng hợp theo tháng; không tiết lộ thông tin cá nhân. */
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    @ResponseBody
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> export(
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        Range r=range(from,to);
        var projectList=projects.findAll();var creditList=walletCredits.findAll();
        StringBuilder csv=new StringBuilder("\uFEFFTháng,Số dự án mới,Phí nền tảng (VND),Ghi có Freelancer (VND)\r\n");
        for(YearMonth m=YearMonth.from(r.from);!m.isAfter(YearMonth.from(r.to));m=m.plusMonths(1)){
            YearMonth month=m;
            long n=projectList.stream().filter(p->r.includes(p.getCreatedAt()) && YearMonth.from(p.getCreatedAt()).equals(month)).count();
            var rows=creditList.stream().filter(c->r.includes(c.getCreatedAt()) && YearMonth.from(c.getCreatedAt()).equals(month)).toList();
            csv.append(csv(String.format("%02d/%d",month.getMonthValue(),month.getYear()))).append(',').append(n).append(',')
                .append(sum(rows.stream().map(WalletCredit::getFee).toList()).toPlainString()).append(',')
                .append(sum(rows.stream().map(WalletCredit::getNet).toList()).toPlainString()).append("\r\n");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=talentmatch-thong-ke.csv")
            .contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
