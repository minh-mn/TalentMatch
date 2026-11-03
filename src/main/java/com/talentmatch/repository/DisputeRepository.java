package com.talentmatch.repository;

import com.talentmatch.model.Dispute;
import com.talentmatch.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DisputeRepository extends JpaRepository<Dispute,Long> {
    List<Dispute> findByProjectOrderByCreatedAtDesc(Project project);
}
