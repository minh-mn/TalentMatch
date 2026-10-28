package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<User,Long>{
 Optional<User> findByEmailIgnoreCase(String email);
 boolean existsByEmailIgnoreCase(String email);
 List<User> findByRoleAndLockedFalseOrderByCreatedAtDesc(Role role);
 List<User> findByRoleOrderByCreatedAtDesc(Role role);
}
