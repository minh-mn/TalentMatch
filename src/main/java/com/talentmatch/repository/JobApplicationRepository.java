package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface JobApplicationRepository extends JpaRepository<JobApplication,Long>{
 Optional<JobApplication> findByJobAndFreelancer(Job job,User freelancer);
 List<JobApplication> findByFreelancerOrderByCreatedAtDesc(User freelancer);
 List<JobApplication> findByJobOrderByCreatedAtDesc(Job job);
 long countByJob(Job job);
 long countByFreelancerAndStatus(User freelancer,ApplicationStatus status);
}
