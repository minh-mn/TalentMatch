package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.PaymentRepository;
import com.talentmatch.service.ProjectService;
import com.talentmatch.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Comparator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/customer/payments")
public class CustomerPaymentsWorkspaceController {
    private final ProjectService projectService;
    private final PaymentRepository paymentRepository;
    private final UserService userService;
    public CustomerPaymentsWorkspaceController(ProjectService projectService, PaymentRepository paymentRepository, UserService userService) {
        this.projectService = projectService;
        this.paymentRepository = paymentRepository;
        this.userService = userService;
    }
    @GetMapping
    public String index(Authentication authentication, Model model,
                        @RequestParam(defaultValue="projects") String tab,
                        @RequestParam(defaultValue="") String q,
                        @RequestParam(defaultValue="all") String status,
                        @RequestParam(defaultValue="newest") String sort) {
        if (authentication == null) return "redirect:/login";
        User customer = userService.byEmail(authentication.getName());
        if (customer.getRole() != Role.CUSTOMER) throw new org.springframework.security.access.AccessDeniedException("Chỉ Client được truy cập.");
        if (!Set.of("projects", "transactions", "refunds").contains(tab)) tab = "projects";
        Set<String> projectStates = Set.of("all", "WAITING_PAYMENT", "IN_PROGRESS", "WAITING_ACCEPTANCE", "COMPLETED", "CANCELLED", "DISPUTED");
        Set<String> paymentStates = Set.of("all", "PENDING", "PAID", "DISBURSED", "FAILED", "REFUNDED");
        if (!("transactions".equals(tab) ? paymentStates : projectStates).contains(status)) status = "all";
        if (!Set.of("newest", "oldest", "amountHigh", "amountLow").contains(sort)) sort = "newest";
        final String activeStatus = status;
        List<Project> allProjects = new ArrayList<>(projectService.forCustomer(customer));
        List<Payment> allPayments = paymentRepository.findByPayerOrderByCreatedAtDesc(customer).stream()
                .filter(p -> p.getProject() != null && p.getProject().getCustomer() != null && Objects.equals(p.getProject().getCustomer().getId(), customer.getId()))
                .collect(Collectors.toList());
        BigDecimal commitments = allProjects.stream().map(Project::getTotalPrice).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = amountFor(allPayments, PaymentStatus.PAID).add(amountFor(allPayments, PaymentStatus.DISBURSED));
        BigDecimal notDisbursed = amountFor(allPayments, PaymentStatus.PAID);
        BigDecimal disbursed = amountFor(allPayments, PaymentStatus.DISBURSED);
        String query = q.trim().toLowerCase(java.util.Locale.ROOT);
        List<Project> projects = allProjects.stream().filter(p -> query.isEmpty() ||
                safeProjectTitle(p).toLowerCase(java.util.Locale.ROOT).contains(query) ||
                ("HD-" + p.getId()).toLowerCase(java.util.Locale.ROOT).contains(query) ||
                safeFreelancerName(p).toLowerCase(java.util.Locale.ROOT).contains(query))
                .filter(p -> "all".equals(activeStatus) || p.getStatus() != null && p.getStatus().name().equals(activeStatus))
                .collect(Collectors.toList());
        List<Payment> transactions = allPayments.stream().filter(p -> query.isEmpty() ||
                safe(p.getTransactionCode()).toLowerCase(java.util.Locale.ROOT).contains(query) ||
                safeProjectTitle(p.getProject()).toLowerCase(java.util.Locale.ROOT).contains(query))
                .filter(p -> "all".equals(activeStatus) || p.getStatus() != null && p.getStatus().name().equals(activeStatus))
                .collect(Collectors.toList());
        if ("oldest".equals(sort)) { Collections.reverse(projects); Collections.reverse(transactions); }
        else if ("amountHigh".equals(sort) || "amountLow".equals(sort)) {
            Comparator<Project> projectAmount = Comparator.comparing(Project::getTotalPrice, Comparator.nullsLast(Comparator.naturalOrder()));
            Comparator<Payment> transactionAmount = Comparator.comparing(Payment::getAmount, Comparator.nullsLast(Comparator.naturalOrder()));
            if ("amountHigh".equals(sort)) { projectAmount = projectAmount.reversed(); transactionAmount = transactionAmount.reversed(); }
            projects.sort(projectAmount); transactions.sort(transactionAmount);
        }
        Map<Long,List<Milestone>> milestones = new HashMap<>();
        Map<Long,BigDecimal> projectPaid = new HashMap<>();
        for(Project p : projects) {
            milestones.put(p.getId(), projectService.milestones(p));
            projectPaid.put(p.getId(), allPayments.stream().filter(tx -> tx.getProject().getId().equals(p.getId()))
                .filter(tx -> tx.getStatus() == PaymentStatus.PAID || tx.getStatus() == PaymentStatus.DISBURSED)
                .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        // Payment state must be derived from the actual transaction ledger, not MilestoneStatus.
        Map<Long,String> milestonePaymentStates = new HashMap<>();
        Map<Long,BigDecimal> milestonePaid = new HashMap<>();
        Map<Long,String> paymentModes = new HashMap<>();
        Map<Long,Boolean> schedulesReady = new HashMap<>();
        Map<Long,Boolean> planBalanced = new HashMap<>();
        Map<Long,BigDecimal> projectHeld = new HashMap<>();
        Map<Long,BigDecimal> projectReleased = new HashMap<>();
        Map<Long,String> projectNames = new HashMap<>();
        Map<Long,String> freelancerNames = new HashMap<>();
        Map<Long,Boolean> agreementSigned = new HashMap<>();
        for (Project p : projects) {
            projectNames.put(p.getId(), safeProjectTitle(p));
            freelancerNames.put(p.getId(), safeFreelancerName(p));
            agreementSigned.put(p.getId(), p.isAgreementSigned());
            List<Milestone> stages=milestones.get(p.getId());
            for(Milestone m:stages){
                List<Payment> mTx=allPayments.stream()
                    .filter(tx->tx.getProject()!=null && Objects.equals(tx.getProject().getId(),p.getId()))
                    .filter(tx->tx.getMilestone()!=null && Objects.equals(tx.getMilestone().getId(),m.getId()))
                    .toList();
                BigDecimal effective=mTx.stream()
                    .filter(tx->tx.getStatus()==PaymentStatus.PAID || tx.getStatus()==PaymentStatus.DISBURSED)
                    .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
                milestonePaid.put(m.getId(),effective);
                String paymentState;
                if(mTx.stream().anyMatch(tx->tx.getStatus()==PaymentStatus.DISBURSED)) paymentState="DISBURSED";
                else if(mTx.stream().anyMatch(tx->tx.getStatus()==PaymentStatus.PAID)) paymentState="PAID";
                else if(mTx.stream().anyMatch(tx->tx.getStatus()==PaymentStatus.PENDING)) paymentState="PENDING";
                else if(mTx.stream().anyMatch(tx->tx.getStatus()==PaymentStatus.REFUNDED)) paymentState="REFUNDED";
                else if(mTx.stream().anyMatch(tx->tx.getStatus()==PaymentStatus.FAILED)) paymentState="FAILED";
                else paymentState="UNPAID";
                if(!"UNPAID".equals(paymentState) && !"FAILED".equals(paymentState)
                        && !"REFUNDED".equals(paymentState) && effective.signum()>0 && m.getAmount()!=null
                        && effective.compareTo(m.getAmount())!=0) paymentState="RECONCILE";
                if("UNPAID".equals(paymentState) && m.getStatus()!=MilestoneStatus.UNPAID) paymentState="RECONCILE";
                if(("PAID".equals(paymentState) || "DISBURSED".equals(paymentState)) && m.getStatus()==MilestoneStatus.UNPAID) paymentState="RECONCILE";
                milestonePaymentStates.put(m.getId(),paymentState);
            }
            paymentModes.put(p.getId(),stages.isEmpty()?"ONE_TIME":"MILESTONES");
            boolean balanced=stages.isEmpty() || stages.stream().map(Milestone::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(p.getTotalPrice())==0;
            planBalanced.put(p.getId(),balanced);
            schedulesReady.put(p.getId(), balanced && (stages.isEmpty()?p.getPaymentDueDate()!=null:
                    stages.stream().allMatch(m->m.getPaymentDueDate()!=null && m.getDeadline()!=null)));
            List<Payment> projectTransactions=allPayments.stream().filter(tx->Objects.equals(tx.getProject().getId(),p.getId())).toList();
            projectHeld.put(p.getId(),amountFor(projectTransactions,PaymentStatus.PAID));
            projectReleased.put(p.getId(),amountFor(projectTransactions,PaymentStatus.DISBURSED));
        }
        Map<Long,String> transactionProjectNames = new HashMap<>();
        for (Payment tx : transactions) transactionProjectNames.put(tx.getId(), safeProjectTitle(tx.getProject()));
        model.addAttribute("milestonePaymentStates",milestonePaymentStates);
        model.addAttribute("milestonePaid",milestonePaid);
        model.addAttribute("paymentModes",paymentModes);
        model.addAttribute("schedulesReady",schedulesReady);
        model.addAttribute("planBalanced",planBalanced);
        model.addAttribute("projectHeld",projectHeld);
        model.addAttribute("projectReleased",projectReleased);
        model.addAttribute("today",LocalDate.now());
        model.addAttribute("projectNames",projectNames);
        model.addAttribute("freelancerNames",freelancerNames);
        model.addAttribute("agreementSignedMap",agreementSigned);
        model.addAttribute("transactionProjectNames",transactionProjectNames);
        model.addAttribute("tab", tab); model.addAttribute("sort", sort);
        model.addAttribute("q", q); model.addAttribute("status", status);
        model.addAttribute("projects", projects); model.addAttribute("transactions", transactions);
        model.addAttribute("milestoneMap", milestones); model.addAttribute("projectPaid", projectPaid);
        model.addAttribute("commitments", commitments); model.addAttribute("paidTotal", paid);
        model.addAttribute("unsettledTotal", notDisbursed); model.addAttribute("disbursedTotal", disbursed);
        return "customer/payments";
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String safeProjectTitle(Project p) {
        if (p == null || p.getApplication() == null || p.getApplication().getJob() == null) return "Dự án";
        return safe(p.getApplication().getJob().getTitle());
    }
    private static String safeFreelancerName(Project p) {
        return p == null || p.getFreelancer() == null ? "Freelancer" : safe(p.getFreelancer().getFullName());
    }
    private BigDecimal amountFor(List<Payment> payments, PaymentStatus status) {
        return payments.stream().filter(p -> p.getStatus() == status).map(Payment::getAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
