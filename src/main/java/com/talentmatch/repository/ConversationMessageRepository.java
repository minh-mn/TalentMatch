package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage,Long>{List<ConversationMessage> findByProjectIdOrderByCreatedAtAsc(Long id);}
