package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AgreementChangeRequestRepository extends JpaRepository<AgreementChangeRequest,Long> {
 List<AgreementChangeRequest> findByProjectOrderByCreatedAtDesc(Project project);
 boolean existsByProjectAndStatus(Project project,String status);
}
