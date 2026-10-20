package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="job_questions")
public class JobQuestion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false)@JoinColumn(name="job_id") private Job job;
 @ManyToOne(optional=false)@JoinColumn(name="author_id")private User author;
 @Column(columnDefinition="TEXT",nullable=false) private String question;
 @Column(columnDefinition="TEXT") private String answer;
 private LocalDateTime createdAt;
 private LocalDateTime answeredAt;
 public Long getId(){return id;}public Job getJob(){return job;}public User getAuthor(){return author;}
 public String getQuestion(){return question;}public String getAnswer(){return answer;}public LocalDateTime getCreatedAt(){return createdAt;}
 public LocalDateTime getAnsweredAt(){return answeredAt;}
 public void setJob(Job v){job=v;}public void setAuthor(User v){author=v;}public void setQuestion(String v){question=v;}
 public void setAnswer(String v){answer=v;}public void setAnsweredAt(LocalDateTime v){answeredAt=v;}
 @PrePersist void init(){createdAt=LocalDateTime.now();}
}
