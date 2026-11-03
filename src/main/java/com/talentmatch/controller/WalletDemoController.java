package com.talentmatch.controller;
import com.talentmatch.model.*;
import com.talentmatch.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
@Controller
public class WalletDemoController {
 private final WalletDemoService wallet;private final UserService users;
 public WalletDemoController(WalletDemoService wallet,UserService users){this.wallet=wallet;this.users=users;}
 @PostMapping("/freelancer/earnings/banks")
 public String bank(Authentication auth,@RequestParam String bankName,@RequestParam String holder,@RequestParam String accountNumber,@RequestParam(required=false,defaultValue="banks") String returnTo,RedirectAttributes a){
  try{wallet.addBank(users.byEmail(auth.getName()),bankName,holder,accountNumber);a.addFlashAttribute("success","Đã lưu ngân hàng cho mô phỏng; chưa xác minh sở hữu.");}
  catch(Exception e){a.addFlashAttribute("error",e.getMessage());}
  return "redirect:/freelancer/earnings?tab="+("withdrawals".equals(returnTo)?"withdrawals":"banks");
 }
 @PostMapping("/freelancer/earnings/withdrawals")
 public String withdraw(Authentication auth,@RequestParam Long bankId,@RequestParam BigDecimal amount,RedirectAttributes a){
  try{wallet.withdraw(users.byEmail(auth.getName()),bankId,amount);a.addFlashAttribute("success","Đã giữ số dư và tạo yêu cầu rút DEMO.");}
  catch(Exception e){a.addFlashAttribute("error",e.getMessage());}
  return "redirect:/freelancer/earnings?tab=withdrawals";
 }
 @GetMapping("/admin/wallet-withdrawals")
 public String admin(Model model){model.addAttribute("requests",wallet.allWithdrawals());return "admin/wallet-withdrawals";}
 @PostMapping("/admin/wallet-withdrawals/{id}/review")
 public String review(@PathVariable Long id,@RequestParam boolean accept,@RequestParam(required=false)String reason,RedirectAttributes a){
  try{wallet.review(id,accept,reason);a.addFlashAttribute("success","Đã cập nhật yêu cầu rút DEMO.");}
  catch(Exception e){a.addFlashAttribute("error",e.getMessage());}
  return "redirect:/admin/wallet-withdrawals";
 }
 @PostMapping("/admin/wallet-withdrawals/{id}/complete")
 public String complete(@PathVariable Long id,RedirectAttributes a){
  try{wallet.complete(id);a.addFlashAttribute("success","Đã xác nhận hoàn tất CHUYỂN TIỀN MÔ PHỎNG (không có tiền thật).");}
  catch(Exception e){a.addFlashAttribute("error",e.getMessage());}
  return "redirect:/admin/wallet-withdrawals";
 }
}
