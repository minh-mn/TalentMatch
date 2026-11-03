package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ProjectRepository extends JpaRepository<Project,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from Project p where p.id=:id")
 Optional<Project> findForUpdate(@Param("id") Long id);
 Optional<Project> findByApplication(JobApplication application);
 List<Project> findByFreelancerOrderByCreatedAtDesc(User freelancer);
 List<Project> findByCustomerOrderByCreatedAtDesc(User customer);
 long countByStatus(ProjectStatus status);
}
