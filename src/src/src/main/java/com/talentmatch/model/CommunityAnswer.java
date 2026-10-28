package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="community_answers")
public class CommunityAnswer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="question_id") public CommunityQuestion question;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="author_id") public User author;
 @Column(nullable=false,columnDefinition="TEXT") public String body;
 public LocalDateTime createdAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();}
 public Long getId(){return id;} public User getAuthor(){return author;} public String getBody(){return body;} public LocalDateTime getCreatedAt(){return createdAt;}
}
