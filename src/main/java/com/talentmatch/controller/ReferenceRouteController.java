package com.talentmatch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReferenceRouteController {
    @GetMapping("/profiles/posted-projects")
    public String postedProjects() {
        return "redirect:/customer/jobs";
    }

    @GetMapping("/profiles/quoted-projects")
    public String quotedProjects() {
        return "redirect:/freelancer/applications";
    }
}
