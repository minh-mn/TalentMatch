package com.talentmatch.controller;

import com.talentmatch.model.User;
import com.talentmatch.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/freelancer/settings")
public class FreelancerSettingsController {
    private final UserService users;
    public FreelancerSettingsController(UserService users) { this.users = users; }

    @GetMapping
    public String view(Authentication authentication, Model model) {
        User user = users.byEmail(authentication.getName());
        model.addAttribute("currentUser", user);
        model.addAttribute("user", user);
        model.addAttribute("active", "settings");
        return "freelancer/settings";
    }

    @PostMapping("/preferences")
    public String savePreferences(Authentication authentication,
                                  @RequestParam(defaultValue = "false") boolean available,
                                  @RequestParam(defaultValue = "") String workPreference,
                                  RedirectAttributes flash) {
        try {
            users.updateFreelancerPreferences(authentication.getName(), available, workPreference);
            flash.addFlashAttribute("success", "Đã cập nhật trạng thái nhận việc và hình thức làm việc.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/freelancer/settings";
    }
}
