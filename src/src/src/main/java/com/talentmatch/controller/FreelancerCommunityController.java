package com.talentmatch.controller;
import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.*;
@Controller @RequestMapping("/freelancer/community")
public class FreelancerCommunityController {
 private final CommunityQuestionRepository questions; private final CommunityAnswerRepository answers; private final CommunityVoteRepository votes; private final UserService users;
 private static final List<String> CATEGORIES=List.of("Freelancer","Doanh nghiệp","Thanh toán","Kỹ thuật","Kinh nghiệm","Hồ sơ","Khác");
 public FreelancerCommunityController(CommunityQuestionRepository q, CommunityAnswerRepository a, CommunityVoteRepository v,UserService u){questions=q;answers=a;votes=v;users=u;}
 @GetMapping public String list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="1") int page,Model m){
  String keyword=q.trim().toLowerCase(Locale.ROOT); String cat=category.trim();
  List<CommunityQuestion> matching=questions.findAllByOrderByCreatedAtDesc().stream().filter(x->cat.isBlank()||x.category.equalsIgnoreCase(cat)).filter(x->keyword.isBlank()||x.title.toLowerCase(Locale.ROOT).contains(keyword)||x.body.toLowerCase(Locale.ROOT).contains(keyword)).toList();
  int pages=Math.max(1,(matching.size()+4)/5);int current=Math.max(1,Math.min(pages,page));
  List<CommunityQuestion> slice=matching.subList(Math.min((current-1)*5,matching.size()),Math.min(current*5,matching.size()));
  Map<Long,Long> counts=new HashMap<>(),score=new HashMap<>();for(CommunityQuestion x:slice){counts.put(x.id,answers.countByQuestionId(x.id));score.put(x.id,votes.countByQuestionId(x.id));}
  m.addAttribute("questions",slice);m.addAttribute("answersCount",counts);m.addAttribute("votesCount",score);m.addAttribute("categories",CATEGORIES);m.addAttribute("q",q);m.addAttribute("category",category);m.addAttribute("page",current);m.addAttribute("pages",pages);m.addAttribute("total",questions.count());m.addAttribute("totalAnswers",answers.count());return "freelancer/community";
 }
 @GetMapping("/{id}") public String detail(@PathVariable Long id,Model m){CommunityQuestion q=questions.findById(id).orElseThrow();m.addAttribute("question",q);m.addAttribute("answers",answers.findByQuestionIdOrderByCreatedAtAsc(id));m.addAttribute("voteCount",votes.countByQuestionId(id));return "freelancer/community-detail";}
 @PostMapping public String post(@RequestParam String title,@RequestParam String body,@RequestParam String category, Authentication auth,RedirectAttributes ra){
  if(title.isBlank()||title.length()>220||body.isBlank()||body.length()>10000||!CATEGORIES.contains(category)){ra.addFlashAttribute("error","Thông tin câu hỏi chưa hợp lệ.");return "redirect:/freelancer/community";}
  CommunityQuestion q=new CommunityQuestion();q.author=users.byEmail(auth.getName());q.title=title.trim();q.body=body.trim();q.category=category;questions.save(q);return "redirect:/freelancer/community/"+q.id;
 }
 @PostMapping("/{id}/answers") public String answer(@PathVariable Long id,@RequestParam String body,Authentication auth,RedirectAttributes ra){if(body.isBlank()||body.length()>10000){ra.addFlashAttribute("error","Nội dung câu trả lời không hợp lệ.");return "redirect:/freelancer/community/"+id;}CommunityAnswer a=new CommunityAnswer();a.question=questions.findById(id).orElseThrow();a.author=users.byEmail(auth.getName());a.body=body.trim();answers.save(a);return "redirect:/freelancer/community/"+id;}
 @PostMapping("/{id}/vote") @Transactional public String vote(@PathVariable Long id,Authentication auth){User u=users.byEmail(auth.getName());CommunityQuestion q=questions.findById(id).orElseThrow();Optional<CommunityVote> prev=votes.findByQuestionIdAndUserId(id,u.getId());if(prev.isPresent())votes.delete(prev.get());else{CommunityVote v=new CommunityVote();v.question=q;v.user=u;votes.save(v);}return "redirect:/freelancer/community/"+id;}
 @PostMapping("/{id}/solve") public String solve(@PathVariable Long id,Authentication auth){CommunityQuestion q=questions.findById(id).orElseThrow();if(!q.author.getId().equals(users.byEmail(auth.getName()).getId()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);q.solved=true;questions.save(q);return "redirect:/freelancer/community/"+id;}
}
