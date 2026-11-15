package com.talentmatch;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.SettlementDemoService;
import com.talentmatch.service.WalletDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SmokeTestFixtures.class)
class FlowTest {
    private static final String CUSTOMER = "client@smoke.test";
    private static final String FREELANCER = "freelancer@smoke.test";
    private static final String ADMIN = "admin@smoke.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private JobRepository jobs;
    @Autowired private JobApplicationRepository applications;
    @Autowired private QuoteRevisionRepository quotes;
    @Autowired private ProjectRepository projects;
    @Autowired private TalentReportRepository reports;
    @Autowired private MilestoneRepository milestones;
    @Autowired private PaymentRepository payments;
    @Autowired private ProjectDeliveryRepository deliveries;
    @Autowired private WalletBankRepository banks;
    @Autowired private WalletWithdrawalRepository withdrawals;
    @Autowired private CommunityQuestionRepository questions;
    @Autowired private CommunityAnswerRepository answers;
    @Autowired private SettlementDemoService settlement;
    @Autowired private WalletDemoService wallet;

    @Test
    void jobQuoteProjectDeliveryPaymentWalletAndWithdrawalFlow() throws Exception {
        User customer = users.findByEmailIgnoreCase(CUSTOMER).orElseThrow();
        User freelancer = users.findByEmailIgnoreCase(FREELANCER).orElseThrow();
        String title = "Flow test job " + System.nanoTime();

        assertRedirect(mockMvc.perform(post("/customer/jobs")
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("title", title)
                .param("category", "Lập trình & CNTT")
                .param("description", "A test job description long enough to pass job validation.")
                .param("requiredSkills", "Java, Spring Boot")
                .param("budget", "20000")
                .param("deadline", LocalDate.now().plusDays(30).toString())
                .param("estimatedDurationDays", "20")
                .param("priceType", "FIXED")
                .param("workType", "REMOTE")
                .param("experienceLevel", "MIDDLE")
                .param("acceptanceCriteria", "Automated tests pass and documentation is delivered."))
                .andExpect(status().is3xxRedirection()).andReturn(), "/customer/jobs/");
        Job job = jobs.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .filter(item -> title.equals(item.getTitle())).findFirst().orElseThrow();

        assertRedirect(mockMvc.perform(post("/jobs/{id}/apply", job.getId())
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf())
                .param("proposal", "I will deliver the tested application with documentation and support.")
                .param("proposedPrice", "20000")
                .param("estimatedDays", "20")
                .param("portfolioLink", "https://portfolio.example.test"))
                .andExpect(status().is3xxRedirection()).andReturn(), "/freelancer/applications");
        JobApplication application = applications.findByJobOrderByCreatedAtDesc(job).stream()
                .findFirst().orElseThrow();
        QuoteRevision quote = quotes.findTopByApplicationOrderByVersionDesc(application).orElseThrow();

