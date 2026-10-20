package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentRepository extends JpaRepository<Payment,Long>{
 List<Payment> findByPayerOrderByCreatedAtDesc(User payer);
 List<Payment> findByProjectOrderByCreatedAtDesc(Project project);
 List<Payment> findByProjectAndMilestone(Project project,Milestone milestone);
 boolean existsByProjectAndMilestoneAndStatusIn(Project project,Milestone milestone,Collection<PaymentStatus> statuses);
 boolean existsByProjectAndMilestoneIsNullAndStatusIn(Project project,Collection<PaymentStatus> statuses);
}
