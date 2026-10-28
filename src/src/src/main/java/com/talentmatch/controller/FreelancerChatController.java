package com.talentmatch.controller;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
@Controller @RequestMapping({"/freelancer/messages","/customer/messages"})
public class FreelancerChatController {
 private final ProjectRepository projects; private final ConversationMessageRepository messages; private final UserService users;
 public FreelancerChatController(ProjectRepository p,ConversationMessageRepository m,UserService u){projects=p;messages=m;users=u;}
 private boolean member(Project p,User u){return p.getFreelancer().getId().equals(u.getId())||p.getCustomer().getId().equals(u.getId());}
 @GetMapping @Transactional public String index(Authentication auth,@RequestParam(required=false) Long projectId,@RequestParam(defaultValue="") String q,Model m){
  User u=users.byEmail(auth.getName());List<Project> all=u.getRole()==Role.CUSTOMER?projects.findByCustomerOrderByCreatedAtDesc(u):projects.findByFreelancerOrderByCreatedAtDesc(u);
  List<Project> filtered=all.stream().filter(p->q.isBlank()||p.getApplication().getJob().getTitle().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))||(u.getRole()==Role.CUSTOMER?p.getFreelancer().getFullName():p.getCustomer().getFullName()).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).toList();
  Project selected=null;if(projectId!=null){selected=projects.findById(projectId).orElseThrow();if(!member(selected,u))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}else if(!filtered.isEmpty())selected=filtered.get(0);
  List<ConversationMessage> chat=selected==null?List.of():messages.findByProjectIdOrderByCreatedAtAsc(selected.getId());
  for(ConversationMessage msg:chat){if(!msg.sender.getId().equals(u.getId())&&msg.readAt==null)msg.readAt=LocalDateTime.now();}
  m.addAttribute("chatBase",u.getRole()==Role.CUSTOMER?"/customer/messages":"/freelancer/messages");m.addAttribute("conversations",filtered);m.addAttribute("selected",selected);m.addAttribute("messages",chat);m.addAttribute("q",q);m.addAttribute("userId",u.getId());m.addAttribute("isCustomer",u.getRole()==Role.CUSTOMER);m.addAttribute("conversationCount",all.size());return "freelancer/messages";
 }
 @PostMapping("/{id}/send") public String send(@PathVariable Long id,@RequestParam String content,Authentication auth,RedirectAttributes ra){User u=users.byEmail(auth.getName());Project p=projects.findById(id).orElseThrow();if(!member(p,u))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(content==null||content.isBlank()||content.length()>4000){ra.addFlashAttribute("error","Tin nhắn phải có nội dung và không quá 4000 ký tự.");return "redirect:"+(u.getRole()==Role.CUSTOMER?"/customer/messages":"/freelancer/messages")+"?projectId="+id;}ConversationMessage msg=new ConversationMessage();msg.project=p;msg.sender=u;msg.content=content.trim();messages.save(msg);return "redirect:"+(u.getRole()==Role.CUSTOMER?"/customer/messages":"/freelancer/messages")+"?projectId="+id;}
}
