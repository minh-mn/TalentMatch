package com.talentmatch.repository;

import com.talentmatch.model.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration,Long> {
    Optional<PendingRegistration> findByEmailIgnoreCase(String email);
    Optional<PendingRegistration> findByTokenHash(String tokenHash);
}
