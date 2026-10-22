package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CommunityQuestionRepository extends JpaRepository<CommunityQuestion,Long>{List<CommunityQuestion> findAllByOrderByCreatedAtDesc();}
