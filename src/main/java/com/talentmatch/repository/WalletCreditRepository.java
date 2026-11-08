package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WalletCreditRepository extends JpaRepository<WalletCredit,Long>{
 boolean existsByPayment(Payment payment);
 Optional<WalletCredit> findByPayment(Payment payment);
 List<WalletCredit> findByFreelancerOrderByCreatedAtDesc(User freelancer);
}
