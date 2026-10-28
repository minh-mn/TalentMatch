package com.talentmatch.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="job_invitations",uniqueConstraints=@UniqueConstraint(columnNames={"job_id","freelancer_id"}))
public class JobInvitation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="job_id") private Job job;
 @ManyToOne(optional=false) @JoinColumn(name="customer_id") private User customer;
 @ManyToOne(optional=false) @JoinColumn(name="freelancer_id") private User freelancer;
 @Column(nullable=false,length=20) private String status="SENT";
 @Column(length=500) private String message;
 private LocalDateTime createdAt;
 @PrePersist void prePersist(){createdAt=LocalDateTime.now();}
 public Long getId(){return id;} public Job getJob(){return job;} public User getCustomer(){return customer;} public User getFreelancer(){return freelancer;}
 public String getStatus(){return status;} public String getMessage(){return message;}public LocalDateTime getCreatedAt(){return createdAt;}
 public void setJob(Job v){job=v;}public void setCustomer(User v){customer=v;}public void setFreelancer(User v){freelancer=v;}
 public void setStatus(String v){status=v;}public void setMessage(String v){message=v;}
}
