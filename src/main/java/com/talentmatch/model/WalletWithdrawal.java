package com.talentmatch.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="wallet_withdrawals")
public class WalletWithdrawal {
 public enum State { PENDING, APPROVED, PROCESSING, COMPLETED, REJECTED, FAILED, CANCELLED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="freelancer_id") private User freelancer;
 @ManyToOne(optional=false) @JoinColumn(name="bank_id") private WalletBank bank;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State status=State.PENDING;
 @Column(length=500) private String reason;
 @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 private LocalDateTime updatedAt;
 @Column(length=64, unique=true) private String payoutCode;
 public String getPayoutCode(){return payoutCode;} public void setPayoutCode(String v){payoutCode=v;}
 public boolean isHeld(){return status==State.PENDING||status==State.APPROVED||status==State.PROCESSING;}
 public String getStatusLabel(){return switch(status){case PENDING -> "Chờ xử lý";case APPROVED -> "Đã phê duyệt";case PROCESSING -> "Đang chuyển tiền mô phỏng";case COMPLETED -> "Đã hoàn thành (test)";case REJECTED -> "Đã từ chối";case FAILED -> "Chuyển tiền thất bại";case CANCELLED -> "Đã hủy";};}
 public Long getId(){return id;} public User getFreelancer(){return freelancer;} public void setFreelancer(User v){freelancer=v;}
 public WalletBank getBank(){return bank;} public void setBank(WalletBank v){bank=v;}
 public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
 public State getStatus(){return status;} public void setStatus(State v){status=v;}
 public String getReason(){return reason;} public void setReason(String v){reason=v;}
 public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime t){updatedAt=t;}
}
