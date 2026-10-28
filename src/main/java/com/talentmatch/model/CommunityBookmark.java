package com.talentmatch.model;

import jakarta.persistence.*;

@Entity
@Table(name = "community_bookmarks", uniqueConstraints =
        @UniqueConstraint(columnNames = {"question_id", "user_id"}))
public class CommunityBookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    public CommunityQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;
}
