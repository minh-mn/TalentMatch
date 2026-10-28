package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="talent_reports")
public class TalentReport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;
 @ManyToOne(optional=false)@JoinColumn(name="reporter_id")private User reporter;
 @ManyToOne(optional=false)@JoinColumn(name="reported_user_id")private User reportedUser;
 @ManyToOne @JoinColumn(name="project_id")private Project project;
 @Column(nullable=false,length=120)private String reason;
 @Column(nullable=false,columnDefinition="TEXT")private String description;
 @Column(nullable=false,length=24)private String status="PENDING";
 @Column(columnDefinition="TEXT")private String adminNote;
 private LocalDateTime createdAt;
 @PrePersist void init(){createdAt=LocalDateTime.now();}
 public Long getId(){return id;}public User getReporter(){return reporter;}public User getReportedUser(){return reportedUser;}
 public Project getProject(){return project;}public String getReason(){return reason;}public String getDescription(){return description;}
 public String getStatus(){return status;}public String getAdminNote(){return adminNote;}public LocalDateTime getCreatedAt(){return createdAt;}
 public void setReporter(User v){reporter=v;}public void setReportedUser(User v){reportedUser=v;}public void setProject(Project v){project=v;}
 public void setReason(String v){reason=v;}public void setDescription(String v){description=v;}
 public void setStatus(String v){status=v;}public void setAdminNote(String v){adminNote=v;}
}
