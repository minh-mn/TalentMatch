package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Stage3WorkflowTest {
    private User user(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setFullName("Demo");
        return user;
    }

    private Project project(User customer, User freelancer) {
        Project project = new Project();
        project.setId(50L);
        project.setCustomer(customer);
        project.setFreelancer(freelancer);
        project.setTotalPrice(new BigDecimal("12300000"));
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setCustomerSignedAt(LocalDateTime.now());
        project.setFreelancerSignedAt(LocalDateTime.now());
        return project;
    }

    private JobApplication application(User customer, User freelancer) {
        Job job = new Job();
        job.setStatus(JobStatus.OPEN);
        job.setCustomer(customer);
        job.setDeadline(LocalDate.now().plusDays(30));
        job.setTitle("Ứng dụng web quản lý");
        JobApplication application = new JobApplication();
        application.setId(60L);
        application.setJob(job);
        application.setFreelancer(freelancer);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setProposedPrice(new BigDecimal("15000000"));
        application.setEstimatedDays(20);
        return application;
    }

    @Test
    void freelancerCannotAcceptTheirOwnQuotation() {
        User freelancer = user(8, Role.FREELANCER);
        User customer = user(2, Role.CUSTOMER);
        JobApplication application = application(customer, freelancer);
        JobApplicationRepository applications = mock(JobApplicationRepository.class);
        QuoteRevisionRepository revisions = mock(QuoteRevisionRepository.class);
        when(applications.findForUpdate(60L)).thenReturn(Optional.of(application));
        QuoteRevision quote = quote(application, freelancer);
        when(revisions.findById(17L)).thenReturn(Optional.of(quote));
        when(revisions.findTopByApplicationOrderByVersionDesc(application)).thenReturn(Optional.of(quote));
        QuoteService service = new QuoteService(revisions, applications, mock(ProjectRepository.class),
                mock(NotificationService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(60L, 17L, freelancer, true, null));
        verify(applications, never()).save(any());
    }

    @Test
    void acceptingOtherSideQuotationUpdatesAcceptedAmount() {
        User freelancer = user(8, Role.FREELANCER);
        User customer = user(2, Role.CUSTOMER);
        JobApplication application = application(customer, freelancer);
        JobApplicationRepository applications = mock(JobApplicationRepository.class);
        QuoteRevisionRepository revisions = mock(QuoteRevisionRepository.class);
        when(applications.findForUpdate(60L)).thenReturn(Optional.of(application));
        QuoteRevision quote = quote(application, freelancer);
        quote.setAmount(new BigDecimal("12300000"));
        quote.setEstimatedDays(18);
        when(revisions.findById(17L)).thenReturn(Optional.of(quote));
        when(revisions.findTopByApplicationOrderByVersionDesc(application)).thenReturn(Optional.of(quote));
        QuoteService service = new QuoteService(revisions, applications, mock(ProjectRepository.class),
                mock(NotificationService.class));

        service.resolve(60L, 17L, customer, true, null);

        assertEquals(QuoteStatus.ACCEPTED, quote.getStatus());
        assertEquals(0, application.getProposedPrice().compareTo(new BigDecimal("12300000")));
        assertEquals(18, application.getEstimatedDays());
    }

    @Test
    void reportedProgressCannotBeReducedOrUpdatedByClient() {
        User freelancer = user(8, Role.FREELANCER);
        User customer = user(2, Role.CUSTOMER);
        Project project = project(customer, freelancer);
        Milestone milestone = new Milestone();
        milestone.setId(100L);
        milestone.setProject(project);
        milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        milestone.setProgressPercent(60);
        MilestoneRepository milestones = mock(MilestoneRepository.class);
        when(milestones.findForUpdate(100L)).thenReturn(Optional.of(milestone));
        ProgressService service = new ProgressService(mock(ProgressUpdateRepository.class), milestones,
                mock(NotificationService.class));

        assertThrows(IllegalArgumentException.class, () -> service.report(project, 100L, freelancer, 35,
                "Đã làm xong màn hình", null));
        assertThrows(IllegalArgumentException.class, () -> service.report(project, 100L, customer, 70,
                "Đã làm xong màn hình", null));
        verify(milestones, never()).save(any());
    }

    @Test
    void progressMetricsAreAmountWeightedAndTrackAcceptanceSeparately() {
        Milestone first = new Milestone();
        first.setAmount(new BigDecimal("3000000"));
        first.setStatus(MilestoneStatus.COMPLETED);
        first.setProgressPercent(100);
        Milestone second = new Milestone();
        second.setAmount(new BigDecimal("7000000"));
        second.setStatus(MilestoneStatus.IN_PROGRESS);
        second.setProgressPercent(50);

        assertEquals(65, ProgressMetrics.reported(List.of(first, second)));
        assertEquals(30, ProgressMetrics.accepted(List.of(first, second)));
    }

    @Test
    void signatureRejectsUnavailableAgreementVersion() {
        User customer = user(2, Role.CUSTOMER);
        Project project = project(customer, user(8, Role.FREELANCER));
        ProjectRepository projects = mock(ProjectRepository.class);
        AgreementRevisionRepository revisions = mock(AgreementRevisionRepository.class);
        when(projects.findForUpdate(50L)).thenReturn(Optional.of(project));
        when(revisions.lockVersion(50L, 2)).thenReturn(Optional.empty());
        AgreementService service = new AgreementService(revisions, mock(AgreementChangeRequestRepository.class),
                projects, mock(PaymentRepository.class));

        assertThrows(IllegalArgumentException.class, () -> service.recordSignature(project, customer, 2));
    }

    private QuoteRevision quote(JobApplication application, User author) {
        QuoteRevision quote = new QuoteRevision();
        quote.setApplication(application);
        quote.setAuthor(author);
        quote.setAmount(new BigDecimal("12300000"));
        quote.setEstimatedDays(18);
        quote.setScope("Implement the complete agreed application scope.");
        quote.setRevisionPolicy("Additional work requires prior written agreement.");
        quote.setVersion(1);
        quote.setStatus(QuoteStatus.PENDING);
        ReflectionTestUtils.setField(quote, "id", 17L);
        return quote;
    }
}
