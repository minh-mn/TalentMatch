package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface JobApplicationRepository extends JpaRepository<JobApplication,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from JobApplication a where a.id = :id")
 Optional<JobApplication> findForUpdate(@Param("id") Long id);
 @Override
 @EntityGraph(attributePaths = {"job", "job.customer", "freelancer"})
 Optional<JobApplication> findById(Long id);
 Optional<JobApplication> findByJobAndFreelancer(Job job,User freelancer);
 @EntityGraph(attributePaths = {"job", "job.customer", "freelancer"})
 List<JobApplication> findByFreelancerOrderByCreatedAtDesc(User freelancer);
 @EntityGraph(attributePaths = {"job", "freelancer"})
 List<JobApplication> findByJobOrderByCreatedAtDesc(Job job);
 long countByJob(Job job);
 long countByFreelancerAndStatus(User freelancer,ApplicationStatus status);
}
