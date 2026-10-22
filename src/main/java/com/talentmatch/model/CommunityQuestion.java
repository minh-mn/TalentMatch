package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="community_questions")
public class CommunityQuestion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="author_id") public User author;
 @Column(nullable=false,length=220) public String title;
 @Column(nullable=false,columnDefinition="TEXT") public String body;
 @Column(nullable=false,length=60) public String category="Freelancer";
 public boolean solved=false;
 public LocalDateTime createdAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();}
 public Long getId(){return id;} public User getAuthor(){return author;} public String getTitle(){return title;} public String getBody(){return body;} public String getCategory(){return category;} public boolean isSolved(){return solved;} public LocalDateTime getCreatedAt(){return createdAt;}
}
