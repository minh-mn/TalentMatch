package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface JobRepository extends JpaRepository<Job,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select j from Job j where j.id=:id")
 Optional<Job> findForUpdate(@Param("id") Long id);
 @Override
 @EntityGraph(attributePaths = "customer")
 List<Job> findAll();
 @EntityGraph(attributePaths = "customer")
 List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);
 @EntityGraph(attributePaths = "customer")
 List<Job> findByCustomerOrderByCreatedAtDesc(User customer);
 @EntityGraph(attributePaths = "customer")
 Optional<Job> findWithCustomerById(Long id);
 long countByStatus(JobStatus status);
}
