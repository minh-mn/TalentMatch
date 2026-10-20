package com.talentmatch.service;

import com.talentmatch.model.PasswordResetToken;
import com.talentmatch.model.Role;
import com.talentmatch.model.User;
import com.talentmatch.model.VerificationToken;
import com.talentmatch.repository.PasswordResetTokenRepository;
import com.talentmatch.repository.UserRepository;
import com.talentmatch.repository.VerificationTokenRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository users;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, VerificationTokenRepository verificationTokens,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String register(String fullName, String email, String password, Role role) {
        fullName = safe(fullName);
        email = safe(email).toLowerCase();
        if (fullName.length() < 2) throw new IllegalArgumentException("Họ tên phải có ít nhất 2 ký tự.");
        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) throw new IllegalArgumentException("Email không hợp lệ.");
        validatePassword(password);
        if (role == null || role == Role.ADMIN) throw new IllegalArgumentException("Vai trò đăng ký không hợp lệ.");

        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user != null && Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("Email này đã được sử dụng.");
        }
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setEnabled(false);
            user.setLocked(false);
        }
        user.setFullName(fullName);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(false);
        users.save(user);
        verificationTokens.deleteByUser(user);

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        verificationTokens.save(token);
        return token.getToken();
    }

    @Transactional
    public void verify(String tokenValue) {
        VerificationToken token = verificationTokens.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Liên kết xác nhận không hợp lệ."));
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Liên kết xác nhận đã hết hạn.");
        }
        User user = token.getUser();
        user.setEnabled(true);
        users.save(user);
        verificationTokens.delete(token);
    }

    @Transactional
    public String createResetToken(String email) {
        User user = users.findByEmailIgnoreCase(safe(email))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với email này."));
        for (PasswordResetToken old : resetTokens.findByUserAndUsedFalse(user)) {
            old.setUsed(true);
            resetTokens.save(old);
        }
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        token.setUsed(false);
        resetTokens.save(token);
        return token.getToken();
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        validatePassword(newPassword);
        PasswordResetToken token = resetTokens.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Liên kết đặt lại mật khẩu không hợp lệ."));
        if (Boolean.TRUE.equals(token.getUsed()) || token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Liên kết đặt lại mật khẩu đã hết hạn hoặc đã được sử dụng.");
        }
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        token.setUsed(true);
        resetTokens.save(token);
    }

    public User byEmail(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản."));
    }

    @Transactional
    public User updateProfile(String email, User form) {
        User user = byEmail(email);
        String fullName = safe(form.getFullName());
        if (fullName.length() < 2) throw new IllegalArgumentException("Họ tên không hợp lệ.");
        user.setFullName(fullName);
        user.setPhone(limit(form.getPhone(), 25));
        user.setAvatar(limit(form.getAvatar(), 500));
        user.setLocation(limit(form.getLocation(), 160));
        user.setWebsite(limit(form.getWebsite(), 255));
        user.setBio(limit(form.getBio(), 4000));

        if (user.getRole() == Role.FREELANCER) {
            user.setProfessionalTitle(limit(form.getProfessionalTitle(), 160));
            user.setSkills(limit(form.getSkills(), 1000));
            user.setField(limit(form.getField(), 120));
            user.setLinkedinUrl(limit(form.getLinkedinUrl(), 255));
            user.setExperienceYears(form.getExperienceYears() == null ? 0 : Math.max(0, form.getExperienceYears()));
            user.setHourlyRate(form.getHourlyRate());
            user.setAvailable(form.getAvailable() == null || form.getAvailable());
        } else if (user.getRole() == Role.CUSTOMER) {
            user.setCompanyName(limit(form.getCompanyName(), 160));
            user.setCompanySize(limit(form.getCompanySize(), 80));
            user.setContactName(limit(form.getContactName(), 120));
            user.setTaxCode(limit(form.getTaxCode(), 40));
            user.setField(limit(form.getField(), 120));
        }
        return users.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = byEmail(username);
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                .accountLocked(Boolean.TRUE.equals(user.getLocked()))
                .build();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8)
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 8 ký tự.");
        if (!password.matches(".*[A-Z].*"))
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất một chữ hoa.");
        if (!password.matches(".*[a-z].*"))
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất một chữ thường.");
        if (!password.matches(".*\\d.*"))
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất một chữ số.");
        if (!password.matches(".*[^A-Za-z0-9].*"))
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất một ký tự đặc biệt.");
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
    private String limit(String s, int max) { if (s == null) return null; s = s.trim(); return s.length() > max ? s.substring(0,max) : s; }
}
