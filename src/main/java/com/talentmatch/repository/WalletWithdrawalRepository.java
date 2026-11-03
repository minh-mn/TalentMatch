package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal,Long>{
 List<WalletWithdrawal> findByFreelancerOrderByCreatedAtDesc(User freelancer);
 List<WalletWithdrawal> findAllByOrderByCreatedAtDesc();
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from WalletWithdrawal w where w.id=:id") Optional<WalletWithdrawal> lock(@Param("id")Long id);
}
