package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Stage2MilestoneTest {
    @Test
    void freelancerCannotMarkMilestoneSubmittedWithoutDelivery() {
        User freelancer = new User();
        freelancer.setId(8L);
        freelancer.setRole(Role.FREELANCER);
        User client = new User();
        client.setId(2L);
        client.setRole(Role.CUSTOMER);
        Project project = new Project();
        project.setId(10L);
        project.setFreelancer(freelancer);
        project.setCustomer(client);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        Milestone milestone = new Milestone();
        milestone.setId(100L);
        milestone.setProject(project);
        milestone.setStatus(MilestoneStatus.IN_PROGRESS);

        MilestoneRepository milestoneRepo = mock(MilestoneRepository.class);
        ProjectDeliveryRepository deliveryRepo = mock(ProjectDeliveryRepository.class);
        when(milestoneRepo.findById(100L)).thenReturn(Optional.of(milestone));
        when(deliveryRepo.existsByProjectAndMilestone(project, milestone)).thenReturn(false);

        ProjectService service = new ProjectService(mock(ProjectRepository.class),
                mock(JobApplicationRepository.class), mock(JobRepository.class), milestoneRepo,
                mock(PaymentRepository.class), mock(UserRepository.class),
                mock(DisputeRepository.class), deliveryRepo);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.markMilestone(100L, freelancer, MilestoneStatus.SUBMITTED));
        assertTrue(ex.getMessage().contains("bàn giao"));
        verify(milestoneRepo, never()).save(any());
        assertEquals(MilestoneStatus.IN_PROGRESS, milestone.getStatus());
    }
}
