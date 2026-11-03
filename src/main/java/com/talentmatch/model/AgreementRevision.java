package com.talentmatch.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="agreement_revisions",uniqueConstraints=@UniqueConstraint(name="uk_agreement_project_version",columnNames={"project_id","version"}))
public class AgreementRevision {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id") private Project project;
 @Column(nullable=false) private int version;
 @Column(nullable=false,columnDefinition="TEXT") private String terms;
 @Column(name="scope_of_work",nullable=false,columnDefinition="TEXT") private String scopeOfWork;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
 @Column(name="estimated_days",nullable=false) private int estimatedDays;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="customer_signed_at") private LocalDateTime customerSignedAt;
 @Column(name="freelancer_signed_at") private LocalDateTime freelancerSignedAt;
 public Long getId(){return id;} public Project getProject(){return project;}public void setProject(Project p){project=p;}
 public int getVersion(){return version;}public void setVersion(int v){version=v;}
 public String getTerms(){return terms;}public void setTerms(String v){terms=v;}
 public String getScopeOfWork(){return scopeOfWork;}public void setScopeOfWork(String v){scopeOfWork=v;}
 public BigDecimal getAmount(){return amount;}public void setAmount(BigDecimal v){amount=v;}
 public int getEstimatedDays(){return estimatedDays;}public void setEstimatedDays(int v){estimatedDays=v;}
 public LocalDateTime getCreatedAt(){return createdAt;}
 public LocalDateTime getCustomerSignedAt(){return customerSignedAt;}public void setCustomerSignedAt(LocalDateTime v){customerSignedAt=v;}
 public LocalDateTime getFreelancerSignedAt(){return freelancerSignedAt;}public void setFreelancerSignedAt(LocalDateTime v){freelancerSignedAt=v;}
}
