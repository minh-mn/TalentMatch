package com.talentmatch.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "application_id", unique = true) private JobApplication application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id") private User customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "freelancer_id") private User freelancer;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal totalPrice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ProjectStatus status = ProjectStatus.WAITING_PAYMENT;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime customerSignedAt;
    private LocalDateTime freelancerSignedAt;
    @Column(columnDefinition="TEXT") private String cancellationReason;
    @Column(nullable=false) private boolean creditAwarded=false;
    @PrePersist void prePersist(){createdAt=LocalDateTime.now();updatedAt=createdAt;if(status==null)status=ProjectStatus.WAITING_PAYMENT;}
    @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public JobApplication getApplication(){return application;} public void setApplication(JobApplication application){this.application=application;}
    public User getCustomer(){return customer;} public void setCustomer(User customer){this.customer=customer;}
    public User getFreelancer(){return freelancer;} public void setFreelancer(User freelancer){this.freelancer=freelancer;}
    public BigDecimal getTotalPrice(){return totalPrice;} public void setTotalPrice(BigDecimal totalPrice){this.totalPrice=totalPrice;}
    public ProjectStatus getStatus(){return status;} public void setStatus(ProjectStatus status){this.status=status;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
    public LocalDateTime getCustomerSignedAt(){return customerSignedAt;} public void setCustomerSignedAt(LocalDateTime v){customerSignedAt=v;}
    public LocalDateTime getFreelancerSignedAt(){return freelancerSignedAt;} public void setFreelancerSignedAt(LocalDateTime v){freelancerSignedAt=v;}
    public boolean isAgreementSigned(){return customerSignedAt!=null&&freelancerSignedAt!=null;}
    public String getCancellationReason(){return cancellationReason;} public void setCancellationReason(String v){cancellationReason=v;}
    public boolean isCreditAwarded(){return creditAwarded;} public void setCreditAwarded(boolean v){creditAwarded=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime completedAt){this.completedAt=completedAt;}
}
