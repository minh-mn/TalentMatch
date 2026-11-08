package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class WalletDemoService {
 private final WalletCreditRepository credits; private final WalletBankRepository banks;
 private final WalletWithdrawalRepository withdrawals; private final PaymentRepository payments;
 private final UserRepository users; private final BigDecimal feePercent;
 public WalletDemoService(WalletCreditRepository c,WalletBankRepository b,WalletWithdrawalRepository w,PaymentRepository p,UserRepository u,@Value("${talentmatch.wallet.demo-fee-percent:10}") BigDecimal fee){
  credits=c;banks=b;withdrawals=w;payments=p;users=u;feePercent=fee;
  if(fee.signum()<0||fee.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Phí demo không hợp lệ");
 }
 public BigDecimal feePercent(){return feePercent;}
 public record Summary(BigDecimal available,BigDecimal held,BigDecimal credited,BigDecimal withdrawn){}
 private User lockUser(User u){return users.lockForWallet(u.getId()).orElseThrow();}
 @Transactional
 public void sync(User freelancer){
  // Chỉ ghi có từ DISBURSED của luồng demo hiện hữu. UNIQUE(payment_id) chống tạo lại.
  for(Payment payment:payments.findAll()){
   if(payment.getStatus()!=PaymentStatus.DISBURSED||!payment.getProject().getFreelancer().getId().equals(freelancer.getId())||credits.existsByPayment(payment))continue;
   if(payment.getProject().getStatus()==ProjectStatus.DISPUTED)continue;
   BigDecimal gross=payment.getAmount();if(gross==null||gross.signum()<=0)continue;
   BigDecimal fee=gross.multiply(payment.getSettlementFeePercent()==null?feePercent:payment.getSettlementFeePercent()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
   WalletCredit credit=new WalletCredit();credit.setFreelancer(freelancer);credit.setPayment(payment);
   credit.setGross(gross);credit.setFee(fee);credit.setNet(gross.subtract(fee));credits.save(credit);
  }
 }
 @Transactional(readOnly=true)
 public Summary summary(User freelancer){
  BigDecimal credit=credits.findByFreelancerOrderByCreatedAtDesc(freelancer).stream().map(WalletCredit::getNet).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal held=BigDecimal.ZERO,withdrawn=BigDecimal.ZERO;
  for(WalletWithdrawal w:withdrawals.findByFreelancerOrderByCreatedAtDesc(freelancer)){
   if(w.getStatus()==WalletWithdrawal.State.COMPLETED)withdrawn=withdrawn.add(w.getAmount());
   else if(w.getStatus()==WalletWithdrawal.State.PENDING||w.getStatus()==WalletWithdrawal.State.APPROVED)held=held.add(w.getAmount());
  }
  return new Summary(credit.subtract(held).subtract(withdrawn),held,credit,withdrawn);
 }
 @Transactional(readOnly=true)
 public List<WalletCredit> creditsForProject(User freelancer,Project project){
  return credits.findByFreelancerOrderByCreatedAtDesc(freelancer).stream()
   .filter(c->c.getPayment()!=null&&c.getPayment().getProject()!=null&&c.getPayment().getProject().getId().equals(project.getId())).toList();
 }
 public List<WalletBank> banks(User u){return banks.findByFreelancerOrderByIdDesc(u);}
 public List<WalletWithdrawal> withdrawals(User u){return withdrawals.findByFreelancerOrderByCreatedAtDesc(u);}
 @Transactional public void addBank(User user,String bank,String holder,String account){
  if(user.getRole()!=Role.FREELANCER)throw new IllegalArgumentException("Không đúng vai trò.");
  if(bank==null||bank.isBlank()||bank.length()>120||holder==null||holder.isBlank()||holder.length()>120||account==null||!account.matches("[0-9]{6,24}"))throw new IllegalArgumentException("Thông tin ngân hàng không hợp lệ.");
  WalletBank a=new WalletBank();a.setFreelancer(user);a.setBankName(bank.trim());a.setHolder(holder.trim());a.setAccountNumber(account);banks.save(a);
 }
 @Transactional public void withdraw(User user,Long bankId,BigDecimal amount){
  lockUser(user); // khóa tiền theo freelancer trong transaction
  if(user.getRole()!=Role.FREELANCER)throw new IllegalArgumentException("Không đúng vai trò.");
  if(amount==null||amount.scale()>0||amount.precision()>15||amount.compareTo(new BigDecimal("10000"))<0)throw new IllegalArgumentException("Số tiền rút demo tối thiểu 10.000đ.");
  WalletBank bank=banks.findById(bankId).orElseThrow(()->new IllegalArgumentException("Không có ngân hàng."));
  if(!bank.getFreelancer().getId().equals(user.getId()))throw new IllegalArgumentException("Tài khoản không thuộc bạn.");
  if(summary(user).available().compareTo(amount)<0)throw new IllegalArgumentException("Không đủ số dư có thể rút.");
  WalletWithdrawal w=new WalletWithdrawal();w.setFreelancer(user);w.setBank(bank);w.setAmount(amount);withdrawals.save(w);
 }
 @Transactional public void review(Long id,boolean accept,String reason){
  WalletWithdrawal w=withdrawals.lock(id).orElseThrow();
  if(w.getStatus()!=WalletWithdrawal.State.PENDING)throw new IllegalArgumentException("Yêu cầu đã xử lý.");
  if(!accept&&(reason==null||reason.trim().length()<10))throw new IllegalArgumentException("Nhập lý do từ chối tối thiểu 10 ký tự.");
  w.setStatus(accept?WalletWithdrawal.State.APPROVED:WalletWithdrawal.State.REJECTED);
  w.setReason(accept?null:reason.trim());w.setUpdatedAt(LocalDateTime.now());withdrawals.save(w);
 }
 @Transactional public void complete(Long id){
  WalletWithdrawal w=withdrawals.lock(id).orElseThrow();
  if(w.getStatus()!=WalletWithdrawal.State.APPROVED)throw new IllegalArgumentException("Phải duyệt trước.");
  w.setStatus(WalletWithdrawal.State.COMPLETED);w.setUpdatedAt(LocalDateTime.now());withdrawals.save(w);
 }
 public List<WalletWithdrawal> allWithdrawals(){return withdrawals.findAllByOrderByCreatedAtDesc();}
}
