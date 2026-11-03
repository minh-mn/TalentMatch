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
import java.math.BigDecimal;
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
                        @RequestParam(defaultValue="all") String status) {
        if (authentication == null) return "redirect:/login";
        User customer = userService.byEmail(authentication.getName());
        if (customer.getRole() != Role.CUSTOMER) throw new org.springframework.security.access.AccessDeniedException("Chỉ Client được truy cập.");
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
                .filter(p -> "all".equals(status) || p.getStatus() != null && p.getStatus().name().equalsIgnoreCase(status))
                .collect(Collectors.toList());
        List<Payment> transactions = allPayments.stream().filter(p -> query.isEmpty() ||
                safe(p.getTransactionCode()).toLowerCase(java.util.Locale.ROOT).contains(query) ||
                safeProjectTitle(p.getProject()).toLowerCase(java.util.Locale.ROOT).contains(query))
                .filter(p -> "all".equals(status) || p.getStatus() != null && p.getStatus().name().equalsIgnoreCase(status))
                .collect(Collectors.toList());
        Map<Long,List<Milestone>> milestones = new HashMap<>();
        Map<Long,BigDecimal> projectPaid = new HashMap<>();
        for(Project p : projects) {
            milestones.put(p.getId(), projectService.milestones(p));
            projectPaid.put(p.getId(), allPayments.stream().filter(tx -> tx.getProject().getId().equals(p.getId()))
                .filter(tx -> tx.getStatus() == PaymentStatus.PAID || tx.getStatus() == PaymentStatus.DISBURSED)
                .map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        Map<Long,String> projectNames = new HashMap<>();
        Map<Long,String> freelancerNames = new HashMap<>();
        Map<Long,Boolean> agreementSigned = new HashMap<>();
        for (Project p : projects) {
            projectNames.put(p.getId(), safeProjectTitle(p));
            freelancerNames.put(p.getId(), safeFreelancerName(p));
            agreementSigned.put(p.getId(), p.isAgreementSigned());
        }
        Map<Long,String> transactionProjectNames = new HashMap<>();
        for (Payment tx : transactions) transactionProjectNames.put(tx.getId(), safeProjectTitle(tx.getProject()));
        model.addAttribute("projectNames",projectNames);
        model.addAttribute("freelancerNames",freelancerNames);
        model.addAttribute("agreementSignedMap",agreementSigned);
        model.addAttribute("transactionProjectNames",transactionProjectNames);
        model.addAttribute("tab", tab);
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
