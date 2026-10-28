package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SavedJobRepository extends JpaRepository<SavedJob,Long>{
 Optional<SavedJob> findByFreelancerAndJob(User freelancer,Job job);
 boolean existsByFreelancerAndJob(User freelancer,Job job);
 List<SavedJob> findByFreelancerOrderByCreatedAtDesc(User freelancer);
}
