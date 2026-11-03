package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WalletBankRepository extends JpaRepository<WalletBank,Long>{
 List<WalletBank> findByFreelancerOrderByIdDesc(User freelancer);
}
