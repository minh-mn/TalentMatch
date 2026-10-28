package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VerificationTokenRepository extends JpaRepository<VerificationToken,Long>{
 Optional<VerificationToken> findByToken(String token);
 Optional<VerificationToken> findByUser(User user);
 void deleteByUser(User user);
}
