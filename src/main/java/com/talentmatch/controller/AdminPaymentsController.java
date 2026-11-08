package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.PaymentRepository;
import com.talentmatch.repository.WalletCreditRepository;
import com.talentmatch.service.SettlementDemoService;
import com.talentmatch.service.WalletDemoService;
import java.math.RoundingMode;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Read-only Admin ledger. No unverified disbursement/refund actions. */
@Controller
@RequestMapping("/admin/payments")
public class AdminPaymentsController {
 private final PaymentRepository repository;
 private final WalletCreditRepository credits;
 private final WalletDemoService wallet;
 private final SettlementDemoService settlement;
 public AdminPaymentsController(PaymentRepository repository,WalletCreditRepository credits,WalletDemoService wallet,SettlementDemoService settlement){this.repository=repository;this.credits=credits;this.wallet=wallet;this.settlement=settlement;}
 public record Row(Long id,String code,String project,String client,String freelancer,String milestone,String method,
                   String status,BigDecimal amount,LocalDateTime createdAt,Long projectId,
                   LocalDateTime paidAt,LocalDateTime acceptedAt,LocalDateTime releaseAt,LocalDateTime disbursedAt,
                   BigDecimal fee,BigDecimal net,BigDecimal feeRate,boolean disputed,boolean credited) {}
 private static BigDecimal total(List<Row> rows,String... states){Set<String> accepted=Set.of(states);return rows.stream().filter(x->accepted.contains(x.status())).map(Row::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
 @GetMapping @Transactional(readOnly=true)
 public String page(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="all") String status,
     @RequestParam(defaultValue="all") String tab,@RequestParam(defaultValue="newest") String sort,
     @RequestParam(required=false) String from,@RequestParam(required=false) String to,
     @RequestParam(defaultValue="1") int page,@RequestParam(required=false) Long selected,Model model){
  LocalDate start=parse(from),end=parse(to);
  List<Row> all=new ArrayList<>();
  for(Payment pay:repository.findAll()){
   Project project=pay.getProject();String title="Dự án #"+project.getId();
   try{title=project.getApplication().getJob().getTitle();}catch(Exception ignored){}
   String client=name(project.getCustomer()),freelancer=name(project.getFreelancer());
   BigDecimal gross=pay.getAmount()==null?BigDecimal.ZERO:pay.getAmount();
   WalletCredit actual=credits.findByPayment(pay).orElse(null);
   BigDecimal rate=actual!=null && actual.getGross().signum()>0
       ?actual.getFee().multiply(new BigDecimal("100")).divide(actual.getGross(),2,RoundingMode.HALF_UP)
       :pay.getSettlementFeePercent()==null?wallet.feePercent():pay.getSettlementFeePercent();
   BigDecimal fee=actual!=null?actual.getFee():gross.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
   BigDecimal net=actual!=null?actual.getNet():gross.subtract(fee);
   LocalDateTime releaseAt=pay.getAcceptedAt()==null?null:(pay.getScheduledDisbursementAt()==null?pay.getAcceptedAt().plusDays(settlement.holdDays()):pay.getScheduledDisbursementAt());
   all.add(new Row(pay.getId(),pay.getTransactionCode(),title,client,freelancer,
     pay.getMilestone()==null?"Toàn dự án":pay.getMilestone().getTitle(),pay.getMethod(),
     pay.getStatus().name(),gross,pay.getCreatedAt(),project.getId(),pay.getPaidAt(),pay.getAcceptedAt(),releaseAt,
     pay.getDisbursedAt(),fee,net,rate,project.getStatus()==ProjectStatus.DISPUTED,actual!=null));
  }
  model.addAttribute("settlementDays",settlement.holdDays());
  model.addAttribute("feePercent",wallet.feePercent());
  model.addAttribute("platformFees",credits.findAll().stream().map(WalletCredit::getFee).reduce(BigDecimal.ZERO,BigDecimal::add));
  model.addAttribute("freelancerNet",credits.findAll().stream().map(WalletCredit::getNet).reduce(BigDecimal.ZERO,BigDecimal::add));
  model.addAttribute("waitingAcceptance",all.stream().filter(x->x.status().equals("PAID") && x.acceptedAt()==null).count());
  model.addAttribute("pendingRelease",all.stream().filter(x->x.status().equals("PAID") && x.acceptedAt()!=null).count());
  model.addAttribute("transactionCount",all.size());model.addAttribute("paidAmount",total(all,"PAID","DISBURSED"));
  model.addAttribute("heldAmount",total(all,"PAID"));model.addAttribute("disbursedAmount",total(all,"DISBURSED"));
  model.addAttribute("refundedAmount",total(all,"REFUNDED"));model.addAttribute("failedCount",all.stream().filter(x->x.status().equals("FAILED")).count());
  String query=q.strip().toLowerCase(Locale.ROOT);
  List<Row> matching=all.stream().filter(r->query.isEmpty()||(r.code()+" "+r.project()+" "+r.client()+" "+r.freelancer()).toLowerCase(Locale.ROOT).contains(query))
   .filter(r->status.equals("all")||r.status().equals(status))
   .filter(r->switch(tab){case "disbursement"->r.status().equals("PAID")||r.status().equals("DISBURSED");case "refund"->r.status().equals("REFUNDED");case "reconcile"->r.status().equals("FAILED")||r.status().equals("PENDING");default->true;})
   .filter(r->r.createdAt()!=null&&(start==null||!r.createdAt().toLocalDate().isBefore(start))&&(end==null||!r.createdAt().toLocalDate().isAfter(end)))
   .sorted("oldest".equals(sort)?Comparator.comparing(Row::id):Comparator.comparing(Row::id).reversed()).toList();
  int pages=Math.max(1,(matching.size()+4)/5);page=Math.max(1,Math.min(pages,page));
  List<Row> visible=matching.subList((page-1)*5,Math.min(page*5,matching.size()));
  Row detail=all.stream().filter(r->Objects.equals(r.id(),selected)).findFirst().orElse(visible.isEmpty()?null:visible.get(0));
  model.addAttribute("rows",visible);model.addAttribute("chosen",detail);model.addAttribute("q",q);model.addAttribute("status",status);
  model.addAttribute("tab",tab);model.addAttribute("sort",sort);model.addAttribute("from",from);model.addAttribute("to",to);
  model.addAttribute("page",page);model.addAttribute("pages",pages);model.addAttribute("total",matching.size());model.addAttribute("active","payments");
  return "admin/payments";
 }
 private LocalDate parse(String value){try{return value==null||value.isBlank()?null:LocalDate.parse(value);}catch(Exception e){return null;}}
 private String name(User u){if(u==null)return "—";return u.getFullName()==null||u.getFullName().isBlank()?u.getEmail():u.getFullName();}
}
