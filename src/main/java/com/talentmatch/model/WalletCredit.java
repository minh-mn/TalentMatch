package com.talentmatch.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="wallet_credits",uniqueConstraints=@UniqueConstraint(name="uk_wallet_credit_payment",columnNames="payment_id"))
public class WalletCredit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="freelancer_id") private User freelancer;
 @OneToOne(optional=false) @JoinColumn(name="payment_id") private Payment payment;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal gross;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal fee;
 @Column(nullable=false,precision=15,scale=2) private BigDecimal net;
 @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 public Long getId(){return id;} public User getFreelancer(){return freelancer;} public void setFreelancer(User a){freelancer=a;}
 public Payment getPayment(){return payment;} public void setPayment(Payment a){payment=a;}
 public BigDecimal getGross(){return gross;} public void setGross(BigDecimal a){gross=a;}
 public BigDecimal getFee(){return fee;} public void setFee(BigDecimal a){fee=a;}
 public BigDecimal getNet(){return net;} public void setNet(BigDecimal a){net=a;}
 public LocalDateTime getCreatedAt(){return createdAt;}
}
