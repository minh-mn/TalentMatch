package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class JobService {
    private final JobRepository jobs;
    private final SavedJobRepository savedJobs;
    private final JobApplicationRepository applications;
    private final UserRepository users;

    public JobService(JobRepository jobs, SavedJobRepository savedJobs,
                      JobApplicationRepository applications, UserRepository users) {
        this.jobs = jobs;
        this.savedJobs = savedJobs;
        this.applications = applications;
        this.users = users;
    }

    public Job get(Long id) {
        return jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));
    }

    public Job save(Job job) { return jobs.save(job); }

    public List<Job> search(String q, String category, String skill, BigDecimal minBudget, BigDecimal maxBudget,
                            LocalDate deadline, String workType) {
        return searchAdvanced(q, category, skill, minBudget, maxBudget, deadline,
                workType == null || workType.isBlank() ? List.of() : List.of(workType), List.of(), "latest");
    }

    public List<Job> searchAdvanced(String q, String category, String skill, BigDecimal minBudget, BigDecimal maxBudget,
                                    LocalDate deadline, List<String> workTypes, List<String> levels, String sort) {
        String k = norm(q), c = norm(category), s = norm(skill);
        Set<String> workTypeSet = upperSet(workTypes);
        Set<String> levelSet = upperSet(levels);

        Comparator<Job> comparator = Comparator.comparing(Job::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        String sortValue = norm(sort);
        if ("budget_desc".equals(sortValue)) {
            comparator = Comparator.comparing(Job::getBudget,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        } else if ("budget_asc".equals(sortValue)) {
            comparator = Comparator.comparing(Job::getBudget,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("deadline_asc".equals(sortValue)) {
            comparator = Comparator.comparing(Job::getDeadline,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("competition_asc".equals(sortValue)) {
            comparator = Comparator.<Job>comparingLong(job -> applications.countByJob(job))
                    .thenComparing(
                            Job::getCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    );
        }

        return jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN).stream()
                // Không hiển thị dự án đã quá hạn; dự án không có deadline vẫn hợp lệ.
                .filter(j -> j.getDeadline() == null || !j.getDeadline().isBefore(LocalDate.now()))
                .filter(j -> {
                    boolean ok = true;
                    if (!k.isBlank()) ok &= norm(j.getTitle() + " " + j.getDescription() + " " + j.getRequiredSkills()).contains(k);
                    if (!c.isBlank()) ok &= norm(j.getCategory()).equals(c);
                    if (!s.isBlank()) ok &= norm(j.getRequiredSkills()).contains(s);
                    if (minBudget != null) ok &= j.getBudget() != null && j.getBudget().compareTo(minBudget) >= 0;
                    if (maxBudget != null) ok &= j.getBudget() != null && j.getBudget().compareTo(maxBudget) <= 0;
                    // Nếu lọc theo thời hạn, dự án không đặt deadline sẽ không phù hợp.
                    if (deadline != null) ok &= j.getDeadline() != null && !j.getDeadline().isAfter(deadline);
                    if (!workTypeSet.isEmpty()) ok &= workTypeSet.contains(norm(j.getWorkType()).toUpperCase(Locale.ROOT));
                    if (!levelSet.isEmpty()) ok &= matchesLevel(j.getExperienceLevel(), levelSet);
                    return ok;
                })
                .sorted(comparator)
                .toList();
    }

    @Transactional
    public boolean toggleSaved(Job job, User freelancer) {
        requireFreelancer(freelancer);
        Optional<SavedJob> existing = savedJobs.findByFreelancerAndJob(freelancer, job);
        if (existing.isPresent()) {
            savedJobs.delete(existing.get());
            return false;
        }
        SavedJob saved = new SavedJob();
        saved.setFreelancer(freelancer);
        saved.setJob(job);
        savedJobs.save(saved);
        return true;
    }

    public boolean isSaved(Job job, User freelancer) { return savedJobs.existsByFreelancerAndJob(freelancer, job); }
    public List<SavedJob> saved(User freelancer) { requireFreelancer(freelancer); return savedJobs.findByFreelancerOrderByCreatedAtDesc(freelancer); }

    @Transactional
    public JobApplication apply(Job job, User freelancer, String proposal, BigDecimal price, Integer estimatedDays, String portfolioLink) {
        requireFreelancer(freelancer);
        if (job.getStatus() != JobStatus.OPEN) throw new IllegalArgumentException("Công việc này không còn nhận ứng tuyển.");
        if (applications.findByJobAndFreelancer(job, freelancer).isPresent()) throw new IllegalArgumentException("Bạn đã ứng tuyển công việc này.");
        if (freelancer.getProfessionalTitle() == null || freelancer.getProfessionalTitle().isBlank()
                || freelancer.getSkills() == null || freelancer.getSkills().isBlank()) {
            throw new IllegalArgumentException("Hãy hoàn thiện tiêu đề nghề nghiệp và kỹ năng trong hồ sơ trước khi ứng tuyển.");
        }
        proposal = proposal == null ? "" : proposal.trim();
        if (proposal.length() < 30) throw new IllegalArgumentException("Proposal nên có ít nhất 30 ký tự để khách hàng hiểu giải pháp của bạn.");
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Báo giá phải lớn hơn 0.");
        if (estimatedDays == null || estimatedDays <= 0 || estimatedDays > 3650) throw new IllegalArgumentException("Thời gian dự kiến không hợp lệ.");

        JobApplication app = new JobApplication();
        app.setJob(job);
        app.setFreelancer(freelancer);
        app.setProposal(proposal);
        app.setProposedPrice(price);
        app.setEstimatedDays(estimatedDays);
        app.setPortfolioLink(portfolioLink == null ? null : portfolioLink.trim());
        app.setStatus(ApplicationStatus.SUBMITTED);
        return applications.save(app);
    }

    @Transactional
    public void withdraw(Long applicationId, User freelancer) {
        JobApplication a = applications.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn ứng tuyển."));
        if (!a.getFreelancer().getId().equals(freelancer.getId())) throw new IllegalArgumentException("Bạn không sở hữu đơn ứng tuyển này.");
        if (a.getStatus() == ApplicationStatus.ACCEPTED) throw new IllegalArgumentException("Ứng tuyển đã được chọn, không thể rút.");
        if (a.getStatus() == ApplicationStatus.REJECTED || a.getStatus() == ApplicationStatus.WITHDRAWN)
            throw new IllegalArgumentException("Ứng tuyển không còn ở trạng thái có thể rút.");
        a.setStatus(ApplicationStatus.WITHDRAWN);
        applications.save(a);
    }

    public List<JobApplication> applications(User freelancer) { requireFreelancer(freelancer); return applications.findByFreelancerOrderByCreatedAtDesc(freelancer); }
    public List<JobApplication> applicants(Job job) { return applications.findByJobOrderByCreatedAtDesc(job); }
    public long applicationCount(Job job) { return applications.countByJob(job); }
    public Optional<JobApplication> findApplication(Job job, User freelancer) { return applications.findByJobAndFreelancer(job, freelancer); }

    public List<User> freelancers(String q) {
        String k = norm(q);
        return users.findByRoleAndLockedFalseOrderByCreatedAtDesc(Role.FREELANCER).stream()
                .filter(User::getEnabled)
                .filter(u -> k.isBlank() || norm(u.getFullName() + " " + u.getProfessionalTitle() + " " + u.getSkills() + " " + u.getField()).contains(k))
                .toList();
    }


    private boolean matchesLevel(String experienceLevel, Set<String> selected) {
        String level = norm(experienceLevel).toUpperCase(Locale.ROOT);
        if (level.isBlank()) return false;
        if (selected.contains(level)) return true;
        if (selected.contains("MIDDLE") && "INTERMEDIATE".equals(level)) return true;
        if (selected.contains("SENIOR") && "EXPERT".equals(level)) return true;
        return false;
    }

    private Set<String> upperSet(List<String> values) {
        if (values == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            String v = norm(value).toUpperCase(Locale.ROOT);
            if (!v.isBlank()) out.add(v);
        }
        return out;
    }

    private void requireFreelancer(User u) {
        if (u == null || u.getRole() != Role.FREELANCER)
            throw new IllegalArgumentException("Chức năng chỉ dành cho Freelancer.");
    }

    private String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
}
