package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="verification_tokens")
public class VerificationToken {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=100) private String token;
 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",unique=true) private User user;
 private LocalDateTime expiresAt;
 public Long getId(){return id;} public void setId(Long id){this.id=id;}
 public String getToken(){return token;} public void setToken(String token){this.token=token;}
 public User getUser(){return user;} public void setUser(User user){this.user=user;}
 public LocalDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(LocalDateTime expiresAt){this.expiresAt=expiresAt;}
}
