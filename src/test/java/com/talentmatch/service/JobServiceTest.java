package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class JobServiceTest {
    @Test void filtersExpiredJobsAndUnknownDeadlinesCorrectly(){
        JobRepository repository=mock(JobRepository.class);
        Job past=new Job();past.setTitle("Dự án quá hạn");past.setStatus(JobStatus.OPEN);
        past.setBudget(new BigDecimal("5000000"));past.setDeadline(LocalDate.now().minusDays(1));
        Job future=new Job();future.setTitle("Dự án còn hạn");future.setStatus(JobStatus.OPEN);
        future.setBudget(new BigDecimal("6000000"));future.setDeadline(LocalDate.now().plusDays(7));
        Job unspecified=new Job();unspecified.setTitle("Không ghi thời hạn");unspecified.setStatus(JobStatus.OPEN);
        unspecified.setBudget(new BigDecimal("4000000"));
        when(repository.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN)).thenReturn(List.of(past,future,unspecified));
        var jobs=new JobService(repository,mock(SavedJobRepository.class),mock(JobApplicationRepository.class),
            mock(UserRepository.class),mock(NotificationService.class));
        var normal=jobs.searchAdvanced(null,null,null,null,null,null,List.of(),List.of(),"latest");
        assertEquals(2,normal.size());assertFalse(normal.contains(past));
        var timeFiltered=jobs.searchAdvanced(null,null,null,null,null,LocalDate.now().plusDays(10),List.of(),List.of(),"latest");
        assertEquals(List.of(future),timeFiltered);
    }
}
