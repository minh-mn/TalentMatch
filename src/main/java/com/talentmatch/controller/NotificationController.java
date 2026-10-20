package com.talentmatch.controller;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller @RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;private final UserService users;
    public NotificationController(NotificationService notifications,UserService users){this.notifications=notifications;this.users=users;}
    @GetMapping public String list(Authentication auth,Model model){model.addAttribute("notifications",notifications.inbox(users.byEmail(auth.getName())));return "notifications/list";}
    @PostMapping("/{id}/read") public String read(@PathVariable Long id,Authentication auth){return "redirect:"+notifications.read(id,users.byEmail(auth.getName()));}
}
