package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Stage3WorkflowTest {
    private User user(long id, Role role) {
        User u = new User(); u.setId(id); u.setRole(role); u.setFullName("Demo");
        return u;
    }
    private Project project(User customer, User freelancer) {
        Project p = new Project(); p.setId(50L); p.setCustomer(customer); p.setFreelancer(freelancer);
        p.setStatus(ProjectStatus.IN_PROGRESS); p.setCustomerSignedAt(java.time.LocalDateTime.now());
        p.setFreelancerSignedAt(java.time.LocalDateTime.now()); return p;
    }
    private JobApplication application(User customer, User freelancer) {
        Job j = new Job(); j.setStatus(JobStatus.OPEN); j.setCustomer(customer);
        j.setDeadline(LocalDate.now().plusDays(30)); j.setTitle("Ứng dụng web quản lý");
        JobApplication app = new JobApplication(); app.setId(60L); app.setJob(j);
        app.setFreelancer(freelancer); app.setStatus(ApplicationStatus.SUBMITTED);
        app.setProposedPrice(new BigDecimal("15000000")); app.setEstimatedDays(20);
        return app;
    }
    @Test void freelancerCannotApproveOwnQuotation() {
        User freelancer = user(8, Role.FREELANCER); User customer = user(2, Role.CUSTOMER);
        JobApplication app = application(customer, freelancer);
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        QuoteRevisionRepository repo = mock(QuoteRevisionRepository.class);
        when(apps.findForUpdate(60L)).thenReturn(Optional.of(app));
        QuoteRevision quote = new QuoteRevision(); quote.setApplication(app);quote.setAuthor(freelancer);
        quote.setVersion(1); quote.setStatus(QuoteStatus.PENDING);
        when(repo.findById(17L)).thenReturn(Optional.of(quote));
        when(repo.findByApplicationOrderByVersionDesc(app)).thenReturn(List.of(quote));
        // id of the stored quotation is generated; use reflection to simulate database row.
        org.springframework.test.util.ReflectionTestUtils.setField(quote,"id",17L);
        QuoteService service = new QuoteService(repo,apps,mock(ProjectRepository.class),mock(NotificationService.class));
        assertThrows(IllegalArgumentException.class,()->service.decide(60L,17L,freelancer,true));
        verify(apps,never()).save(any());
    }
    @Test void acceptingOtherSideQuotationUpdatesAcceptedAmount() {
        User freelancer = user(8, Role.FREELANCER);User customer = user(2, Role.CUSTOMER);
        JobApplication app = application(customer,freelancer);
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        QuoteRevisionRepository repo = mock(QuoteRevisionRepository.class);
        when(apps.findForUpdate(60L)).thenReturn(Optional.of(app));
        QuoteRevision quote = new QuoteRevision();quote.setApplication(app);quote.setAuthor(freelancer);
        quote.setAmount(new BigDecimal("12300000"));quote.setEstimatedDays(18);
        quote.setVersion(1);quote.setStatus(QuoteStatus.PENDING);
        org.springframework.test.util.ReflectionTestUtils.setField(quote,"id",17L);
        when(repo.findById(17L)).thenReturn(Optional.of(quote));
        when(repo.findByApplicationOrderByVersionDesc(app)).thenReturn(List.of(quote));
        QuoteService service = new QuoteService(repo,apps,mock(ProjectRepository.class),mock(NotificationService.class));
        service.decide(60L,17L,customer,true);
        assertEquals(QuoteStatus.ACCEPTED,quote.getStatus());
        assertEquals(0,app.getProposedPrice().compareTo(new BigDecimal("12300000")));
        assertEquals(18,app.getEstimatedDays());
    }
    @Test void reportedProgressCannotBeReducedAndCannotBeUpdatedByClient() {
        User freelancer = user(8,Role.FREELANCER);User customer = user(2,Role.CUSTOMER);
        Project project = project(customer,freelancer);
        Milestone m = new Milestone();m.setId(100L);m.setProject(project);
        m.setStatus(MilestoneStatus.IN_PROGRESS);m.setProgressPercent(60);
        MilestoneRepository repo = mock(MilestoneRepository.class);
        when(repo.findForUpdate(100L)).thenReturn(Optional.of(m));
        ProgressService service = new ProgressService(mock(ProgressUpdateRepository.class),repo,mock(NotificationService.class));
        assertThrows(IllegalArgumentException.class,()->service.report(project,100L,freelancer,35,
                "Đã làm xong màn hình",null));
        assertThrows(IllegalArgumentException.class,()->service.report(project,100L,customer,70,
                "Đã làm xong màn hình",null));
        verify(repo,never()).save(any());
    }
    @Test void progressIsWeightedByMilestoneAmountAndAcceptanceIsSeparate() {
        Milestone first = new Milestone();first.setAmount(new BigDecimal("3000000"));
        first.setStatus(MilestoneStatus.COMPLETED);first.setProgressPercent(100);
        Milestone second = new Milestone();second.setAmount(new BigDecimal("7000000"));
        second.setStatus(MilestoneStatus.IN_PROGRESS);second.setProgressPercent(50);
        ProgressService service = new ProgressService(mock(ProgressUpdateRepository.class),
                mock(MilestoneRepository.class),mock(NotificationService.class));
        assertEquals(65,service.weightedProgress(List.of(first,second)));
        assertEquals(30,service.acceptedProgress(List.of(first,second)));
    }
    @Test void agreementRejectsOldVersion() {
        User customer = user(2,Role.CUSTOMER);User freelancer = user(8,Role.FREELANCER);
        Project p = project(customer,freelancer);p.setAgreementVersion(3);
        ProjectRepository repo = mock(ProjectRepository.class);
        when(repo.findForUpdate(50L)).thenReturn(Optional.of(p));
        AgreementService service = new AgreementService(mock(AgreementRevisionRepository.class),
                repo,mock(PaymentRepository.class),mock(NotificationService.class));
        assertThrows(IllegalArgumentException.class,()->service.sign(p,customer,2));
    }
}
