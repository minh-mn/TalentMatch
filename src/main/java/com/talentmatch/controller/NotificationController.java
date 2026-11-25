package com.talentmatch.controller;
import com.talentmatch.model.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@Controller @RequestMapping("/notifications")
public class NotificationController {
  private final NotificationService notifications;private final UserService users;
  public NotificationController(NotificationService notifications,UserService users){this.notifications=notifications;this.users=users;}
  @GetMapping public String list(Authentication auth,Model model,@RequestParam(defaultValue="all") String filter){
    User user=users.byEmail(auth.getName());var all=notifications.inbox(user);
    model.addAttribute("notifications",all.stream().filter(n->switch(filter){case "unread"->!n.isRead();case "read"->n.isRead();default->true;}).toList());
    model.addAttribute("filter",filter);model.addAttribute("unreadCount",all.stream().filter(n->!n.isRead()).count());
    return user.getRole()==Role.ADMIN?"notifications/admin-list":"notifications/list";
  }
  @PostMapping("/{id}/read") public String read(@PathVariable Long id,Authentication auth){return "redirect:"+notifications.read(id,users.byEmail(auth.getName()));}
  @PostMapping("/read-all") public String readAll(Authentication auth){notifications.markAllRead(users.byEmail(auth.getName()));return "redirect:/notifications";}
}
