package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ProjectRepository extends JpaRepository<Project,Long>{
 @Override
 @EntityGraph(attributePaths = {"application.job", "application.freelancer", "customer", "freelancer"})
 Optional<Project> findById(Long id);
 @Override
 @EntityGraph(attributePaths = {"application.job", "application.freelancer", "customer", "freelancer"})
 List<Project> findAll();
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from Project p where p.id=:id")
 Optional<Project> findForUpdate(@Param("id") Long id);
 @EntityGraph(attributePaths = {"application.job", "application.freelancer", "customer", "freelancer"})
 Optional<Project> findByApplication(JobApplication application);
 @EntityGraph(attributePaths = {"application.job", "application.freelancer", "customer", "freelancer"})
 List<Project> findByFreelancerOrderByCreatedAtDesc(User freelancer);
 @EntityGraph(attributePaths = {"application.job", "application.freelancer", "customer", "freelancer"})
 List<Project> findByCustomerOrderByCreatedAtDesc(User customer);
 long countByStatus(ProjectStatus status);
}
