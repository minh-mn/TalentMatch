package com.talentmatch.service;

import com.talentmatch.model.Job;
import com.talentmatch.model.JobStatus;
import com.talentmatch.model.User;
import com.talentmatch.repository.JobApplicationRepository;
import com.talentmatch.repository.JobRepository;
import com.talentmatch.repository.SavedJobRepository;
import com.talentmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    @Test
    void filtersExpiredJobsAndUnknownDeadlinesCorrectly() {
        JobRepository repository = mock(JobRepository.class);

        Job past = new Job();
        past.setTitle("Dự án quá hạn");
        past.setStatus(JobStatus.OPEN);
        past.setBudget(new BigDecimal("5000000"));
        past.setDeadline(LocalDate.now().minusDays(1));

        Job future = new Job();
        future.setTitle("Dự án còn hạn");
        future.setStatus(JobStatus.OPEN);
        future.setBudget(new BigDecimal("6000000"));
        future.setDeadline(LocalDate.now().plusDays(7));

        Job unspecified = new Job();
        unspecified.setTitle("Không ghi thời hạn");
        unspecified.setStatus(JobStatus.OPEN);
        unspecified.setBudget(new BigDecimal("4000000"));

        when(repository.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN))
                .thenReturn(List.of(past, future, unspecified));

        JobService service = new JobService(
                repository,
                mock(SavedJobRepository.class),
                mock(JobApplicationRepository.class),
                mock(UserRepository.class),
                mock(QuoteService.class)
        );

        var normal = service.searchAdvanced(
                null, null, null, null, null, null,
                List.of(), List.of(), "latest"
        );
        assertEquals(2, normal.size());
        assertFalse(normal.contains(past));

        var timeFiltered = service.searchAdvanced(
                null, null, null, null, null,
                LocalDate.now().plusDays(10),
                List.of(), List.of(), "latest"
        );
        assertEquals(List.of(future), timeFiltered);
    }

    @Test
    void cannotReopenCancelledJob() {
        User customer = new User();
        customer.setId(1L);
        Job cancelled = new Job();
        cancelled.setId(2L);
        cancelled.setCustomer(customer);
        cancelled.setStatus(JobStatus.CANCELLED);
        JobRepository jobs = mock(JobRepository.class);
        when(jobs.findForUpdate(2L)).thenReturn(Optional.of(cancelled));
        JobService service = new JobService(jobs, mock(SavedJobRepository.class),
                mock(JobApplicationRepository.class), mock(UserRepository.class), mock(QuoteService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.changeStatus(2L, customer, JobStatus.OPEN));
        verify(jobs, org.mockito.Mockito.never()).save(cancelled);
    }
}
