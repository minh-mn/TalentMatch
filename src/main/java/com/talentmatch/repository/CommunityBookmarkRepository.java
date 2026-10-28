package com.talentmatch.repository;

import com.talentmatch.model.CommunityBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CommunityBookmarkRepository extends JpaRepository<CommunityBookmark, Long> {
    List<CommunityBookmark> findByUser_Id(Long userId);
    Optional<CommunityBookmark> findByQuestion_IdAndUser_Id(Long questionId, Long userId);
}
