package com.talentmatch.service;

import com.talentmatch.model.PasswordResetToken;
import com.talentmatch.model.PendingRegistration;
import com.talentmatch.model.Role;
import com.talentmatch.model.User;
import com.talentmatch.model.VerificationToken;
import com.talentmatch.repository.PasswordResetTokenRepository;
import com.talentmatch.repository.PendingRegistrationRepository;
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
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class UserService implements UserDetailsService {
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private final UserRepository users;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PendingRegistrationRepository pendingRegistrations;
    private final MailService mail;

    public UserService(UserRepository users, VerificationTokenRepository verificationTokens,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder,
                       PendingRegistrationRepository pendingRegistrations, MailService mail) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.pendingRegistrations = pendingRegistrations;
        this.mail = mail;
    }

    /** Transaction rolls back if SMTP fails. No User record exists before email verification. */
    @Transactional
    public void registerAndSend(String fullName, String email, String password, Role role) {
        fullName = safe(fullName);
        email = safe(email).toLowerCase();
        if (fullName.length() < 2) throw new IllegalArgumentException("Họ tên phải có ít nhất 2 ký tự.");
        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
            throw new IllegalArgumentException("Email không hợp lệ.");
        validatePassword(password);
        if (role == null || role == Role.ADMIN) throw new IllegalArgumentException("Vai trò đăng ký không hợp lệ.");
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new IllegalArgumentException("Email này đã có tài khoản. Vui lòng đăng nhập hoặc liên hệ hỗ trợ.");
        PendingRegistration pending = pendingRegistrations.findByEmailIgnoreCase(email).orElseGet(PendingRegistration::new);
        if (pending.getSentAt() != null && pending.getSentAt().isAfter(LocalDateTime.now().minusSeconds(60)))
            throw new IllegalArgumentException("Vui lòng đợi 60 giây trước khi yêu cầu OTP mới.");
        String rawToken = newOtp();
        pending.setEmail(email);
        pending.setFullName(fullName);
        pending.setPasswordHash(passwordEncoder.encode(password));
        pending.setRole(role);
        pending.setTokenHash(passwordEncoder.encode(rawToken));
        pending.setFailedAttempts(0);
        pending.setSentAt(LocalDateTime.now());
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        pendingRegistrations.saveAndFlush(pending);
        mail.sendVerificationOtp(email, rawToken); // SMTP failure -> transaction rollback
    }

    /** Six-digit OTP: only verified signups become real users. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyOtp(String email, String otp) {
        PendingRegistration pending = pendingRegistrations.findByEmailIgnoreCase(safe(email).toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu đăng ký. Vui lòng đăng ký lại."));
        if (!pending.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("OTP đã hết hạn 5 phút. Vui lòng đăng ký lại để nhận mã mới.");
        if (pending.getFailedAttempts() >= 5)
            throw new IllegalArgumentException("Bạn đã nhập sai quá 5 lần. Vui lòng đăng ký lại.");
        if (otp == null || !otp.matches("\\d{6}") || !passwordEncoder.matches(otp, pending.getTokenHash())) {
            pending.setFailedAttempts(pending.getFailedAttempts() + 1);
            pendingRegistrations.saveAndFlush(pending);
            throw new IllegalArgumentException("OTP không đúng. Bạn còn " + (5-pending.getFailedAttempts()) + " lần thử.");
        }
        if (users.findByEmailIgnoreCase(pending.getEmail()).isPresent())
            throw new IllegalArgumentException("Email này đã tồn tại trong hệ thống.");
        User user = new User();
        user.setEmail(pending.getEmail());
        user.setFullName(pending.getFullName());
        user.setPassword(pending.getPasswordHash());
        user.setRole(pending.getRole());
        user.setEnabled(true);
        user.setLocked(false);
        users.saveAndFlush(user);
        pendingRegistrations.delete(pending);
    }

    /** Legacy verification links are no longer accepted by new registrations. */
    @Transactional
    public void verify(String tokenValue) {
        throw new IllegalArgumentException("Vui lòng xác minh bằng OTP 6 số được gửi qua email.");
    }

    private String newOtp() {
        return String.format(java.util.Locale.ROOT, "%06d", OTP_RANDOM.nextInt(1_000_000));
    }

    /** Password-reset mail is sent in the same transaction as token issuance. */
    @Transactional
    public void requestPasswordReset(String email) {
        User account = users.findByEmailIgnoreCase(safe(email)).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) return;
        String token = createResetToken(account.getEmail());
        mail.sendResetOtp(account.getEmail(), token); // SMTP failures roll back token changes
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
        String otp = newOtp();
        token.setToken(passwordEncoder.encode(otp));
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setUsed(false);
        token.setFailedAttempts(0);
        resetTokens.save(token);
        return otp;
    }

    /** Verify reset OTP before showing the new password form. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyResetOtp(String email, String otp) {
        User user = users.findByEmailIgnoreCase(safe(email)).orElseThrow(
                () -> new IllegalArgumentException("Yêu cầu không hợp lệ hoặc đã hết hạn."));
        PasswordResetToken token = currentResetToken(user);
        checkResetOtp(token, otp);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPasswordWithOtp(String email, String otp, String newPassword) {
        validatePassword(newPassword);
        User user = users.findByEmailIgnoreCase(safe(email)).orElseThrow(
                () -> new IllegalArgumentException("Yêu cầu không hợp lệ hoặc đã hết hạn."));
        PasswordResetToken token = currentResetToken(user);
        checkResetOtp(token, otp);
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        token.setUsed(true);
        resetTokens.save(token);
    }

    private PasswordResetToken currentResetToken(User user) {
        return resetTokens.findByUserAndUsedFalse(user).stream()
                .max(java.util.Comparator.comparing(PasswordResetToken::getId))
                .orElseThrow(() -> new IllegalArgumentException("OTP không hợp lệ. Hãy yêu cầu mã mới."));
    }

    private void checkResetOtp(PasswordResetToken token, String otp) {
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("OTP đã hết hạn 5 phút. Hãy yêu cầu mã mới.");
        if (token.getFailedAttempts() >= 5)
            throw new IllegalArgumentException("Bạn đã nhập sai quá 5 lần. Hãy yêu cầu mã mới.");
        if (otp == null || !otp.matches("\\d{6}") || !passwordEncoder.matches(otp, token.getToken())) {
            token.setFailedAttempts(token.getFailedAttempts() + 1);
            resetTokens.saveAndFlush(token);
            throw new IllegalArgumentException("OTP không đúng. Bạn còn " + (5-token.getFailedAttempts()) + " lần thử.");
        }
    }

    /** Old token-based URLs are intentionally disabled for OTP-only reset. */
    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        throw new IllegalArgumentException("Vui lòng sử dụng OTP được gửi qua email.");
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
