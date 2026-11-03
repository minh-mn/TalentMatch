package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProgressUpdateRepository extends JpaRepository<ProgressUpdate,Long> {
 List<ProgressUpdate> findByProjectOrderByCreatedAtDesc(Project project);
}
