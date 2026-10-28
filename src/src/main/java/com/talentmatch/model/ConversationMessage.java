package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="conversation_messages",indexes=@Index(name="idx_message_project_created",columnList="project_id,created_at"))
public class ConversationMessage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="project_id") public Project project;
 @ManyToOne(optional=false) @JoinColumn(name="sender_id") public User sender;
 @Column(nullable=false,length=4000) public String content;
 @Column(name="created_at") public LocalDateTime createdAt;
 @Column(name="read_at") public LocalDateTime readAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();}
 public Long getId(){return id;} public User getSender(){return sender;} public String getContent(){return content;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getReadAt(){return readAt;}
}
