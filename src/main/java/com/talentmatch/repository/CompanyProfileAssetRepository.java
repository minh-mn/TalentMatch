package com.talentmatch.repository;

import com.talentmatch.model.CompanyProfileAsset;
import com.talentmatch.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyProfileAssetRepository extends JpaRepository<CompanyProfileAsset, Long> {
    List<CompanyProfileAsset> findByOwnerOrderByCreatedAtDesc(User owner);
    Optional<CompanyProfileAsset> findByIdAndOwner(Long id, User owner);
    long countByOwnerAndKind(User owner, CompanyProfileAsset.Kind kind);
}
