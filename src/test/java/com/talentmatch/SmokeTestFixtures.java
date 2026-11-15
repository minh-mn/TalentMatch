package com.talentmatch;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@TestConfiguration
public class SmokeTestFixtures {
    @Bean
    CommandLineRunner smokeTestData(UserRepository users, JobRepository jobs,
                                    JobApplicationRepository applications, QuoteRevisionRepository quotes,
                                    ProjectRepository projects, AgreementRevisionRepository agreements,
                                    CommunityQuestionRepository questions, PasswordEncoder encoder) {
        return args -> {
            if (users.existsByEmailIgnoreCase("client@smoke.test")) return;

            User customer = user(users, encoder, "client@smoke.test", Role.CUSTOMER, "Smoke Client");
            User freelancer = user(users, encoder, "freelancer@smoke.test", Role.FREELANCER, "Smoke Freelancer");
            freelancer.setProfessionalTitle("Java Developer");
            freelancer.setSkills("Java, Spring Boot, MySQL");
            freelancer.setExperienceYears(5);
            users.saveAndFlush(freelancer);
            user(users, encoder, "admin@smoke.test", Role.ADMIN, "Smoke Admin");

            Job quoteJob = job(jobs, customer, "Smoke pending quote");
            JobApplication quoteApplication = application(applications, quoteJob, freelancer);
            QuoteRevision pending = quote(quotes, quoteApplication, freelancer, QuoteStatus.PENDING);

            Job projectJob = job(jobs, customer, "Smoke active project");
            JobApplication projectApplication = application(applications, projectJob, freelancer);
            QuoteRevision accepted = quote(quotes, projectApplication, freelancer, QuoteStatus.ACCEPTED);
            Project project = new Project();
            project.setApplication(projectApplication);
            project.setCustomer(customer);
            project.setFreelancer(freelancer);
            project.setTotalPrice(accepted.getAmount());
            project.setStatus(ProjectStatus.IN_PROGRESS);
            projects.saveAndFlush(project);

            AgreementRevision agreement = new AgreementRevision();
            agreement.setProject(project);
            agreement.setVersion(1);
            agreement.setTerms("Smoke test agreement terms for both parties.");
            agreement.setScopeOfWork(accepted.getScope());
            agreement.setAmount(accepted.getAmount());
            agreement.setEstimatedDays(accepted.getEstimatedDays());
            agreements.saveAndFlush(agreement);

            CommunityQuestion question = new CommunityQuestion();
            question.author = freelancer;
            question.title = "Smoke test community question";
            question.body = "A sufficiently long body for a smoke-test question.";
            question.category = "Kỹ thuật";
            questions.saveAndFlush(question);
        };
    }

    private User user(UserRepository users, PasswordEncoder encoder, String email, Role role, String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPassword(encoder.encode("SmokePassword123!"));
        user.setRole(role);
        user.setEnabled(true);
        user.setLocked(false);
        return users.saveAndFlush(user);
    }

    private Job job(JobRepository jobs, User customer, String title) {
        Job job = new Job();
        job.setCustomer(customer);
        job.setTitle(title);
        job.setCategory("Lập trình & CNTT");
        job.setDescription("A smoke test job with enough details for route rendering.");
        job.setRequiredSkills("Java, Spring Boot");
        job.setBudget(new BigDecimal("20000000"));
        job.setDeadline(LocalDate.now().plusDays(30));
        job.setEstimatedDurationDays(20);
        job.setStatus(JobStatus.OPEN);
        return jobs.saveAndFlush(job);
    }

    private JobApplication application(JobApplicationRepository applications, Job job, User freelancer) {
        JobApplication application = new JobApplication();
        application.setJob(job);
        application.setFreelancer(freelancer);
        application.setProposal("I can complete this project with tested and documented delivery.");
        application.setProposedPrice(new BigDecimal("15000000"));
        application.setEstimatedDays(20);
        application.setStatus(ApplicationStatus.SUBMITTED);
        return applications.saveAndFlush(application);
    }

    private QuoteRevision quote(QuoteRevisionRepository quotes, JobApplication application,
                               User author, QuoteStatus status) {
        QuoteRevision quote = new QuoteRevision();
        quote.setApplication(application);
        quote.setAuthor(author);
        quote.setVersion(1);
        quote.setAmount(new BigDecimal("15000000"));
        quote.setEstimatedDays(20);
        quote.setScope("Complete the agreed Java application and provide tests and documentation.");
        quote.setRevisionPolicy("Additional requirements need agreement from both parties.");
        quote.setStatus(status);
        return quotes.saveAndFlush(quote);
    }
}
