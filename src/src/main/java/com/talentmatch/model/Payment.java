package com.talentmatch.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="payments")
public class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="project_id") private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="milestone_id") private Milestone milestone;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="payer_id") private User payer;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=40) private String method;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PaymentStatus status=PaymentStatus.PENDING;
    @Column(nullable=false,unique=true,length=80) private String transactionCode;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime disbursedAt;
    @PrePersist void prePersist(){createdAt=LocalDateTime.now();if(status==null)status=PaymentStatus.PENDING;}
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Project getProject(){return project;} public void setProject(Project project){this.project=project;}
    public Milestone getMilestone(){return milestone;} public void setMilestone(Milestone milestone){this.milestone=milestone;}
    public User getPayer(){return payer;} public void setPayer(User payer){this.payer=payer;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal amount){this.amount=amount;}
    public String getMethod(){return method;} public void setMethod(String method){this.method=method;}
    public PaymentStatus getStatus(){return status;} public void setStatus(PaymentStatus status){this.status=status;}
    public String getTransactionCode(){return transactionCode;} public void setTransactionCode(String transactionCode){this.transactionCode=transactionCode;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getPaidAt(){return paidAt;} public void setPaidAt(LocalDateTime paidAt){this.paidAt=paidAt;}
    public LocalDateTime getDisbursedAt(){return disbursedAt;} public void setDisbursedAt(LocalDateTime disbursedAt){this.disbursedAt=disbursedAt;}
}
