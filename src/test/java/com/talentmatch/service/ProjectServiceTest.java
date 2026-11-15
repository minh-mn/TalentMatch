package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProjectServiceTest {
    @Test
    void doesNotAllowAnotherHireAfterJobHasBeenClosed() {
        User customer = new User();
        customer.setId(1L);
        customer.setRole(Role.CUSTOMER);

        Job job = new Job();
        job.setId(2L);
        job.setCustomer(customer);
        job.setStatus(JobStatus.CLOSED);

        JobApplication application = new JobApplication();
        application.setId(3L);
        application.setJob(job);
        application.setStatus(ApplicationStatus.SHORTLISTED);

        JobApplicationRepository applications = mock(JobApplicationRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        QuoteService quotes = mock(QuoteService.class);
        when(applications.findById(3L)).thenReturn(Optional.of(application));
        when(jobs.findForUpdate(2L)).thenReturn(Optional.of(job));
        when(applications.findForUpdate(3L)).thenReturn(Optional.of(application));
        when(projects.findByApplication(application)).thenReturn(Optional.empty());

        ProjectService service = new ProjectService(projects, applications, jobs,
                mock(MilestoneRepository.class), mock(PaymentRepository.class),
                mock(UserRepository.class), quotes, mock(AgreementService.class));

        assertThrows(IllegalArgumentException.class, () -> service.acceptApplication(3L, customer));
        verify(quotes, never()).accepted(application);
        verify(projects, never()).save(any(Project.class));
    }
}
