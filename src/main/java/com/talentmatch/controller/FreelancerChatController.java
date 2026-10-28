package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.ChatAttachmentService;
import com.talentmatch.service.UserService;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping({"/freelancer/messages", "/customer/messages"})
public class FreelancerChatController {
    private final ProjectRepository projects;
    private final ConversationMessageRepository messages;
    private final UserService users;
    private final ChatAttachmentService attachments;

    public FreelancerChatController(ProjectRepository projects, ConversationMessageRepository messages,
                                   UserService users, ChatAttachmentService attachments) {
        this.projects = projects;
        this.messages = messages;
        this.users = users;
        this.attachments = attachments;
    }

    private boolean member(Project p, User u) {
        return p.getFreelancer().getId().equals(u.getId()) || p.getCustomer().getId().equals(u.getId());
    }

    private String base(User u) {
        return u.getRole() == Role.CUSTOMER ? "/customer/messages" : "/freelancer/messages";
    }

    private String partnerName(Project p, User u) {
        if (u.getRole() == Role.CUSTOMER) return Objects.toString(p.getFreelancer().getFullName(), "Freelancer");
        String company = p.getCustomer().getCompanyName();
        return company == null || company.isBlank() ? Objects.toString(p.getCustomer().getFullName(), "Khách hàng") : company;
    }

    @GetMapping
    @Transactional
    public String index(Authentication auth, @RequestParam(required = false) Long projectId,
                        @RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "all") String tab, Model model) {
        User u = users.byEmail(auth.getName());
        String keyword = q.strip().toLowerCase(Locale.ROOT);
        List<Project> all = u.getRole() == Role.CUSTOMER
                ? projects.findByCustomerOrderByCreatedAtDesc(u)
                : projects.findByFreelancerOrderByCreatedAtDesc(u);

        Map<Long, ConversationMessage> latest = new HashMap<>();
        Map<Long, Long> unreadCounts = new HashMap<>();
        for (Project p : all) {
            messages.findFirstByProjectIdOrderByCreatedAtDesc(p.getId())
                    .ifPresent(msg -> latest.put(p.getId(), msg));
            unreadCounts.put(p.getId(), messages.countByProjectIdAndSenderIdNotAndReadAtIsNull(p.getId(), u.getId()));
        }
        long unreadThreads = unreadCounts.values().stream().filter(n -> n > 0).count();

        List<Project> filtered = new ArrayList<>();
        for (Project p : all) {
            String name = partnerName(p, u).toLowerCase(Locale.ROOT);
            String title = p.getApplication().getJob().getTitle().toLowerCase(Locale.ROOT);
            if (!keyword.isBlank() && !name.contains(keyword) && !title.contains(keyword)) continue;
            if ("unread".equals(tab) && unreadCounts.getOrDefault(p.getId(), 0L) == 0) continue;
            if ("projects".equals(tab) && p.getStatus() != ProjectStatus.IN_PROGRESS
                    && p.getStatus() != ProjectStatus.WAITING_ACCEPTANCE) continue;
            filtered.add(p);
        }
        filtered.sort((a, b) -> {
            LocalDateTime dateA = latest.containsKey(a.getId()) ? latest.get(a.getId()).getCreatedAt() : a.getCreatedAt();
            LocalDateTime dateB = latest.containsKey(b.getId()) ? latest.get(b.getId()).getCreatedAt() : b.getCreatedAt();
            return Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()).reversed().compare(dateA, dateB);
        });

        Project selected = null;
        if (projectId != null) {
            selected = projects.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!member(selected, u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } else if (!filtered.isEmpty()) {
            selected = filtered.get(0);
        }

        List<ConversationMessage> chat = selected == null ? List.of() : messages.findByProjectIdOrderByCreatedAtAsc(selected.getId());
        List<ConversationMessage> shared = new ArrayList<>();
        for (ConversationMessage msg : chat) {
            if (!msg.sender.getId().equals(u.getId()) && msg.readAt == null) msg.readAt = LocalDateTime.now();
            if (msg.hasAttachment()) shared.add(msg);
        }
        Collections.reverse(shared);
        if (selected != null) unreadCounts.put(selected.getId(), 0L);
        unreadThreads = unreadCounts.values().stream().filter(n -> n > 0).count();

        model.addAttribute("chatBase", base(u));
        model.addAttribute("conversations", filtered);
        model.addAttribute("conversationCount", all.size());
        model.addAttribute("unreadThreadCount", unreadThreads);
        model.addAttribute("unreadCounts", unreadCounts);
        model.addAttribute("latestMessages", latest);
        model.addAttribute("selected", selected);
        model.addAttribute("messages", chat);
        model.addAttribute("sharedFiles", shared);
        model.addAttribute("q", q);
        model.addAttribute("tab", tab);
        model.addAttribute("userId", u.getId());
        model.addAttribute("isCustomer", u.getRole() == Role.CUSTOMER);
        return "freelancer/messages";
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id, @RequestParam(required = false) String content,
                       @RequestParam(required = false, name = "file") MultipartFile file,
                       Authentication auth, RedirectAttributes ra) {
        User u = users.byEmail(auth.getName());
        Project project = projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!member(project, u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        String text = content == null ? "" : content.strip();
        boolean withFile = file != null && !file.isEmpty();
        String redirect = "redirect:" + base(u) + "?projectId=" + id;
        if ((text.isEmpty() && !withFile) || text.length() > 4000) {
            ra.addFlashAttribute("error", "Nhập tin nhắn hoặc chọn tệp, tối đa 4000 ký tự.");
            return redirect;
        }
        try {
            ConversationMessage message = new ConversationMessage();
            message.project = project;
            message.sender = u;
            message.content = text;
            if (withFile) {
                ChatAttachmentService.SavedFile saved = attachments.save(file);
                message.setAttachmentName(saved.name());
                message.setAttachmentKey(saved.key());
                message.setAttachmentSize(saved.size());
            }
            messages.save(message);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return redirect;
    }

    @GetMapping("/files/{messageId}")
    @ResponseBody
    public ResponseEntity<UrlResource> download(@PathVariable Long messageId, Authentication auth) throws IOException {
        User user = users.byEmail(auth.getName());
        ConversationMessage message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!member(message.project, user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!message.hasAttachment()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path file = attachments.get(message.getAttachmentKey());
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(file))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(message.getAttachmentName(), StandardCharsets.UTF_8).build().toString())
                .body(new UrlResource(file.toUri()));
    }
}
