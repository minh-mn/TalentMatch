package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface MilestoneRepository extends JpaRepository<Milestone,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select m from Milestone m where m.id = :id")
 Optional<Milestone> findForUpdate(@Param("id") Long id);
 List<Milestone> findByProjectOrderByCreatedAtAsc(Project project);
}
