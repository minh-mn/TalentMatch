package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/freelancer/community")
public class FreelancerCommunityController {
    private static final List<String> CATEGORIES = List.of(
            "Freelancer", "Doanh nghiệp", "Thanh toán", "Kỹ thuật",
            "Kinh nghiệm", "Hồ sơ", "Khác"
    );

    private final CommunityQuestionRepository questions;
    private final CommunityAnswerRepository answers;
    private final CommunityVoteRepository votes;
    private final CommunityBookmarkRepository bookmarks;
    private final UserService users;

    public FreelancerCommunityController(CommunityQuestionRepository questions,
                                         CommunityAnswerRepository answers,
                                         CommunityVoteRepository votes,
                                         CommunityBookmarkRepository bookmarks,
                                         UserService users) {
        this.questions = questions;
        this.answers = answers;
        this.votes = votes;
        this.bookmarks = bookmarks;
        this.users = users;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String category,
                       @RequestParam(defaultValue = "all") String tab,
                       @RequestParam(defaultValue = "all") String status,
                       @RequestParam(defaultValue = "all") String period,
                       @RequestParam(defaultValue = "newest") String sort,
                       @RequestParam(defaultValue = "false") boolean featured,
                       @RequestParam(defaultValue = "1") int page,
                       Authentication auth, Model model) {
        User currentUser = users.byEmail(auth.getName());
        Long userId = currentUser.getId();
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String selectedCategory = category == null ? "" : category.trim();
        String selectedTab = Set.of("all", "waiting", "answered", "mine", "saved").contains(tab) ? tab : "all";
        String selectedStatus = Set.of("all", "waiting", "answered", "discussion").contains(status) ? status : "all";
        String selectedPeriod = Set.of("all", "7", "30").contains(period) ? period : "all";
        String selectedSort = Set.of("newest", "oldest", "popular", "answered").contains(sort) ? sort : "newest";

        List<CommunityQuestion> all = questions.findAllByOrderByCreatedAtDesc();
        Map<Long, Long> answerCounts = new HashMap<>();
        Map<Long, Long> voteCounts = new HashMap<>();
        for (CommunityQuestion item : all) {
            answerCounts.put(item.id, answers.countByQuestionId(item.id));
            voteCounts.put(item.id, votes.countByQuestionId(item.id));
        }
        Set<Long> savedIds = bookmarks.findByUser_Id(userId).stream()
                .map(b -> b.question.getId()).collect(Collectors.toSet());

        long solvedCount = all.stream().filter(item -> item.solved).count();
        long waitingCount = all.stream().filter(item -> !item.solved && answerCounts.get(item.id) == 0).count();
        long discussionCount = all.stream().filter(item -> !item.solved && answerCounts.get(item.id) > 0).count();
        long mineCount = all.stream().filter(item -> item.author.getId().equals(userId)).count();
        LocalDateTime cutoff = "7".equals(selectedPeriod) ? LocalDateTime.now().minusDays(7) :
                               "30".equals(selectedPeriod) ? LocalDateTime.now().minusDays(30) : null;

        List<CommunityQuestion> filtered = all.stream().filter(item -> {
            if (!selectedCategory.isBlank() && !selectedCategory.equalsIgnoreCase(item.category)) return false;
            if (!keyword.isBlank() && !(item.title.toLowerCase(Locale.ROOT).contains(keyword)
                    || item.body.toLowerCase(Locale.ROOT).contains(keyword)
                    || item.category.toLowerCase(Locale.ROOT).contains(keyword))) return false;
            if (cutoff != null && (item.createdAt == null || item.createdAt.isBefore(cutoff))) return false;
            if (featured && voteCounts.get(item.id) < 3) return false;
            if (selectedTab.equals("waiting") && (item.solved || answerCounts.get(item.id) > 0)) return false;
            if (selectedTab.equals("answered") && !item.solved) return false;
            if (selectedTab.equals("mine") && !item.author.getId().equals(userId)) return false;
            if (selectedTab.equals("saved") && !savedIds.contains(item.id)) return false;
            if (selectedStatus.equals("waiting") && (item.solved || answerCounts.get(item.id) > 0)) return false;
            if (selectedStatus.equals("answered") && !item.solved) return false;
            if (selectedStatus.equals("discussion") && (item.solved || answerCounts.get(item.id) == 0)) return false;
            return true;
        }).toList();

        Comparator<CommunityQuestion> byDate = Comparator.comparing(
                item -> item.createdAt == null ? LocalDateTime.MIN : item.createdAt);
        Comparator<CommunityQuestion> comparator = switch (selectedSort) {
            case "oldest" -> byDate;
            case "popular" -> Comparator.<CommunityQuestion>comparingLong(item -> voteCounts.get(item.id))
                    .reversed().thenComparing(byDate.reversed());
            case "answered" -> Comparator.<CommunityQuestion>comparingLong(item -> answerCounts.get(item.id))
                    .reversed().thenComparing(byDate.reversed());
            default -> byDate.reversed();
        };
        filtered = filtered.stream().sorted(comparator).toList();

        int pages = Math.max(1, (filtered.size() + 4) / 5);
        int currentPage = Math.max(1, Math.min(pages, page));
        int from = Math.min((currentPage - 1) * 5, filtered.size());
        List<CommunityQuestion> slice = filtered.subList(from, Math.min(from + 5, filtered.size()));

        model.addAttribute("questions", slice);
        model.addAttribute("votesCount", voteCounts);
        model.addAttribute("answersCount", answerCounts);
        model.addAttribute("savedIds", savedIds);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("statsTotal", all.size());
        model.addAttribute("statsSolved", solvedCount);
        model.addAttribute("statsWaiting", waitingCount);
        model.addAttribute("statsDiscussions", discussionCount);
        model.addAttribute("statsMine", mineCount);
        model.addAttribute("statsSaved", savedIds.size());
        model.addAttribute("filteredCount", filtered.size());
        model.addAttribute("page", currentPage);
        model.addAttribute("pages", pages);
        model.addAttribute("q", q);
        model.addAttribute("category", selectedCategory);
        model.addAttribute("tab", selectedTab);
        model.addAttribute("status", selectedStatus);
        model.addAttribute("period", selectedPeriod);
        model.addAttribute("sort", selectedSort);
        model.addAttribute("featured", featured);
        return "freelancer/community";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        CommunityQuestion question = questions.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("question", question);
        model.addAttribute("answers", answers.findByQuestionIdOrderByCreatedAtAsc(id));
        model.addAttribute("voteCount", votes.countByQuestionId(id));
        return "freelancer/community-detail";
    }

