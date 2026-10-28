package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MilestoneRepository extends JpaRepository<Milestone,Long>{
 List<Milestone> findByProjectOrderByCreatedAtAsc(Project project);
}
