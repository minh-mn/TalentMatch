package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="notifications", indexes={@Index(name="idx_notification_user",columnList="recipient_id,created_at")})
public class Notification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id") private User recipient;
    @Column(nullable=false,length=180) private String title;
    @Column(nullable=false,length=1000) private String message;
    @Column(length=500) private String targetUrl;
    @Column(name="is_read",nullable=false) private boolean read=false;
    @Column(nullable=false) private LocalDateTime createdAt;
    @PrePersist void init(){if(createdAt==null)createdAt=LocalDateTime.now();}
    public Long getId(){return id;} public User getRecipient(){return recipient;} public void setRecipient(User v){recipient=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getTargetUrl(){return targetUrl;} public void setTargetUrl(String v){targetUrl=v;}
    public boolean isRead(){return read;} public void setRead(boolean v){read=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
