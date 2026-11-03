package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="agreement_change_requests")
public class AgreementChangeRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id") private Project project;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="requester_id") private User requester;
 @Column(name="base_version",nullable=false) private int baseVersion;
 @Column(name="proposed_terms",nullable=false,columnDefinition="TEXT") private String proposedTerms;
 @Column(name="reason",nullable=false,length=1000) private String reason;
 @Column(nullable=false,length=20) private String status="PENDING";
 @Column(name="response_reason",length=1000) private String responseReason;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="responded_at") private LocalDateTime respondedAt;
 public Long getId(){return id;} public Project getProject(){return project;}public void setProject(Project v){project=v;}
 public User getRequester(){return requester;}public void setRequester(User v){requester=v;}
 public int getBaseVersion(){return baseVersion;}public void setBaseVersion(int v){baseVersion=v;}
 public String getProposedTerms(){return proposedTerms;}public void setProposedTerms(String v){proposedTerms=v;}
 public String getReason(){return reason;}public void setReason(String v){reason=v;}
 public String getStatus(){return status;}public void setStatus(String v){status=v;}
 public String getResponseReason(){return responseReason;}public void setResponseReason(String v){responseReason=v;}
 public LocalDateTime getCreatedAt(){return createdAt;}public LocalDateTime getRespondedAt(){return respondedAt;}public void setRespondedAt(LocalDateTime v){respondedAt=v;}
}
