package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface QuoteRevisionRepository extends JpaRepository<QuoteRevision,Long> {
    List<QuoteRevision> findByApplicationOrderByVersionDesc(JobApplication application);
    Optional<QuoteRevision> findTopByApplicationOrderByVersionDesc(JobApplication application);
    Optional<QuoteRevision> findTopByApplicationAndStatusOrderByVersionDesc(JobApplication application,QuoteStatus status);
    boolean existsByApplication(JobApplication application);
}