    @PostMapping
    public String post(@RequestParam String title, @RequestParam String body,
                       @RequestParam String category, Authentication auth, RedirectAttributes ra) {
        title = title.trim();
        body = body.trim();
        if (title.length() < 8 || title.length() > 220 || body.length() < 10 || body.length() > 10000
                || !CATEGORIES.contains(category)) {
            ra.addFlashAttribute("error", "Thông tin câu hỏi chưa hợp lệ.");
            return "redirect:/freelancer/community";
        }
        CommunityQuestion question = new CommunityQuestion();
        question.author = users.byEmail(auth.getName());
        question.title = title;
        question.body = body;
        question.category = category;
        questions.save(question);
        return "redirect:/freelancer/community/" + question.id;
    }

    @PostMapping("/{id}/answers")
    public String answer(@PathVariable Long id, @RequestParam String body,
                         Authentication auth, RedirectAttributes ra) {
        body = body.trim();
        if (body.length() < 2 || body.length() > 10000) {
            ra.addFlashAttribute("error", "Nội dung câu trả lời không hợp lệ.");
            return "redirect:/freelancer/community/" + id;
        }
        CommunityAnswer answer = new CommunityAnswer();
        answer.question = questions.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        answer.author = users.byEmail(auth.getName());
        answer.body = body;
        answers.save(answer);
        return "redirect:/freelancer/community/" + id;
    }

    @PostMapping("/{id}/vote")
    @Transactional
    public String vote(@PathVariable Long id, Authentication auth,
                       @RequestParam(defaultValue = "detail") String back,
                       @RequestParam(defaultValue = "all") String tab,
                       @RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "1") int page,
                       RedirectAttributes ra) {
        User user = users.byEmail(auth.getName());
        CommunityQuestion question = questions.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Optional<CommunityVote> previous = votes.findByQuestionIdAndUserId(id, user.getId());
        if (previous.isPresent()) {
            votes.delete(previous.get());
        } else {
            CommunityVote vote = new CommunityVote();
            vote.question = question;
            vote.user = user;
            votes.save(vote);
        }
        if ("list".equals(back)) {
            ra.addAttribute("tab", tab);
            ra.addAttribute("q", q);
            ra.addAttribute("page", page);
            return "redirect:/freelancer/community";
        }
        return "redirect:/freelancer/community/" + id;
    }

    @PostMapping("/{id}/bookmark")
    @Transactional
    public String toggleBookmark(@PathVariable Long id, Authentication auth,
                                 @RequestParam(defaultValue = "all") String tab,
                                 @RequestParam(defaultValue = "") String q,
                                 @RequestParam(defaultValue = "1") int page,
                                 RedirectAttributes ra) {
        User user = users.byEmail(auth.getName());
        CommunityQuestion question = questions.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Optional<CommunityBookmark> existing = bookmarks.findByQuestion_IdAndUser_Id(id, user.getId());
        if (existing.isPresent()) {
            bookmarks.delete(existing.get());
        } else {
            CommunityBookmark bookmark = new CommunityBookmark();
            bookmark.user = user;
            bookmark.question = question;
            bookmarks.save(bookmark);
        }
        ra.addAttribute("tab", tab);
        ra.addAttribute("q", q);
        ra.addAttribute("page", page);
        return "redirect:/freelancer/community";
    }

    @PostMapping("/{id}/solve")
    public String solve(@PathVariable Long id, Authentication auth) {
        CommunityQuestion question = questions.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!question.author.getId().equals(users.byEmail(auth.getName()).getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        question.solved = true;
        questions.save(question);
        return "redirect:/freelancer/community/" + id;
    }
}
