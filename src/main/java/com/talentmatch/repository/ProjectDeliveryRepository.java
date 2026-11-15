package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProjectDeliveryRepository extends JpaRepository<ProjectDelivery,Long> {
    @Override
    @EntityGraph(attributePaths = {"project.customer", "project.freelancer"})
    Optional<ProjectDelivery> findById(Long id);
    List<ProjectDelivery> findByProjectOrderBySubmittedAtDesc(Project project);
    List<ProjectDelivery> findByProjectAndMilestoneOrderBySubmittedAtDesc(Project project,Milestone milestone);
    boolean existsByProjectAndMilestone(Project project,Milestone milestone);
}
