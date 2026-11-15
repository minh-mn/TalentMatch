package com.talentmatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}

    @Bean public AuthenticationSuccessHandler successHandler(){
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        SavedRequestAwareAuthenticationSuccessHandler savedRequestHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        savedRequestHandler.setRequestCache(requestCache);
        return (req,res,auth)->{
            if (requestCache.getRequest(req, res) != null) {
                savedRequestHandler.onAuthenticationSuccess(req, res, auth);
                return;
            }
            boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));

            // Admin vào khu quản trị; Freelancer/Customer sau khi đăng nhập quay về Marketplace.
            // Workspace được truy cập riêng từ menu tài khoản / Bảng điều khiển.
            boolean freelancer=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_FREELANCER"));
            res.sendRedirect(admin ? "/admin/dashboard" : (freelancer ? "/freelancer/dashboard" : "/customer/dashboard"));
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,AuthenticationSuccessHandler successHandler,
                                           @Value("${app.security.remember-me-key:${random.uuid}}") String rememberMeKey)throws Exception{
        http.authorizeHttpRequests(auth->auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers("/","/login","/register","/verify","/verify-otp","/forgot-password","/reset-password","/css/**","/js/**","/images/**","/error","/error/**","/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.GET,"/jobs/*/document").authenticated()
                .requestMatchers(HttpMethod.GET,"/jobs/**","/freelancers/**","/hire","/projects").permitAll()
                .requestMatchers("/payment/vnpay/ipn", "/payment/vnpay/return").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/customer/**").hasRole("CUSTOMER")
                .requestMatchers("/freelancer/community/**").hasAnyRole("FREELANCER", "CUSTOMER")
                .requestMatchers("/freelancer/**").hasRole("FREELANCER")
                .requestMatchers("/projects/**","/payment/**").authenticated()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions->exceptions.accessDeniedPage("/error/403"))
            .formLogin(form->form.loginPage("/login").loginProcessingUrl("/login").usernameParameter("email").passwordParameter("password")
                    .successHandler(successHandler).failureUrl("/login?error").permitAll())
            .logout(logout->logout.logoutUrl("/logout").logoutSuccessUrl("/?logout").permitAll())
            .rememberMe(r->r.key(rememberMeKey).tokenValiditySeconds(7*24*60*60));
        return http.build();
    }
}
