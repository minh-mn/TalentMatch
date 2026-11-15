package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Kiểm thử các quy tắc Freelancer được bổ sung trong giai đoạn 2. */
class Stage2WorkflowTest {
    private User freelancer() {
        User f = new User();
        f.setId(8L);
        f.setRole(Role.FREELANCER);
        f.setEnabled(true);
        f.setLocked(false);
        f.setProfessionalTitle("Java Developer");
        f.setSkills("Java, SQL");
        return f;
    }

    private Job activeJob() {
        Job j = new Job();
        j.setStatus(JobStatus.OPEN);
        j.setDeadline(LocalDate.now().plusDays(5));
        j.setBudget(new BigDecimal("5000000"));
        return j;
    }

    private JobService jobs(JobApplicationRepository apps) {
        return new JobService(mock(JobRepository.class), mock(SavedJobRepository.class), apps,
                mock(UserRepository.class), mock(QuoteService.class));
    }

    @Test
    void cannotApplyToExpiredJobEvenByDirectPost() {
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        Job j = activeJob();
        j.setDeadline(LocalDate.now().minusDays(1));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> jobs(apps).apply(j, freelancer(), "Tôi có thể thực hiện yêu cầu này theo đúng tiến độ.",
                        new BigDecimal("4000000"), 10, null));
        assertTrue(ex.getMessage().contains("hết hạn"));
        verifyNoInteractions(apps);
    }

    @Test
    void cannotApplyWhenFreelancerAccountIsLocked() {
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        User f = freelancer();
        f.setLocked(true);
        var ex = assertThrows(IllegalArgumentException.class,
                () -> jobs(apps).apply(activeJob(), f, "Tôi có thể hoàn thành công việc theo yêu cầu.",
                        new BigDecimal("4000000"), 10, null));
        assertTrue(ex.getMessage().contains("khóa"));
        verifyNoInteractions(apps);
    }

    @Test
    void stillCanApplyToOpenJobWithValidFields() {
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        Job j = activeJob();
        User f = freelancer();
        when(apps.findByJobAndFreelancer(j, f)).thenReturn(Optional.empty());
        when(apps.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        JobApplication app = jobs(apps).apply(j, f,
                "Tôi sẽ bàn giao sản phẩm đầy đủ, có kiểm thử chức năng và hướng dẫn sử dụng.",
                new BigDecimal("4000000"), 10, "https://portfolio.example");
        assertEquals(ApplicationStatus.SUBMITTED, app.getStatus());
        assertEquals(f, app.getFreelancer());
        verify(apps).save(any(JobApplication.class));
    }

    @Test
    void freelancerStatsUsePersistedApplicationsAndProjects() {
        User f = freelancer();
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        JobApplication current = new JobApplication();
        current.setCreatedAt(LocalDateTime.now().minusDays(2));
        JobApplication old = new JobApplication();
        old.setCreatedAt(LocalDateTime.now().minusMonths(12));
        Project completed = new Project();
        completed.setStatus(ProjectStatus.COMPLETED);
        completed.setCompletedAt(LocalDateTime.now().minusDays(1));
        Project active = new Project();
        active.setStatus(ProjectStatus.IN_PROGRESS);
        when(apps.findByFreelancerOrderByCreatedAtDesc(f)).thenReturn(List.of(current, old));
        when(projects.findByFreelancerOrderByCreatedAtDesc(f)).thenReturn(List.of(completed, active));
        DashboardService dashboard = new DashboardService(mock(UserRepository.class),
                mock(JobRepository.class), apps, projects, mock(PaymentRepository.class));
        var stats = dashboard.freelancerStats(f);
        assertEquals(2, stats.get("applications"));
        assertEquals(1L, stats.get("completedProjects"));
    }
}
