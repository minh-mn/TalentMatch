package com.talentmatch.controller;

import com.talentmatch.model.User;
import com.talentmatch.service.NotificationService;
import com.talentmatch.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;

@ControllerAdvice
public class GlobalControllerAdvice {
    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);
    private static final String GENERIC_ERROR = "Đã xảy ra lỗi. Vui lòng thử lại sau.";

    private final UserService users;
    private final NotificationService notifications;

    public GlobalControllerAdvice(UserService users, NotificationService notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Authentication auth, org.springframework.ui.Model model) {
        model.addAttribute("unreadNotificationCount", 0L);
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return null;
        try {
            User user = users.byEmail(auth.getName());
            model.addAttribute("unreadNotificationCount", notifications.unread(user));
            return user;
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String businessError(RuntimeException exception, Authentication auth, HttpServletRequest request,
                                RedirectAttributes redirect) {
        String message = exception.getMessage();
        redirect.addFlashAttribute("error", message == null || message.isBlank()
                ? "Yêu cầu chưa thể thực hiện. Vui lòng kiểm tra thông tin và thử lại."
                : message);
        return redirectToSafeReferer(request, auth);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            BindException.class
    })
    public String invalidInput(Exception exception, Authentication auth, HttpServletRequest request,
                               RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Thông tin gửi lên chưa hợp lệ. Vui lòng kiểm tra và thử lại.");
        return redirectToSafeReferer(request, auth);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView accessDenied() {
        return errorView("error/403", HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView responseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return errorView("error/404", status, "Không tìm thấy nội dung bạn yêu cầu.");
        }
        if (status == HttpStatus.FORBIDDEN) {
            return errorView("error/403", status, "Bạn không có quyền thực hiện thao tác này.");
        }
        return errorView("error", status, "Yêu cầu chưa thể thực hiện.");
    }

    @ExceptionHandler({NoSuchElementException.class, EntityNotFoundException.class})
    public ModelAndView missingResource() {
        return errorView("error/404", HttpStatus.NOT_FOUND, "Không tìm thấy nội dung bạn yêu cầu.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String dataIntegrityFailure(DataIntegrityViolationException exception, Authentication auth,
                                       HttpServletRequest request, RedirectAttributes redirect) {
        log.error("Dữ liệu không hợp lệ khi xử lý {} {}", request.getMethod(), request.getRequestURI(), exception);
        redirect.addFlashAttribute("error", "Dữ liệu không thể lưu do xung đột. Vui lòng tải lại trang và thử lại.");
        return redirectToSafeReferer(request, auth);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpectedError(Exception exception) {
        String errorId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.error("Lỗi không xử lý được, errorId={}", errorId, exception);
        ModelAndView view = errorView("error", HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR);
        view.addObject("errorId", errorId);
        return view;
    }

    private String redirectToSafeReferer(HttpServletRequest request, Authentication auth) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getRawPath();
                boolean safePath = path != null && path.startsWith("/") && !path.startsWith("//")
                        && !path.contains("\\") && !path.startsWith("/error");
                if (safePath && uri.getRawUserInfo() == null && uri.getRawFragment() == null) {
                    if (uri.isAbsolute()) {
                        boolean sameHost = uri.getHost() != null
                                && uri.getHost().equalsIgnoreCase(request.getServerName())
                                && effectivePort(uri) == request.getServerPort()
                                && (uri.getScheme().equalsIgnoreCase("http")
                                || uri.getScheme().equalsIgnoreCase("https"));
                        if (!sameHost) return roleHome(auth);
                    }
                    return "redirect:" + path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid Referer values are not trusted as redirect destinations.
            }
        }
        return roleHome(auth);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String roleHome(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            var roles = AuthorityUtils.authorityListToSet(auth.getAuthorities());
            if (roles.contains("ROLE_ADMIN")) return "redirect:/admin/dashboard";
            if (roles.contains("ROLE_CUSTOMER")) return "redirect:/customer/dashboard";
            if (roles.contains("ROLE_FREELANCER")) return "redirect:/freelancer/dashboard";
        }
        return "redirect:/";
    }

    private ModelAndView errorView(String viewName, HttpStatus status, String message) {
        ModelAndView view = new ModelAndView(viewName);
        view.setStatus(status);
        view.addObject("message", message);
        return view;
    }
}
