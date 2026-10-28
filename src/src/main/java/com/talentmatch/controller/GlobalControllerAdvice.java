package com.talentmatch.controller;

import com.talentmatch.model.User;
import com.talentmatch.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {
    private final UserService users;
    public GlobalControllerAdvice(UserService users){this.users=users;}
    @ModelAttribute("currentUser")
    public User currentUser(Authentication auth){
        if(auth==null||!auth.isAuthenticated()||"anonymousUser".equals(auth.getName()))return null;
        try{return users.byEmail(auth.getName());}catch(Exception e){return null;}
    }
}
