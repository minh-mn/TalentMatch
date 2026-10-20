package com.talentmatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}

    @Bean public AuthenticationSuccessHandler successHandler(){
        return (req,res,auth)->{
            boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));

            // Admin vào khu quản trị; Freelancer/Customer sau khi đăng nhập quay về Marketplace.
            // Workspace được truy cập riêng từ menu tài khoản / Bảng điều khiển.
            res.sendRedirect(admin ? "/admin/dashboard" : "/");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,AuthenticationSuccessHandler successHandler)throws Exception{
        http.authorizeHttpRequests(auth->auth
                .requestMatchers("/","/login","/register","/verify","/forgot-password","/reset-password","/css/**","/js/**","/images/**","/error").permitAll()
                .requestMatchers(HttpMethod.GET,"/jobs/**","/freelancers/**","/hire","/projects").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/customer/**").hasRole("CUSTOMER")
                .requestMatchers("/freelancer/**").hasRole("FREELANCER")
                .requestMatchers("/projects/**","/payment/**").authenticated()
                .anyRequest().authenticated())
            .formLogin(form->form.loginPage("/login").loginProcessingUrl("/login").usernameParameter("email").passwordParameter("password")
                    .successHandler(successHandler).failureUrl("/login?error").permitAll())
            .logout(logout->logout.logoutUrl("/logout").logoutSuccessUrl("/?logout").permitAll())
            .rememberMe(r->r.key("talentmatch-pro-remember-key").tokenValiditySeconds(7*24*60*60));
        return http.build();
    }
}
