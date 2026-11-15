package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface QuoteRevisionRepository extends JpaRepository<QuoteRevision,Long> {
    @EntityGraph(attributePaths = {"application.job", "application.freelancer", "author"})
    List<QuoteRevision> findByApplicationOrderByVersionDesc(JobApplication application);
    @EntityGraph(attributePaths = {"application.job", "application.freelancer", "author"})
    Optional<QuoteRevision> findTopByApplicationOrderByVersionDesc(JobApplication application);
    @EntityGraph(attributePaths = {"application.job", "application.freelancer", "author"})
    Optional<QuoteRevision> findTopByApplicationAndStatusOrderByVersionDesc(JobApplication application,QuoteStatus status);
    boolean existsByApplication(JobApplication application);
}
