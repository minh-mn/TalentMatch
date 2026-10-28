package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TalentReviewRepository extends JpaRepository<TalentReview,Long>{
 java.util.List<TalentReview> findByProjectOrderByCreatedAtDesc(Project project);
 java.util.List<TalentReview> findByTarget(User target);
 boolean existsByProjectAndAuthor(Project project,User author);
}
