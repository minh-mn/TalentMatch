package com.talentmatch.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ho so tranh chap doc lap; tinh trang du an van duoc luu trong Project.status. */
@Entity
@Table(name="disputes", indexes={@Index(name="idx_dispute_project", columnList="project_id"),
        @Index(name="idx_dispute_status", columnList="status")})
public class Dispute {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id") private Project project;
    @Column(length=50) private String orderNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="opened_by") private User openedBy;
    @Column(nullable=false,length=255) private String reason;
    @Column(nullable=false,columnDefinition="TEXT") private String evidenceNotes;
    @Column(nullable=false,length=50) private String status="OPEN";
    @Column(nullable=false,precision=15,scale=2) private BigDecimal refundEmployerAmount=BigDecimal.ZERO;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal payoutFreelancerAmount=BigDecimal.ZERO;
    @Column(columnDefinition="TEXT") private String adminResolutionNote;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="resolved_by") private User resolvedBy;
    @Column(nullable=false) private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    @PrePersist void created(){if(createdAt==null)createdAt=LocalDateTime.now();}
    public Long getId(){return id;}
    public Project getProject(){return project;} public void setProject(Project v){project=v;}
    public String getOrderNumber(){return orderNumber;} public void setOrderNumber(String v){orderNumber=v;}
    public User getOpenedBy(){return openedBy;} public void setOpenedBy(User v){openedBy=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getEvidenceNotes(){return evidenceNotes;} public void setEvidenceNotes(String v){evidenceNotes=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public BigDecimal getRefundEmployerAmount(){return refundEmployerAmount;}
    public void setRefundEmployerAmount(BigDecimal v){refundEmployerAmount=v;}
    public BigDecimal getPayoutFreelancerAmount(){return payoutFreelancerAmount;}
    public void setPayoutFreelancerAmount(BigDecimal v){payoutFreelancerAmount=v;}
    public String getAdminResolutionNote(){return adminResolutionNote;} public void setAdminResolutionNote(String v){adminResolutionNote=v;}
    public User getResolvedBy(){return resolvedBy;} public void setResolvedBy(User v){resolvedBy=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getResolvedAt(){return resolvedAt;} public void setResolvedAt(LocalDateTime v){resolvedAt=v;}
}
