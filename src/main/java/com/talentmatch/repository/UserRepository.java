package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<User,Long>{
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
 Optional<User> lockForWallet(@org.springframework.data.repository.query.Param("id") Long id);
 Optional<User> findByEmailIgnoreCase(String email);
 boolean existsByEmailIgnoreCase(String email);
 List<User> findByRoleAndLockedFalseOrderByCreatedAtDesc(Role role);
 List<User> findByRoleOrderByCreatedAtDesc(Role role);
}
