package com.talentmatch.controller;

import com.talentmatch.model.Role;
import com.talentmatch.service.MailService;
import com.talentmatch.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final UserService users;
    private final MailService mail;

    public AuthController(UserService users, MailService mail) {
        this.users = users;
        this.mail = mail;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String register(@RequestParam(required = false) String role, Model model) {
        model.addAttribute("preselectedRole", role);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam(required = false) String fullName,
                           @RequestParam(required = false) String firstName,
                           @RequestParam(required = false) String lastName,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam Role role,
                           @RequestParam(required = false) Boolean agreeTerms,
                           RedirectAttributes ra) {
        try {
            String finalName = buildName(fullName, firstName, lastName);
            if (!Boolean.TRUE.equals(agreeTerms)) {
                throw new IllegalArgumentException("Bạn cần đồng ý điều khoản trước khi đăng ký.");
            }
            if (!password.equals(confirmPassword)) {
                throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
            }
            users.registerAndSend(finalName, email, password, role);
            ra.addFlashAttribute("success", "Đã gửi OTP 6 số đến email. Mã có hiệu lực 5 phút.");
            return "redirect:/verify-otp?email=" + java.net.URLEncoder.encode(email.trim(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String email, @RequestParam String otp, RedirectAttributes ra) {
        try {
            users.verifyOtp(email, otp);
            ra.addFlashAttribute("success", "Xác minh thành công! Tài khoản đã được tạo. Hãy đăng nhập.");
            return "redirect:/login";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/verify-otp?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/forgot-password")
    public String forgot() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgot(@RequestParam String email, RedirectAttributes ra) {
        try {
            users.requestPasswordReset(email);
            ra.addFlashAttribute("success", "Nếu email đã đăng ký và được xác minh, OTP 6 số đã được gửi (hiệu lực 5 phút).");
            return "redirect:/reset-password?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Không gửi được email. Vui lòng kiểm tra cấu hình SMTP hoặc thử lại sau.");
        }
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String reset(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam String email, @RequestParam String otp,
                        @RequestParam String password, @RequestParam String confirmPassword,
                        RedirectAttributes ra) {
        try {
            if (!password.equals(confirmPassword))
                throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
            users.resetPasswordWithOtp(email, otp, password);
            ra.addFlashAttribute("success", "Đặt lại mật khẩu thành công. Hãy đăng nhập.");
            return "redirect:/login";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/reset-password?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String buildName(String fullName, String firstName, String lastName) {
        if (fullName != null && !fullName.trim().isBlank()) return fullName.trim();
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (last + " " + first).trim();
    }
}
