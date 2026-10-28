package com.talentmatch.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="milestones")
public class Milestone {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="project_id") private Project project;
    @Column(nullable=false,length=180) private String title;
    @Column(columnDefinition="TEXT") private String description;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
    private LocalDate deadline;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private MilestoneStatus status=MilestoneStatus.UNPAID;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    @Column(columnDefinition="TEXT") private String revisionNote;
    @PrePersist void prePersist(){createdAt=LocalDateTime.now();if(status==null)status=MilestoneStatus.UNPAID;}
    public String getRevisionNote(){return revisionNote;} public void setRevisionNote(String v){revisionNote=v;}
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Project getProject(){return project;} public void setProject(Project project){this.project=project;}
    public String getTitle(){return title;} public void setTitle(String title){this.title=title;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal amount){this.amount=amount;}
    public LocalDate getDeadline(){return deadline;} public void setDeadline(LocalDate deadline){this.deadline=deadline;}
    public MilestoneStatus getStatus(){return status;} public void setStatus(MilestoneStatus status){this.status=status;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime completedAt){this.completedAt=completedAt;}
}
