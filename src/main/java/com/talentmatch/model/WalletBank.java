package com.talentmatch.model;
import jakarta.persistence.*;
@Entity @Table(name="wallet_banks")
public class WalletBank {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="freelancer_id") private User freelancer;
 @Column(nullable=false,length=120) private String bankName;
 @Column(nullable=false,length=120) private String holder;
 @Column(nullable=false,length=40) private String accountNumber;
 public Long getId(){return id;} public User getFreelancer(){return freelancer;} public void setFreelancer(User v){freelancer=v;}
 public String getBankName(){return bankName;} public void setBankName(String v){bankName=v;}
 public String getHolder(){return holder;} public void setHolder(String v){holder=v;}
 public String getAccountNumber(){return accountNumber;} public void setAccountNumber(String v){accountNumber=v;}
 public String getMasked(){return "•••• "+accountNumber.substring(Math.max(0,accountNumber.length()-4));}
}
