package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.WalletDemoService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/** Monitoring-only disbursement view. Withdrawal decisions use the existing simulation workflow. */
@Controller
@RequestMapping("/admin/settlements")
public class AdminSettlementWorkspaceController {
    private final PaymentRepository payments;
    private final WalletDemoService wallet;
    public AdminSettlementWorkspaceController(PaymentRepository payments, WalletDemoService wallet) {
        this.payments = payments;
        this.wallet = wallet;
    }
    public record DisbursementRow(Long id, String code, Long projectId, String project,
              String freelancer, String milestone, BigDecimal amount, String status) {}
    public record WithdrawalRow(Long id, String freelancer, String bank, String masked,
              BigDecimal amount, String status, String reason) {}
    private String label(User user) {
        if (user == null) return "—";
        return user.getFullName()==null || user.getFullName().isBlank() ? user.getEmail() : user.getFullName();
    }
    private BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    @GetMapping
    @Transactional(readOnly = true)
    public String index(@RequestParam(defaultValue="all") String tab,
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="all") String status,
            @RequestParam(defaultValue="1") int page,
            @RequestParam(required=false) Long selectedPayment,
            @RequestParam(required=false) Long selectedWithdrawal, Model model) {
        List<DisbursementRow> allPayments = new ArrayList<>();
        for (Payment payment : payments.findAll()) {
            if (payment.getStatus()!=PaymentStatus.PAID && payment.getStatus()!=PaymentStatus.DISBURSED) continue;
            Project project=payment.getProject();
            String title="Dự án #"+project.getId();
            try { title=project.getApplication().getJob().getTitle(); } catch(Exception ignored) {}
            allPayments.add(new DisbursementRow(payment.getId(),payment.getTransactionCode(),project.getId(),title,
                 label(project.getFreelancer()), payment.getMilestone()==null ? "Toàn dự án" : payment.getMilestone().getTitle(),
                 payment.getAmount()==null?BigDecimal.ZERO:payment.getAmount(), payment.getStatus().name()));
        }
        List<WithdrawalRow> allWithdrawals=new ArrayList<>();
        for(WalletWithdrawal w:wallet.allWithdrawals()) {
            allWithdrawals.add(new WithdrawalRow(w.getId(),label(w.getFreelancer()),w.getBank().getBankName(),
                w.getBank().getMasked(),w.getAmount(),w.getStatus().name(),w.getReason()));
        }
        String query=q.strip().toLowerCase(Locale.ROOT);
        List<DisbursementRow> filteredPayments=allPayments.stream().filter(r->
             (query.isBlank()||(r.code()+" "+r.project()+" "+r.freelancer()).toLowerCase(Locale.ROOT).contains(query))
             && (status.equals("all")||status.equals(r.status()))).sorted(Comparator.comparing(DisbursementRow::id).reversed()).toList();
        List<WithdrawalRow> filteredWithdrawals=allWithdrawals.stream().filter(r->
            (query.isBlank()||(r.id()+" "+r.freelancer()+" "+r.bank()).toLowerCase(Locale.ROOT).contains(query))
             && (status.equals("all")||status.equals(r.status()))).sorted(Comparator.comparing(WithdrawalRow::id).reversed()).toList();
        boolean withdrawalTab="withdrawals".equals(tab);
        int count=withdrawalTab?filteredWithdrawals.size():filteredPayments.size();
        int pages=Math.max(1,(count+4)/5);page=Math.min(Math.max(page,1),pages);int from=(page-1)*5;
        List<DisbursementRow> paymentRows=withdrawalTab?List.of():filteredPayments.subList(from,Math.min(from+5,filteredPayments.size()));
        List<WithdrawalRow> withdrawalRows=withdrawalTab?filteredWithdrawals.subList(from,Math.min(from+5,filteredWithdrawals.size())):List.of();
        DisbursementRow chosenPayment=allPayments.stream().filter(r->Objects.equals(r.id(),selectedPayment)).findFirst().orElse(paymentRows.isEmpty()?null:paymentRows.get(0));
        WithdrawalRow chosenWithdrawal=allWithdrawals.stream().filter(r->Objects.equals(r.id(),selectedWithdrawal)).findFirst().orElse(withdrawalRows.isEmpty()?null:withdrawalRows.get(0));
        model.addAttribute("paymentRows",paymentRows);model.addAttribute("withdrawalRows",withdrawalRows);
        model.addAttribute("chosenPayment",chosenPayment);model.addAttribute("chosenWithdrawal",chosenWithdrawal);
        model.addAttribute("q",q);model.addAttribute("status",status);model.addAttribute("tab",tab);
        model.addAttribute("page",page);model.addAttribute("pages",pages);model.addAttribute("total",count);
        model.addAttribute("awaitingCount",allPayments.stream().filter(r->r.status().equals("PAID")).count());
        model.addAttribute("creditedCount",allPayments.stream().filter(r->r.status().equals("DISBURSED")).count());
        model.addAttribute("creditedAmount",sum(allPayments.stream().filter(r->r.status().equals("DISBURSED")).map(DisbursementRow::amount).toList()));
        model.addAttribute("awaitingAmount",sum(allPayments.stream().filter(r->r.status().equals("PAID")).map(DisbursementRow::amount).toList()));
        model.addAttribute("withdrawalPendingCount",allWithdrawals.stream().filter(r->r.status().equals("PENDING")).count());
        model.addAttribute("withdrawalCompleteAmount",sum(allWithdrawals.stream().filter(r->r.status().equals("COMPLETED")).map(WithdrawalRow::amount).toList()));
        model.addAttribute("active","settlements");
        return "admin/settlements";
    }
}
