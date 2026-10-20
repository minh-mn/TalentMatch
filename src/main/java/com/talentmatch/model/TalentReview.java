package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="talent_reviews",uniqueConstraints=@UniqueConstraint(columnNames={"project_id","author_id"}))
public class TalentReview {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;
 @ManyToOne(optional=false)@JoinColumn(name="project_id")private Project project;
 @ManyToOne(optional=false)@JoinColumn(name="author_id")private User author;
 @ManyToOne(optional=false)@JoinColumn(name="target_id")private User target;
 @Column(nullable=false)private Integer rating;
 @Column(nullable=false,length=1000)private String comment;
 private LocalDateTime createdAt;
 @PrePersist void init(){createdAt=LocalDateTime.now();}
 public Long getId(){return id;}public Project getProject(){return project;}public User getAuthor(){return author;}public User getTarget(){return target;}
 public Integer getRating(){return rating;}public String getComment(){return comment;}public LocalDateTime getCreatedAt(){return createdAt;}
 public void setProject(Project v){project=v;}public void setAuthor(User v){author=v;}public void setTarget(User v){target=v;}
 public void setRating(Integer v){rating=v;}public void setComment(String v){comment=v;}
}
