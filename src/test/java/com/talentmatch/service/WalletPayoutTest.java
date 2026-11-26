package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WalletPayoutTest {
 @Test void payoutMustBeApprovedAndProcessingBeforeCompletion(){
  WalletWithdrawalRepository repo=mock(WalletWithdrawalRepository.class);
  WalletWithdrawal w=new WalletWithdrawal(); w.setFreelancer(new User());
  when(repo.lock(1L)).thenReturn(Optional.of(w));
  WalletDemoService svc=new WalletDemoService(mock(WalletCreditRepository.class),mock(WalletBankRepository.class),repo,mock(PaymentRepository.class),mock(UserRepository.class),mock(NotificationService.class),BigDecimal.TEN);
  assertThrows(IllegalArgumentException.class,()->svc.startPayout(1L));
  svc.review(1L,true,null); assertTrue(w.isHeld());
  assertThrows(IllegalArgumentException.class,()->svc.finishPayout(1L,true,null));
  svc.startPayout(1L); assertTrue(w.isHeld()); assertNotNull(w.getPayoutCode());
  svc.finishPayout(1L,true,null); assertFalse(w.isHeld()); assertEquals(WalletWithdrawal.State.COMPLETED,w.getStatus());
  assertThrows(IllegalArgumentException.class,()->svc.finishPayout(1L,true,null));
 }
 @Test void failedPayoutReleasesHeldAmount(){
  WalletWithdrawalRepository repo=mock(WalletWithdrawalRepository.class);
  WalletWithdrawal w=new WalletWithdrawal();w.setFreelancer(new User());w.setStatus(WalletWithdrawal.State.PROCESSING);
  when(repo.lock(2L)).thenReturn(Optional.of(w));
  WalletDemoService svc=new WalletDemoService(mock(WalletCreditRepository.class),mock(WalletBankRepository.class),repo,mock(PaymentRepository.class),mock(UserRepository.class),mock(NotificationService.class),BigDecimal.TEN);
  svc.finishPayout(2L,false,"Ngân hàng test từ chối giao dịch");assertFalse(w.isHeld());assertEquals(WalletWithdrawal.State.FAILED,w.getStatus());
 }
}
