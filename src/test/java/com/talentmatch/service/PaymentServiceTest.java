package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test
    void abandonedVnpayAttemptExpiresBeforeRetry() {
        PaymentRepository payments = mock(PaymentRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class);
        AgreementService agreements = mock(AgreementService.class);

        User customer = new User();
        customer.setId(7L);
        customer.setRole(Role.CUSTOMER);
        Project project = new Project();
        project.setId(31L);
        project.setCustomer(customer);
        project.setTotalPrice(new BigDecimal("250000"));
        project.setPaymentDueDate(LocalDate.now().plusDays(1));

        Payment abandoned = new Payment();
        abandoned.setProject(project);
        abandoned.setPayer(customer);
        abandoned.setAmount(project.getTotalPrice());
        abandoned.setMethod("VNPAY_SANDBOX");
        abandoned.setStatus(PaymentStatus.PENDING);
        abandoned.setTransactionCode("VNS-OLD");
        abandoned.setCreatedAt(LocalDateTime.now().minusMinutes(16));

        Payment legacyPending = new Payment();
        legacyPending.setProject(project);
        legacyPending.setPayer(customer);
        legacyPending.setAmount(project.getTotalPrice());
        legacyPending.setMethod("VNPAY_SANDBOX");
        legacyPending.setStatus(PaymentStatus.PENDING);
        legacyPending.setTransactionCode("VNS-LEGACY");

        when(projects.findForUpdate(project.getId())).thenReturn(Optional.of(project));
        when(agreements.signedCurrent(project)).thenReturn(true);
        when(milestones.findByProjectOrderByCreatedAtAsc(project)).thenReturn(List.of());
        when(payments.findByProjectOrderByCreatedAtDesc(project))
                .thenReturn(new ArrayList<>(List.of(abandoned, legacyPending)));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(payments, projects, milestones, agreements);
        Payment retry = service.startVnpay(project, null, customer);

        assertEquals(PaymentStatus.FAILED, abandoned.getStatus());
        assertEquals(PaymentStatus.FAILED, legacyPending.getStatus());
        assertEquals(PaymentStatus.PENDING, retry.getStatus());
        verify(payments).saveAll(anyList());
        verify(payments).save(any(Payment.class));
    }

    @Test
    void validVnpayIpnConfirmsPaymentAndStartsProject() {
        PaymentRepository payments = mock(PaymentRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class);
        AgreementService agreements = mock(AgreementService.class);

        User customer = new User();
        customer.setId(7L);
        customer.setRole(Role.CUSTOMER);
        Project project = new Project();
        project.setId(31L);
        project.setCustomer(customer);
        project.setTotalPrice(new BigDecimal("250000"));
        project.setStatus(ProjectStatus.WAITING_PAYMENT);
        Payment payment = new Payment();
        payment.setProject(project);
        payment.setPayer(customer);
        payment.setAmount(new BigDecimal("250000"));
        payment.setMethod("VNPAY_SANDBOX");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("VNS-TEST");

        when(payments.lockByCode("VNS-TEST")).thenReturn(Optional.of(payment));
        when(projects.findForUpdate(project.getId())).thenReturn(Optional.of(project));
        when(agreements.signedCurrent(project)).thenReturn(true);
        when(milestones.findByProjectOrderByCreatedAtAsc(project)).thenReturn(List.of());

        PaymentService service = new PaymentService(payments, projects, milestones, agreements);

        assertEquals("00", service.confirmVnpay("VNS-TEST", "25000000", true));
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(ProjectStatus.IN_PROGRESS, project.getStatus());
        assertEquals("02", service.confirmVnpay("VNS-TEST", "25000000", true),
                "A repeated IPN must not confirm the same payment twice.");
    }

    @Test
    void vnpayIpnRejectsWrongAmountWithoutChangingPayment() {
        PaymentRepository payments = mock(PaymentRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class);
        AgreementService agreements = mock(AgreementService.class);
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("250000"));
        payment.setMethod("VNPAY_SANDBOX");
        payment.setStatus(PaymentStatus.PENDING);
        when(payments.lockByCode("VNS-TEST")).thenReturn(Optional.of(payment));

        PaymentService service = new PaymentService(payments, projects, milestones, agreements);

        assertEquals("04", service.confirmVnpay("VNS-TEST", "1", true));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verifyNoInteractions(projects, milestones, agreements);
    }
}
