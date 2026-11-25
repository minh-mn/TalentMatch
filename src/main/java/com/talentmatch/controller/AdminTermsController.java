package com.talentmatch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Quy định tham khảo cho demo tranh chấp: chỉ đọc, không thay đổi trạng thái tài chính. */
@Controller
@RequestMapping("/admin")
public class AdminTermsController {
    @GetMapping("/terms")
    public String terms(Model model) {
        model.addAttribute("active", "terms");
        model.addAttribute("pageTitle", "Điều khoản & Quy trình giải quyết tranh chấp");
        return "admin/terms";
    }
}
