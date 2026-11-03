package com.talentmatch.repository;
import com.talentmatch.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AgreementRevisionRepository extends JpaRepository<AgreementRevision,Long>{
 List<AgreementRevision> findByProjectOrderByVersionDesc(Project project);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from AgreementRevision a where a.project.id=:projectId and a.version=:version")
 Optional<AgreementRevision> lockVersion(@Param("projectId") Long projectId,@Param("version") int version);
 Optional<AgreementRevision> findTopByProjectOrderByVersionDesc(Project project);
}
