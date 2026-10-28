package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CommunityAnswerRepository extends JpaRepository<CommunityAnswer,Long>{List<CommunityAnswer> findByQuestionIdOrderByCreatedAtAsc(Long id); long countByQuestionId(Long id);}
