package com.talentmatch.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="quote_revisions", uniqueConstraints=@UniqueConstraint(name="uk_quote_application_version",columnNames={"application_id","version"}))
public class QuoteRevision {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="application_id") private JobApplication application;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private User author;
    @Column(nullable=false) private int version;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
    @Column(name="estimated_days",nullable=false) private int estimatedDays;
    @Column(nullable=false,length=5000) private String scope;
    @Column(name="revision_policy",nullable=false,length=1000) private String revisionPolicy;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private QuoteStatus status=QuoteStatus.PENDING;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="resolved_at") private LocalDateTime resolvedAt;
    @Column(name="rejection_reason",length=1000) private String rejectionReason;
    @PrePersist void onInsert(){if(createdAt==null)createdAt=LocalDateTime.now();}
    public Long getId(){return id;}
    public JobApplication getApplication(){return application;} public void setApplication(JobApplication v){application=v;}
    public User getAuthor(){return author;} public void setAuthor(User v){author=v;}
    public int getVersion(){return version;} public void setVersion(int v){version=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public int getEstimatedDays(){return estimatedDays;} public void setEstimatedDays(int v){estimatedDays=v;}
    public String getScope(){return scope;} public void setScope(String v){scope=v;}
    public String getRevisionPolicy(){return revisionPolicy;} public void setRevisionPolicy(String v){revisionPolicy=v;}
    public QuoteStatus getStatus(){return status;} public void setStatus(QuoteStatus v){status=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getResolvedAt(){return resolvedAt;} public void setResolvedAt(LocalDateTime v){resolvedAt=v;}
    public String getRejectionReason(){return rejectionReason;} public void setRejectionReason(String v){rejectionReason=v;}
}
