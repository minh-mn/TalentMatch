package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface JobRepository extends JpaRepository<Job,Long>{
 List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);
 List<Job> findByCustomerOrderByCreatedAtDesc(User customer);
 long countByStatus(JobStatus status);
}
