package com.talentmatch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Hình ảnh/logo/tài liệu pháp lý thuộc riêng một doanh nghiệp. */
@Entity
@Table(name = "company_profile_assets", indexes = @Index(name = "idx_company_asset_owner", columnList = "owner_id"))
public class CompanyProfileAsset {
    public enum Kind { LOGO, PHOTO, DOCUMENT }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Kind kind;
    @Column(nullable = false, length = 70)
    private String fileKey;
    @Column(nullable = false, length = 160)
    private String displayName;
    @Column(nullable = false, length = 40)
    private String contentType;
    @Column(nullable = false)
    private long fileSize;
    private LocalDateTime createdAt;
    @PrePersist public void createDate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String key) { this.fileKey = key; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }
    public String getContentType() { return contentType; }
    public void setContentType(String type) { this.contentType = type; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long bytes) { this.fileSize = bytes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
