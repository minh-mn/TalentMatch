package com.talentmatch.repository;
import com.talentmatch.model.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog,Long> {
    List<AdminAuditLog> findTop30ByOrderByCreatedAtDesc();
}
