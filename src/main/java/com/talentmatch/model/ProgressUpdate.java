package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="progress_updates")
public class ProgressUpdate {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id") private Project project;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="milestone_id") private Milestone milestone;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private User author;
 @Column(nullable=false) private int percent;
 @Column(nullable=false,length=3000) private String note;
 @Column(name="evidence_url",length=500) private String evidenceUrl;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 public Long getId(){return id;}public Project getProject(){return project;}public void setProject(Project v){project=v;}
 public Milestone getMilestone(){return milestone;}public void setMilestone(Milestone v){milestone=v;}
 public User getAuthor(){return author;}public void setAuthor(User v){author=v;}
 public int getPercent(){return percent;}public void setPercent(int v){percent=v;}
 public String getNote(){return note;}public void setNote(String v){note=v;}
 public String getEvidenceUrl(){return evidenceUrl;}public void setEvidenceUrl(String v){evidenceUrl=v;}
 public LocalDateTime getCreatedAt(){return createdAt;}
}
