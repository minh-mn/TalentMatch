package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuoteServiceTest {
    private QuoteRevisionRepository revisions;
    private JobApplicationRepository applications;
    private ProjectRepository projects;
    private QuoteService service;
    private JobApplication application;
    private User freelancer;
    private User client;

    @BeforeEach void setup() {
        revisions=mock(QuoteRevisionRepository.class);
        applications=mock(JobApplicationRepository.class);
        projects=mock(ProjectRepository.class);
        service=new QuoteService(revisions,applications,projects,mock(NotificationService.class));
        freelancer=new User(); freelancer.setId(1L); freelancer.setRole(Role.FREELANCER); freelancer.setFullName("Freelancer");
        client=new User(); client.setId(2L); client.setRole(Role.CUSTOMER); client.setFullName("Client");
        Job job=new Job(); job.setId(3L); job.setStatus(JobStatus.OPEN); job.setCustomer(client);
        application=new JobApplication(); application.setId(4L); application.setFreelancer(freelancer);
        application.setJob(job); application.setStatus(ApplicationStatus.SUBMITTED);
        application.setProposal("Xây dựng giao diện đầy đủ và bàn giao mã nguồn.");
        application.setProposedPrice(new BigDecimal("18000000")); application.setEstimatedDays(25);
        when(applications.findForUpdate(4L)).thenReturn(Optional.of(application));
        when(projects.findByApplication(application)).thenReturn(Optional.empty());
        when(revisions.findTopByApplicationAndStatusOrderByVersionDesc(application,QuoteStatus.ACCEPTED)).thenReturn(Optional.empty());
    }

    @Test void cannotAcceptOwnPendingQuote() {
        QuoteRevision current=quote(5L,1,freelancer,QuoteStatus.PENDING);
        when(revisions.findById(5L)).thenReturn(Optional.of(current));
        when(revisions.findTopByApplicationOrderByVersionDesc(application)).thenReturn(Optional.of(current));
        var error=assertThrows(IllegalArgumentException.class,()->service.resolve(4L,5L,freelancer,true,""));
        assertTrue(error.getMessage().contains("tự chấp nhận"));
    }

    @Test void canAcceptOtherSidesPendingQuote() {
        QuoteRevision current=quote(5L,2,freelancer,QuoteStatus.PENDING);
        when(revisions.findById(5L)).thenReturn(Optional.of(current));
        when(revisions.findTopByApplicationOrderByVersionDesc(application)).thenReturn(Optional.of(current));
        service.resolve(4L,5L,client,true,"");
        assertEquals(QuoteStatus.ACCEPTED,current.getStatus());
        assertEquals(0,application.getProposedPrice().compareTo(new BigDecimal("15000000")));
        verify(applications).save(application);
        verify(revisions).save(current);
    }

    @Test void cannotAcceptSupersededVersion() {
        QuoteRevision old=quote(5L,1,freelancer,QuoteStatus.SUPERSEDED);
        QuoteRevision latest=quote(6L,2,client,QuoteStatus.PENDING);
        when(revisions.findById(5L)).thenReturn(Optional.of(old));
        when(revisions.findTopByApplicationOrderByVersionDesc(application)).thenReturn(Optional.of(latest));
        assertThrows(IllegalArgumentException.class,()->service.resolve(4L,5L,client,true,""));
    }

    private QuoteRevision quote(Long id,int version,User author,QuoteStatus status){
        QuoteRevision q=new QuoteRevision(); ReflectionTestUtils.setField(q,"id",id);
        q.setApplication(application);q.setVersion(version);q.setAuthor(author);
        q.setStatus(status);q.setAmount(new BigDecimal("15000000"));q.setEstimatedDays(30);
        q.setScope("Thiết kế giao diện và bàn giao tài liệu hướng dẫn chi tiết");
        q.setRevisionPolicy("Cho phép hai lần chỉnh sửa sau bàn giao");
        return q;
    }
}
