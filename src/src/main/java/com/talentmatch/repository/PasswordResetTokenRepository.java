package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long>{
 Optional<PasswordResetToken> findByToken(String token);
 List<PasswordResetToken> findByUserAndUsedFalse(User user);
}
