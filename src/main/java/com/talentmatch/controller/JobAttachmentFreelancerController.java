package com.talentmatch.controller;

import com.talentmatch.model.Job;
import com.talentmatch.model.JobStatus;
import com.talentmatch.model.Role;
import com.talentmatch.model.User;
import com.talentmatch.service.JobDocumentService;
import com.talentmatch.service.JobService;
import com.talentmatch.service.UserService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tải tệp của công việc: chặn truy cập nếu chưa đủ quyền. */
@Controller
public class JobAttachmentFreelancerController {
    private final JobService jobs;
    private final UserService users;
    private final JobDocumentService documents;

    public JobAttachmentFreelancerController(JobService jobs, UserService users, JobDocumentService documents) {
        this.jobs=jobs; this.users=users; this.documents=documents;
    }

    @GetMapping("/jobs/{id}/document")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        User user = users.byEmail(auth.getName());
        Job job = jobs.get(id);
        boolean owner = job.getCustomer().getId().equals(user.getId());
        boolean visible = user.getRole() == Role.ADMIN || owner ||
                (user.getRole() == Role.FREELANCER && (job.getStatus() == JobStatus.OPEN
                        || jobs.findApplication(job, user).isPresent()));
        if (!visible) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (job.getAttachmentKey() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path path;
        try { path = documents.filePath(job.getAttachmentKey()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String name = job.getAttachmentOriginalName() == null ? "tai-lieu" : job.getAttachmentOriginalName();
        String safeName = URLEncoder.encode(name.replaceAll("[\\r\\n]", ""), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + safeName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
