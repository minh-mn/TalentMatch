package com.talentmatch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Lịch sử thao tác kiểm duyệt; không dùng để thay thế nhật ký giao dịch ngân hàng. */
@Entity
@Table(name = "admin_audit_logs", indexes = @Index(name = "idx_admin_audit_created", columnList = "created_at"))
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="actor_id",nullable=false) private Long actorId;
    @Column(name="actor_email",nullable=false,length=160) private String actorEmail;
    @Column(nullable=false,length=70) private String action;
    @Column(name="target_type",nullable=false,length=45) private String targetType;
    @Column(name="target_id",nullable=false) private Long targetId;
    @Column(length=600) private String details;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @PrePersist void init(){if(createdAt==null)createdAt=LocalDateTime.now();}
    public Long getId(){return id;}
    public Long getActorId(){return actorId;}
    public void setActorId(Long v){actorId=v;}
    public String getActorEmail(){return actorEmail;}
    public void setActorEmail(String v){actorEmail=v;}
    public String getAction(){return action;}
    public void setAction(String v){action=v;}
    public String getTargetType(){return targetType;}
    public void setTargetType(String v){targetType=v;}
    public Long getTargetId(){return targetId;}
    public void setTargetId(Long v){targetId=v;}
    public String getDetails(){return details;}
    public void setDetails(String v){details=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
