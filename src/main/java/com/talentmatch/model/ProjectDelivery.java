package com.talentmatch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A delivery is immutable. New revisions create new versions instead of overwriting files. */
@Entity
@Table(name="project_deliveries", indexes={@Index(name="idx_delivery_project",columnList="project_id")})
public class ProjectDelivery {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id") private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="milestone_id") private Milestone milestone;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="submitter_id") private User submitter;
    @Column(nullable=false) private int version;
    @Column(length=255) private String fileName;
    @Column(length=90) private String storageKey;
    @Column(length=500) private String externalUrl;
    @Column(columnDefinition="TEXT") private String note;
    @Column(nullable=false) private LocalDateTime submittedAt;
    @PrePersist void prePersist(){if(submittedAt==null)submittedAt=LocalDateTime.now();}
    public Long getId(){return id;} public Project getProject(){return project;} public void setProject(Project v){project=v;}
    public Milestone getMilestone(){return milestone;} public void setMilestone(Milestone v){milestone=v;}
    public User getSubmitter(){return submitter;} public void setSubmitter(User v){submitter=v;}
    public int getVersion(){return version;} public void setVersion(int v){version=v;}
    public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
    public String getStorageKey(){return storageKey;} public void setStorageKey(String v){storageKey=v;}
    public String getExternalUrl(){return externalUrl;} public void setExternalUrl(String v){externalUrl=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public LocalDateTime getSubmittedAt(){return submittedAt;}
}
