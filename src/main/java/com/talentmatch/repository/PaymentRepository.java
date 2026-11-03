package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment,Long>{
 Optional<Payment> findByTransactionCode(String code);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from Payment p where p.transactionCode = :code")
 Optional<Payment> lockByCode(@org.springframework.data.repository.query.Param("code") String code);
 List<Payment> findByPayerOrderByCreatedAtDesc(User payer);
 List<Payment> findByProjectOrderByCreatedAtDesc(Project project);
 List<Payment> findByProjectAndMilestone(Project project,Milestone milestone);
 boolean existsByProjectAndMilestoneAndStatusIn(Project project,Milestone milestone,Collection<PaymentStatus> statuses);
 boolean existsByProjectAndMilestoneIsNullAndStatusIn(Project project,Collection<PaymentStatus> statuses);
}
