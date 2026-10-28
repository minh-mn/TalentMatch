package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProjectDeliveryRepository extends JpaRepository<ProjectDelivery,Long> {
    List<ProjectDelivery> findByProjectOrderBySubmittedAtDesc(Project project);
    List<ProjectDelivery> findByProjectAndMilestoneOrderBySubmittedAtDesc(Project project,Milestone milestone);
    boolean existsByProjectAndMilestone(Project project,Milestone milestone);
}
