package com.talentmatch.controller;

import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/projects")
public class ProgressReportController {
 private final ProgressService progress;
 private final ProjectService projects;
 private final UserService users;
 public ProgressReportController(ProgressService progress,ProjectService projects,UserService users){
   this.progress=progress;this.projects=projects;this.users=users;
 }
 @PostMapping("/{projectId}/milestones/{milestoneId}/progress")
 public String report(@PathVariable Long projectId,@PathVariable Long milestoneId,
     @RequestParam int percent,@RequestParam String note,
     @RequestParam(required=false) String evidenceUrl,Authentication auth,RedirectAttributes flash){
    try {progress.report(projects.get(projectId),milestoneId,users.byEmail(auth.getName()),percent,note,evidenceUrl);
       flash.addFlashAttribute("success","Đã lưu cập nhật tiến độ và thông báo cho Client.");}
    catch(RuntimeException ex){flash.addFlashAttribute("error",ex.getMessage());}
    return "redirect:/freelancer/deliveries?selected="+projectId;
 }
}
