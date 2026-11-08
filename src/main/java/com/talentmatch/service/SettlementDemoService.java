package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Internal DEMO settlement. Never sends actual money to a bank. */
@Service
public class SettlementDemoService {
 private final PaymentRepository payments;
 private final ProjectRepository projects;
 private final WalletDemoService wallet;
 private final int holdDays;
 public SettlementDemoService(PaymentRepository payments,ProjectRepository projects,
    WalletDemoService wallet,@Value("${talentmatch.settlement.hold-days:3}") int holdDays){
   if(holdDays<0||holdDays>365)throw new IllegalArgumentException("Số ngày chờ giải ngân không hợp lệ");
   this.payments=payments;this.projects=projects;this.wallet=wallet;this.holdDays=holdDays;
 }
 public int holdDays(){return holdDays;}
 @Transactional
 public void settleOne(Long id){
   Payment before=payments.findById(id).orElseThrow();
   Project p=projects.findForUpdate(before.getProject().getId()).orElseThrow();
   Payment pay=payments.findById(id).orElseThrow();
   if(pay.getStatus()!=PaymentStatus.PAID || pay.getAcceptedAt()==null) return;
   if((pay.getScheduledDisbursementAt()==null?pay.getAcceptedAt().plusDays(holdDays):pay.getScheduledDisbursementAt()).isAfter(LocalDateTime.now()))return;
   if(p.getStatus()==ProjectStatus.DISPUTED||p.getStatus()==ProjectStatus.CANCELLED)
       throw new IllegalStateException("Dự án đang bị khóa bởi tranh chấp/hủy");
   if(pay.getMilestone()!=null){
      if(pay.getMilestone().getStatus()!=MilestoneStatus.COMPLETED)
          throw new IllegalStateException("Milestone chưa nghiệm thu");
   }else if(p.getStatus()!=ProjectStatus.COMPLETED)
      throw new IllegalStateException("Dự án trọn gói chưa nghiệm thu xong");
   pay.setStatus(PaymentStatus.DISBURSED);
   pay.setDisbursedAt(LocalDateTime.now());
   payments.saveAndFlush(pay);
   wallet.sync(p.getFreelancer());
 }
}
