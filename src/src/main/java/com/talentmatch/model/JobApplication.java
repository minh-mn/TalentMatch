package com.talentmatch.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id","freelancer_id"}))
public class JobApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id") private Job job;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "freelancer_id") private User freelancer;
    @Column(nullable = false, columnDefinition = "TEXT") private String proposal;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal proposedPrice;
    @Column(nullable = false) private Integer estimatedDays;
    @Column(length = 500) private String portfolioLink;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ApplicationStatus status = ApplicationStatus.SUBMITTED;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void prePersist(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; if(status==null)status=ApplicationStatus.SUBMITTED; }
    @PreUpdate void preUpdate(){ updatedAt=LocalDateTime.now(); }
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Job getJob(){return job;} public void setJob(Job job){this.job=job;}
    public User getFreelancer(){return freelancer;} public void setFreelancer(User freelancer){this.freelancer=freelancer;}
    public String getProposal(){return proposal;} public void setProposal(String proposal){this.proposal=proposal;}
    public BigDecimal getProposedPrice(){return proposedPrice;} public void setProposedPrice(BigDecimal proposedPrice){this.proposedPrice=proposedPrice;}
    public Integer getEstimatedDays(){return estimatedDays;} public void setEstimatedDays(Integer estimatedDays){this.estimatedDays=estimatedDays;}
    public String getPortfolioLink(){return portfolioLink;} public void setPortfolioLink(String portfolioLink){this.portfolioLink=portfolioLink;}
    public ApplicationStatus getStatus(){return status;} public void setStatus(ApplicationStatus status){this.status=status;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
