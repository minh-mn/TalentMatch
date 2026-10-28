package com.talentmatch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Only pending signups live here; users are inserted after successful email verification. */
@Entity
@Table(name = "pending_registrations", indexes = @Index(name="idx_pending_expiry", columnList="expires_at"))
public class PendingRegistration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=160) private String email;
    @Column(name="full_name", nullable=false, length=120) private String fullName;
    @Column(name="password_hash", nullable=false, length=255) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Role role;
    @Column(name="token_hash", nullable=false, unique=true, length=64) private String tokenHash;
    @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
    @Column(name="failed_attempts", nullable=false) private int failedAttempts=0;
    @Column(name="sent_at") private LocalDateTime sentAt;
    public int getFailedAttempts(){return failedAttempts;} public void setFailedAttempts(int n){failedAttempts=n;}
    public LocalDateTime getSentAt(){return sentAt;} public void setSentAt(LocalDateTime t){sentAt=t;}
    public Long getId(){return id;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
    public Role getRole(){return role;} public void setRole(Role v){role=v;}
    public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;}
    public LocalDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(LocalDateTime v){expiresAt=v;}
}
