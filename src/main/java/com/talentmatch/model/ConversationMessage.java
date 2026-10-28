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
 @Column(name="attachment_name",length=200) private String attachmentName;
 @Column(name="attachment_key",length=90) private String attachmentKey;
 @Column(name="attachment_size") private Long attachmentSize;
 @PrePersist void create(){createdAt=LocalDateTime.now();}
 public String getAttachmentName(){return attachmentName;}
 public void setAttachmentName(String s){attachmentName=s;}
 public String getAttachmentKey(){return attachmentKey;}
 public void setAttachmentKey(String s){attachmentKey=s;}
 public Long getAttachmentSize(){return attachmentSize;}
 public void setAttachmentSize(Long s){attachmentSize=s;}
 public boolean hasAttachment(){return attachmentKey!=null&&!attachmentKey.isBlank();}
 public Long getId(){return id;} public User getSender(){return sender;} public String getContent(){return content;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getReadAt(){return readAt;}
}
