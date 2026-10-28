package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JobQuestionRepository extends JpaRepository<JobQuestion,Long>{
 java.util.List<JobQuestion> findByJobOrderByCreatedAtDesc(Job job);
}
