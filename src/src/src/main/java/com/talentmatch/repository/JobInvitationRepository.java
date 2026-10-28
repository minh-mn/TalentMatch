package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JobInvitationRepository extends JpaRepository<JobInvitation,Long>{
 java.util.List<JobInvitation> findByCustomerOrderByCreatedAtDesc(User customer);
 java.util.List<JobInvitation> findByFreelancerOrderByCreatedAtDesc(User freelancer);
 java.util.Optional<JobInvitation> findByJobAndFreelancer(Job job,User freelancer);
}
