package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProjectRepository extends JpaRepository<Project,Long>{
 Optional<Project> findByApplication(JobApplication application);
 List<Project> findByFreelancerOrderByCreatedAtDesc(User freelancer);
 List<Project> findByCustomerOrderByCreatedAtDesc(User customer);
 long countByStatus(ProjectStatus status);
}
