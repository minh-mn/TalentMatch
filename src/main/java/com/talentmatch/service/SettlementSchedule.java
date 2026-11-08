package com.talentmatch.service;

import com.talentmatch.model.PaymentStatus;
import com.talentmatch.repository.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

/** Separates scheduled discovery from transactional settlement via Spring proxy. */
@Component
public class SettlementSchedule {
 private final PaymentRepository payments;
 private final SettlementDemoService settlement;
 public SettlementSchedule(PaymentRepository payments,SettlementDemoService settlement){
  this.payments=payments;this.settlement=settlement;
 }
 @Scheduled(fixedDelayString="${talentmatch.settlement.check-ms:60000}")
 public void process(){
  List<Long> ids=payments.findAll().stream()
      .filter(p->p.getStatus()==PaymentStatus.PAID && p.getAcceptedAt()!=null
           && !(p.getScheduledDisbursementAt()==null?p.getAcceptedAt().plusDays(settlement.holdDays()):p.getScheduledDisbursementAt()).isAfter(LocalDateTime.now()))
      .map(p->p.getId()).toList();
  for(Long id:ids){
   try { settlement.settleOne(id); }
   catch(RuntimeException e){
    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Demo settlement skipped payment {}: {}",id,e.getMessage());
   }
  }
 }
}
