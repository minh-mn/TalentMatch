package com.talentmatch.service;

import com.talentmatch.model.Job;
import com.talentmatch.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingServiceTest {
    private final MatchingService engine = new MatchingService();

    @Test
    void ranksRelevantProfilesHigherAndExplainsMatchFactors() {
        Job job = job();
        User relevant = freelancer();
        relevant.setSkills("Java, Spring Boot, MySQL");
        relevant.setField("Lap trinh & CNTT");
        relevant.setExperienceYears(5);
        relevant.setHourlyRate(new BigDecimal("1000"));
        relevant.setAvailable(true);
        relevant.setProfessionalTitle("Senior Java Developer");
        relevant.setBio("REST API development");

        User unrelated = freelancer();
        unrelated.setSkills("Photography, Blender, Premiere Pro");
        unrelated.setField("Thiet ke & Sang tao");
        unrelated.setExperienceYears(0);

        var matches = engine.rank(job, List.of(relevant, unrelated));

        assertTrue(matches.get(relevant).score() > matches.get(unrelated).score());
        assertTrue(matches.get(relevant).reason().contains("Tương đồng kỹ năng"));
        assertTrue(matches.get(relevant).reason().contains("Đúng lĩnh vực"));
        assertEquals(100, matches.get(relevant).score());
        var detailed = matches.get(relevant);
        assertEquals(detailed.score(), detailed.skillPoints() + detailed.textPoints()
                + detailed.fieldPoints() + detailed.experiencePoints() + detailed.budgetPoints()
                + detailed.availabilityPoints());
        assertEquals(List.of("Java", "Spring Boot", "MySQL"), detailed.matchedSkills());
        assertTrue(detailed.missingSkills().isEmpty());
        assertEquals(new BigDecimal("56000"), detailed.estimatedBudget());
        assertTrue(detailed.budgetEvaluated());
    }

    @Test
    void handlesMissingInputsAndEmptyCandidateSets() {
        assertEquals(0, engine.recommend(null, freelancer()).score());
        assertEquals(0, engine.recommend(job(), null).score());
        assertTrue(engine.rank(job(), List.of()).isEmpty());
    }

    @Test
    void doesNotClaimExperienceMatchWhenJobHasNoRecognizedLevel() {
        Job job = job();
        job.setExperienceLevel(null);
        User candidate = freelancer();
        candidate.setSkills("Java");
        candidate.setExperienceYears(0);

        var recommendation = engine.recommend(job, candidate);

        assertFalse(recommendation.reason().contains("Đạt yêu cầu kinh nghiệm"));
        assertEquals(0, recommendation.requiredExperienceYears());
        assertEquals(0, recommendation.experiencePoints());
    }

    @Test
    void matchingScoreIsBoundedToZeroThroughOneHundred() {
        Job job = job();
        User candidate = freelancer();
        candidate.setSkills("Java, Spring Boot, MySQL");
        candidate.setField(job.getCategory());
        candidate.setExperienceYears(20);
        candidate.setAvailable(true);
        candidate.setHourlyRate(BigDecimal.ZERO);

        var recommendation = engine.recommend(job, candidate);

        assertTrue(recommendation.score() >= 0);
        assertTrue(recommendation.score() <= 100);
    }

    @Test
    void recommendationsChangeWhenSelectedJobChanges() {
        User developer = freelancer();
        developer.setSkills("Java, Spring Boot, MySQL, REST API");
        developer.setProfessionalTitle("Java Backend Developer");
        developer.setField("Lập trình & CNTT");
        developer.setExperienceYears(6);
        developer.setAvailable(true);

        User designer = freelancer();
        designer.setSkills("Figma, UI/UX, Product Design, Prototype");
        designer.setProfessionalTitle("UI/UX Designer");
        designer.setField("Thiết kế & Sáng tạo");
        designer.setExperienceYears(4);
        designer.setAvailable(true);

        Job designJob = job();
        designJob.setTitle("Thiết kế UI/UX ứng dụng");
        designJob.setCategory("Thiết kế & Sáng tạo");
        designJob.setRequiredSkills("Figma, UI/UX, Prototype");
        designJob.setDescription("Thiết kế flow và prototype sản phẩm số bằng Figma.");

        var designMatches = engine.rank(designJob, List.of(developer, designer));
        var developmentMatches = engine.rank(job(), List.of(developer, designer));

        assertTrue(designMatches.get(designer).score() > designMatches.get(developer).score());
        assertTrue(developmentMatches.get(developer).score() > developmentMatches.get(designer).score());
    }

    private Job job() {
        Job job = new Job();
        job.setTitle("Senior Java Developer");
        job.setRequiredSkills("Java, Spring Boot, MySQL");
        job.setCategory("Lập trình & CNTT");
        job.setDescription("REST API development");
        job.setExperienceLevel("SENIOR");
        job.setBudget(new BigDecimal("1000000"));
        job.setEstimatedDurationDays(14);
        return job;
    }

    private User freelancer() {
        User freelancer = new User();
        freelancer.setExperienceYears(0);
        freelancer.setAvailable(false);
        return freelancer;
    }
}