        MvcResult quotePage = mockMvc.perform(get("/customer/quotations/jobs/{jobId}/applications/{applicationId}",
                        job.getId(), application.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        String quoteHtml = quotePage.getResponse().getContentAsString();
        java.util.regex.Matcher actionMatcher = java.util.regex.Pattern
                .compile("action=\"([^\"]*respond[^\"]*)\"").matcher(quoteHtml);
        List<String> renderedActions = new java.util.ArrayList<>();
        while (actionMatcher.find()) renderedActions.add(actionMatcher.group(1));
        assertTrue(quoteHtml.contains("/quotations/application/" + application.getId() + "/respond/" + quote.getId()),
                "Quote accept form must render both persisted route identifiers. canRespond="
                        + quotePage.getModelAndView().getModel().get("canRespond")
                        + ", quoteStatus=" + quote.getStatus() + ", actions=" + renderedActions);

        assertRedirect(mockMvc.perform(post("/quotations/application/{id}/respond/{revisionId}",
                        application.getId(), quote.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("action", "accept"))
                .andExpect(status().is3xxRedirection()).andReturn(),
                "/customer/quotations/jobs/" + job.getId() + "/applications/" + application.getId());

        assertRedirect(mockMvc.perform(post("/customer/applications/{id}/shortlist", application.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("value", "true").param("jobId", job.getId().toString()))
                .andExpect(status().is3xxRedirection()).andReturn(), "/customer/applicants?jobId=");

        MvcResult selected = mockMvc.perform(post("/customer/applications/{id}/accept", application.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String projectPath = selected.getResponse().getRedirectedUrl();
        assertNotNull(projectPath);
        assertTrue(projectPath.matches("/projects/\\d+"), projectPath);
        Long projectId = Long.valueOf(projectPath.substring("/projects/".length()));
        Project project = projects.findById(projectId).orElseThrow();

        assertRedirect(mockMvc.perform(post("/projects/{id}/agreement/sign", projectId)
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf()).param("expectedVersion", "1"))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);
        assertRedirect(mockMvc.perform(post("/projects/{id}/agreement/sign", projectId)
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf()).param("expectedVersion", "1"))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);

        assertRedirect(mockMvc.perform(post("/projects/{id}/milestones", projectId)
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("title", "Delivery milestone")
                .param("description", "Build, test and document the agreed application.")
                .param("amount", "20000")
                .param("deadline", LocalDate.now().plusDays(20).toString()))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);
        Milestone milestone = milestones.findByProjectOrderByCreatedAtAsc(project).get(0);

        assertRedirect(mockMvc.perform(post("/customer/payments/schedule/{id}", projectId)
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("milestoneId", milestone.getId().toString())
                .param("paymentDueDate", LocalDate.now().plusDays(5).toString()))
                .andExpect(status().is3xxRedirection()).andReturn(), "/customer/payments?tab=projects");
        assertRedirect(mockMvc.perform(post("/payment/project/{id}", projectId)
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("milestoneId", milestone.getId().toString())
                .param("method", "MOMO_DEMO"))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);
        Payment payment = payments.findByProjectOrderByCreatedAtDesc(project).get(0);
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        int paymentCount = payments.findByProjectOrderByCreatedAtDesc(project).size();
        mockMvc.perform(post("/payment/project/{id}", projectId)
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                        .param("milestoneId", milestone.getId().toString())
                        .param("method", "MOMO_DEMO"))
                .andExpect(status().is3xxRedirection());
        assertEquals(paymentCount, payments.findByProjectOrderByCreatedAtDesc(project).size(),
                "A repeated payment request must not create another payment.");

        assertRedirect(mockMvc.perform(post("/projects/{id}/milestones/{milestoneId}/status",
                        projectId, milestone.getId())
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf())
                .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);
        assertRedirect(mockMvc.perform(post("/projects/{projectId}/milestones/{milestoneId}/progress",
                        projectId, milestone.getId())
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf())
                .param("percent", "100")
                .param("note", "Implementation and automated tests are complete.")
                .param("evidenceUrl", "https://delivery.example.test"))
                .andExpect(status().is3xxRedirection()).andReturn(),
                "/freelancer/deliveries?selected=" + projectId);
        assertRedirect(mockMvc.perform(post("/projects/{projectId}/deliveries", projectId)
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf())
                .param("milestoneId", milestone.getId().toString())
                .param("externalUrl", "https://delivery.example.test")
                .param("note", "Final delivery for acceptance."))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);
        ProjectDelivery delivery = deliveries.findByProjectOrderBySubmittedAtDesc(project).get(0);

        assertRedirect(mockMvc.perform(post("/customer/progress/{projectId}/milestones/{milestoneId}/decide",
                        projectId, milestone.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("deliveryId", delivery.getId().toString())
                .param("decision", "accept"))
                .andExpect(status().is3xxRedirection()).andReturn(),
                "/customer/progress?selected=" + projectId);

        payment = payments.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        settlement.settleOne(payment.getId());
        assertEquals(PaymentStatus.DISBURSED, payments.findById(payment.getId()).orElseThrow().getStatus());

        mockMvc.perform(get("/freelancer/earnings")
                .with(user(FREELANCER).roles("FREELANCER")))
                .andExpect(status().isOk());
        assertTrue(wallet.summary(freelancer).available().compareTo(new BigDecimal("10000")) >= 0);

        assertRedirect(mockMvc.perform(post("/freelancer/earnings/banks")
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf())
                .param("bankName", "Demo Bank")
                .param("holder", "Smoke Freelancer")
                .param("accountNumber", "123456789012"))
                .andExpect(status().is3xxRedirection()).andReturn(), "/freelancer/earnings?tab=banks");
        WalletBank bank = banks.findByFreelancerOrderByIdDesc(freelancer).get(0);
        assertRedirect(mockMvc.perform(post("/freelancer/earnings/withdrawals")
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf())
                .param("bankId", bank.getId().toString())
                .param("amount", "10000"))
                .andExpect(status().is3xxRedirection()).andReturn(), "/freelancer/earnings?tab=withdrawals");
        WalletWithdrawal withdrawal = withdrawals.findByFreelancerOrderByCreatedAtDesc(freelancer).get(0);
        assertRedirect(mockMvc.perform(post("/admin/wallet-withdrawals/{id}/review", withdrawal.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()).param("accept", "true"))
                .andExpect(status().is3xxRedirection()).andReturn(), "/admin/wallet-withdrawals");
        assertRedirect(mockMvc.perform(post("/admin/wallet-withdrawals/{id}/complete", withdrawal.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn(), "/admin/wallet-withdrawals");
        assertEquals(WalletWithdrawal.State.COMPLETED,
                withdrawals.findById(withdrawal.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancellationDisputeReportAndCommunityQuestionFlows() throws Exception {
        User customer = users.findByEmailIgnoreCase(CUSTOMER).orElseThrow();
        User freelancer = users.findByEmailIgnoreCase(FREELANCER).orElseThrow();
        Project project = createWorkflowProject(customer, freelancer, "Cancellation report flow job");
        String projectPath = "/projects/" + project.getId();

        assertRedirect(mockMvc.perform(post("/projects/{id}/reports", project.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("reason", "Không đáp ứng cam kết")
                .param("description", "The agreed work is not being delivered as documented."))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);
        assertRedirect(mockMvc.perform(post("/projects/{id}/cancel", project.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("reason", "Dừng dự án để xử lý cùng đối tác."))
                .andExpect(status().is3xxRedirection()).andReturn(), projectPath);
        assertEquals(ProjectStatus.CANCELLED, projects.findById(project.getId()).orElseThrow().getStatus());

        Project disputedProject = createWorkflowProject(customer, freelancer, "Funded dispute flow job");
        Payment paid = new Payment();
        paid.setProject(disputedProject);
        paid.setPayer(customer);
        paid.setAmount(new BigDecimal("20000"));
        paid.setMethod("MOMO_DEMO");
        paid.setStatus(PaymentStatus.PAID);
        paid.setTransactionCode("FLOW-" + System.nanoTime());
        payments.saveAndFlush(paid);
        String disputedPath = "/projects/" + disputedProject.getId();
        assertRedirect(mockMvc.perform(post("/projects/{id}/cancel", disputedProject.getId())
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("reason", "Dừng dự án đã thanh toán để xử lý."))
                .andExpect(status().is3xxRedirection()).andReturn(), disputedPath);
        assertEquals(ProjectStatus.DISPUTED,
                projects.findById(disputedProject.getId()).orElseThrow().getStatus());
        assertRedirect(mockMvc.perform(post("/admin/disputes/{projectId}/note", disputedProject.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("note", "Đã đối chiếu hồ sơ và giao dịch demo."))
                .andExpect(status().is3xxRedirection()).andReturn(), "/admin/disputes");
        assertRedirect(mockMvc.perform(post("/admin/disputes/{id}/resolve", disputedProject.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn(), "/admin/disputes");
        assertEquals(ProjectStatus.DISPUTED, projects.findById(disputedProject.getId()).orElseThrow().getStatus());

        String title = "Community flow " + System.nanoTime();
        MvcResult questionResult = mockMvc.perform(post("/freelancer/community")
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf())
                .param("title", title)
                .param("body", "A community question with enough detail for a complete discussion.")
                .param("category", "Kỹ thuật"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String questionPath = questionResult.getResponse().getRedirectedUrl();
        assertNotNull(questionPath);
        assertTrue(questionPath.matches("/freelancer/community/\\d+"), questionPath);
        Long questionId = Long.valueOf(questionPath.substring("/freelancer/community/".length()));

        assertRedirect(mockMvc.perform(post("/freelancer/community/{id}/answers", questionId)
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                .param("body", "This answer provides a practical and testable solution."))
                .andExpect(status().is3xxRedirection()).andReturn(), questionPath);
        assertRedirect(mockMvc.perform(post("/freelancer/community/{id}/vote", questionId)
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn(), questionPath);
        assertRedirect(mockMvc.perform(post("/freelancer/community/{id}/bookmark", questionId)
                .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn(), "/freelancer/community");
        assertRedirect(mockMvc.perform(post("/freelancer/community/{id}/solve", questionId)
                .with(user(FREELANCER).roles("FREELANCER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn(), questionPath);
        assertTrue(questions.findById(questionId).orElseThrow().solved);
        assertFalse(answers.findByQuestionIdOrderByCreatedAtAsc(questionId).isEmpty());
    }

    @Test
    void clientReportsSupportAndMatchingRoutesRenderAndPersist() throws Exception {
        User customer = users.findByEmailIgnoreCase(CUSTOMER).orElseThrow();
        Project ownedProject = projects.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .filter(project -> "Smoke active project".equals(project.getApplication().getJob().getTitle()))
                .findFirst().orElseThrow();

        MvcResult reportsPage = mockMvc.perform(get("/customer/reports")
                        .with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        assertEquals("reports", reportsPage.getModelAndView().getModel().get("active"));
        assertTrue(reportsPage.getModelAndView().getModel().containsKey("barData"));
        assertTrue(reportsPage.getModelAndView().getModel().containsKey("pieData"));

        mockMvc.perform(get("/customer/support").with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isOk());
        assertRedirect(mockMvc.perform(get("/customer/community").with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().is3xxRedirection()).andReturn(), "/customer/support");
        assertRedirect(mockMvc.perform(post("/customer/support/reports")
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                        .param("projectId", ownedProject.getId().toString())
                        .param("reason", "Không đúng thỏa thuận")
                        .param("description", "Các hạng mục đã thống nhất chưa được bàn giao đầy đủ."))
                .andExpect(status().is3xxRedirection()).andReturn(), "/customer/support");
        TalentReport savedReport = reports.findByReporterOrderByCreatedAtDesc(customer).stream()
                .filter(report -> ownedProject.getId().equals(report.getProject().getId()))
                .findFirst().orElseThrow();
        assertEquals("PENDING", savedReport.getStatus());
        User otherCustomer = new User();
        otherCustomer.setFullName("Other Smoke Client");
        otherCustomer.setEmail("other-client@smoke.test");
        otherCustomer.setPassword("not-used-by-mock-authentication");
        otherCustomer.setRole(Role.CUSTOMER);
        otherCustomer.setEnabled(true);
        otherCustomer.setLocked(false);
        users.saveAndFlush(otherCustomer);
        long reportCount = reports.count();
        mockMvc.perform(post("/customer/support/reports")
                        .with(user("other-client@smoke.test").roles("CUSTOMER")).with(csrf())
                        .param("projectId", ownedProject.getId().toString())
                        .param("reason", "Không đúng thỏa thuận")
                        .param("description", "Thử gửi báo cáo cho dự án không thuộc tài khoản này."))
                .andExpect(status().isForbidden());
        assertEquals(reportCount, reports.count(), "Client must not report another client's project.");
        MvcResult supportHistory = mockMvc.perform(get("/customer/support")
                        .with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        assertTrue(supportHistory.getModelAndView().getModel().get("requests").toString()
                .contains(savedReport.getReason()));

        Job matchingJob = jobs.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .filter(job -> "Smoke pending quote".equals(job.getTitle())).findFirst().orElseThrow();
        MvcResult matchingPage = mockMvc.perform(get("/customer/talent").param("jobId", matchingJob.getId().toString())
                        .with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> suggestions =
                (List<java.util.Map<String, Object>>) matchingPage.getModelAndView().getModel().get("suggestions");
        java.util.Map<String, Object> freelancerMatch = suggestions.stream()
                .filter(row -> ((User) row.get("user")).getEmail().equalsIgnoreCase(FREELANCER))
                .findFirst().orElseThrow();
        assertTrue((Integer) freelancerMatch.get("score") > 0);
        assertFalse(((String) freelancerMatch.get("reason")).isBlank());
        assertTrue(matchingPage.getResponse().getContentAsString().contains("Độ phù hợp:"));
    }

    private Project createWorkflowProject(User customer, User freelancer, String title) {
        Job job = new Job();
        job.setCustomer(customer);
        job.setTitle(title);
        job.setCategory("Lập trình & CNTT");
        job.setDescription("A dedicated project for cancellation and dispute report flow tests.");
        job.setRequiredSkills("Java");
        job.setBudget(new BigDecimal("20000"));
        job.setDeadline(LocalDate.now().plusDays(20));
        job.setStatus(JobStatus.CLOSED);
        job = jobs.saveAndFlush(job);

        JobApplication application = new JobApplication();
        application.setJob(job);
        application.setFreelancer(freelancer);
        application.setProposal("Dedicated application for the report flow integration test.");
        application.setProposedPrice(new BigDecimal("20000"));
        application.setEstimatedDays(20);
        application.setStatus(ApplicationStatus.ACCEPTED);
        application = applications.saveAndFlush(application);

        Project project = new Project();
        project.setApplication(application);
        project.setCustomer(customer);
        project.setFreelancer(freelancer);
        project.setTotalPrice(new BigDecimal("20000"));
        project.setStatus(ProjectStatus.IN_PROGRESS);
        return projects.saveAndFlush(project);
    }

    private void assertRedirect(MvcResult result, String expectedPrefix) {
        String redirect = result.getResponse().getRedirectedUrl();
        assertNotNull(redirect, "POST should redirect after processing.");
        assertTrue(redirect.startsWith(expectedPrefix), "Expected redirect to " + expectedPrefix + " but was " + redirect);
    }
}
