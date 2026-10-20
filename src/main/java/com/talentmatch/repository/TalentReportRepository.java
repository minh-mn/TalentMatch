package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TalentReportRepository extends JpaRepository<TalentReport,Long>{
 java.util.List<TalentReport> findAllByOrderByCreatedAtDesc();
 java.util.List<TalentReport> findByReporterOrderByCreatedAtDesc(User reporter);
}
