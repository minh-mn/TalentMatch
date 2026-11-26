package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.LocalDate;

@Controller
@RequestMapping("/ai")
public class AiInsightsController {
    private final UserService users;
    private final UserRepository userRepo;
    private final JobRepository jobs;
    private final TalentReviewRepository reviews;
    private final CommunityAnswerRepository answers;
    private final AiMatchingService matching;
    private final VietnameseSentimentService sentiments;

    public AiInsightsController(UserService users, UserRepository userRepo, JobRepository jobs,
                                TalentReviewRepository reviews, CommunityAnswerRepository answers, AiMatchingService matching,
                                VietnameseSentimentService sentiments) {
        this.users=users; this.userRepo=userRepo; this.jobs=jobs; this.reviews=reviews;
        this.matching=matching; this.sentiments=sentiments; this.answers=answers;
    }
    private User current(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new org.springframework.security.access.AccessDeniedException("Cần đăng nhập");
        return users.byEmail(auth.getName());
    }
    private void shared(Model m, User user) {
        m.addAttribute("currentUser", user);
        m.addAttribute("aiRole", user.getRole().name());
        m.addAttribute("active", "ai");
    }
    @GetMapping("/matching")
    public String matching(@RequestParam(required=false) Long jobId, Authentication auth, Model model) {
        User user=current(auth); shared(model,user);
        if (user.getRole() == Role.ADMIN) return "redirect:/ai/sentiment";
        if (user.getRole() == Role.CUSTOMER) return "redirect:/customer/talent" + (jobId == null ? "" : "?jobId=" + jobId);
        List<Job> jobsList=jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN).stream()
                .filter(j -> j.getDeadline() == null || !j.getDeadline().isBefore(LocalDate.now())).toList();
        List<AiMatchingService.MatchResult> results=jobsList.stream().map(job->matching.rankJob(user,job))
                .sorted(Comparator.comparingInt(AiMatchingService.MatchResult::score).reversed()).limit(50).toList();
        model.addAttribute("results",results);
        return "ai/matching";
    }
    public record ReviewView(Long id, String source, String author, String target, String content, String sentiment,
                             String code, String explanation, Integer rating) {}
    @GetMapping("/sentiment")
    public String sentiment(@RequestParam(required=false) String text,
                            @RequestParam(defaultValue="ALL") String mood,
                            @RequestParam(defaultValue="0") int page, Authentication auth, Model model) {
        User user=current(auth); shared(model,user);
        if (text != null && !text.isBlank()) model.addAttribute("testResult",sentiments.classify(text.length()>1000?text.substring(0,1000):text));
        model.addAttribute("inputText",text==null?"":text);
        // Reviews are scoped by business ownership, not exposed globally to ordinary users.
        List<TalentReview> source = reviews.findAll().stream()
            .filter(r -> r.getAuthor()!=null && r.getTarget()!=null && r.getProject()!=null)
            .filter(r -> user.getRole()==Role.ADMIN
                || (user.getRole()==Role.FREELANCER && user.getId().equals(r.getTarget().getId()))
                || (user.getRole()==Role.CUSTOMER && r.getProject().getCustomer()!=null
                    && user.getId().equals(r.getProject().getCustomer().getId())))
            .sorted(Comparator.comparing(TalentReview::getId).reversed())
            .limit(100).toList();
        List<ReviewView> combined = new ArrayList<>();
        for (TalentReview r : source) {
            var result=sentiments.classify(r.getComment());
            combined.add(new ReviewView(r.getId(),"Đánh giá dự án",r.getAuthor().getFullName(),
                r.getTarget().getFullName(),r.getComment(),result.label(),result.code(),result.evidence(),r.getRating()));
        }
        // Community comments are only analyzed in aggregate by Admin; individual users
        // do not receive unrelated third-party comments in their personal feedback page.
        if (user.getRole()==Role.ADMIN) {
            answers.findAll().stream().sorted(Comparator.comparing(CommunityAnswer::getId).reversed()).limit(100)
                .forEach(a -> { var result=sentiments.classify(a.getBody());
                    combined.add(new ReviewView(a.getId(), "Bình luận cộng đồng",
                        a.getAuthor()==null?"Thành viên":a.getAuthor().getFullName(), "Cộng đồng",
                        a.getBody(), result.label(),result.code(),result.evidence(),null)); });
        }
        model.addAttribute("sentimentScope", user.getRole()==Role.ADMIN ? "Toàn nền tảng: đánh giá và bình luận cộng đồng"
            : user.getRole()==Role.FREELANCER ? "Chỉ đánh giá khách hàng gửi cho bạn"
            : "Chỉ đánh giá thuộc dự án của doanh nghiệp bạn");
        long unclear = combined.stream().filter(r -> r.code().equals("UNCLEAR")).count();
        long reviewed = combined.size() - unclear;
        long suspicious = combined.stream().filter(r -> r.rating()!=null &&
            ((r.rating() >= 4 && r.code().equals("NEGATIVE")) || (r.rating() <= 2 && r.code().equals("POSITIVE")))).count();
        String chosenMood = Set.of("ALL","POSITIVE","NEGATIVE","NEUTRAL","UNCLEAR").contains(mood) ? mood : "ALL";
        List<ReviewView> visible = combined.stream().filter(r -> switch (chosenMood) {
            case "UNCLEAR" -> r.code().equals("UNCLEAR");
            case "POSITIVE", "NEGATIVE", "NEUTRAL" -> chosenMood.equals(r.code());
            default -> true;
        }).toList();
        int pages = Math.max(1, (visible.size()+9)/10);
        int currentPage = Math.max(0, Math.min(page, pages-1));
        model.addAttribute("reviews", visible.stream().skip((long)currentPage*10).limit(10).toList());
        model.addAttribute("mood", chosenMood);
        model.addAttribute("page", currentPage);
        model.addAttribute("totalPages", pages);
        model.addAttribute("filteredCount", visible.size());
        model.addAttribute("sampleCount", combined.size());
        model.addAttribute("unclearCount", unclear);
        model.addAttribute("clearCount", reviewed);
        model.addAttribute("mismatchCount", suspicious);
        model.addAttribute("aiPageTitle", user.getRole()==Role.FREELANCER ? "Đánh giá của khách hàng" : user.getRole()==Role.CUSTOMER ? "Phân tích phản hồi dự án" : "Phân tích cảm xúc toàn nền tảng");
        model.addAttribute("aiPageSubtitle", user.getRole()==Role.FREELANCER ? "Xem khách hàng nhận xét điểm mạnh, vấn đề cần cải thiện và mức độ rõ ràng của phản hồi." : user.getRole()==Role.CUSTOMER ? "Theo dõi chất lượng cộng tác trong dự án, nhận diện nhận xét cần phản hồi." : "Theo dõi xu hướng phản hồi toàn hệ thống để ưu tiên kiểm tra chất lượng và hỗ trợ.");
        model.addAttribute("aiPracticalTitle", user.getRole()==Role.FREELANCER ? "Bạn nên cải thiện điều gì?" : user.getRole()==Role.CUSTOMER ? "Doanh nghiệp nên theo dõi điều gì?" : "Admin cần ưu tiên kiểm tra điều gì?");
        model.addAttribute("aiPracticalDesc", user.getRole()==Role.FREELANCER ? "Đọc đánh giá tiêu cực có nội dung cụ thể, đối chiếu tiến độ và trao đổi với Client. Nhận xét tích cực giúp xác định thế mạnh khi ứng tuyển." : user.getRole()==Role.CUSTOMER ? "Ưu tiên các phản hồi tiêu cực kèm dẫn chứng để xem lại quy trình giao việc, nghiệm thu và hỗ trợ Freelancer." : "Kiểm tra các bình luận tiêu cực hoặc điểm sao mâu thuẫn với nội dung; chỉ chuyển sang xử lý khi có bằng chứng và người phụ trách xác nhận.");
        model.addAttribute("positiveCount",combined.stream().filter(r->r.code().equals("POSITIVE")).count());
        model.addAttribute("negativeCount",combined.stream().filter(r->r.code().equals("NEGATIVE")).count());
        model.addAttribute("neutralCount",combined.stream().filter(r->r.code().equals("NEUTRAL")).count());
        return "ai/sentiment";
    }
}
