package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CommunityVoteRepository extends JpaRepository<CommunityVote,Long>{Optional<CommunityVote> findByQuestionIdAndUserId(Long questionId,Long userId); long countByQuestionId(Long id);}
