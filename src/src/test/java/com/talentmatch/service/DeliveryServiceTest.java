package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeliveryServiceTest {
 @TempDir Path temp;
 @Test void rejectSubmissionWithoutValidProof(){
     var service=new DeliveryService(mock(ProjectDeliveryRepository.class),mock(MilestoneRepository.class),
         mock(ProjectRepository.class),mock(NotificationService.class),temp.toString());
     User freelancer=new User();freelancer.setId(2L);
     Project p=new Project();p.setStatus(ProjectStatus.IN_PROGRESS);
     p.setFreelancer(freelancer);p.setCustomerSignedAt(java.time.LocalDateTime.now());p.setFreelancerSignedAt(java.time.LocalDateTime.now());
     assertThrows(IllegalArgumentException.class,()->service.submit(p,null,freelancer,"",null,null));
     var invalid=new MockMultipartFile("file","malicious.exe","application/octet-stream",new byte[]{1,2,3});
     assertThrows(IllegalArgumentException.class,()->service.submit(p,null,freelancer,"",null,invalid));
 }
}
