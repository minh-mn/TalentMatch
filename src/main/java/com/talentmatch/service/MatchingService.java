package com.talentmatch.service;

import com.talentmatch.model.Job;
import com.talentmatch.model.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/**
 * Explainable candidate ranking using corpus-aware TF-IDF cosine similarity and profile features.
 * It is a data-mining recommendation, not a trained language model or a guarantee of fit.
 */
@Service
public class MatchingService {
    public record Recommendation(int score, String reason, int skillPoints, int textPoints,
                                 int fieldPoints, int experiencePoints, int budgetPoints,
                                 int availabilityPoints, BigDecimal estimatedBudget,
                                 boolean budgetEvaluated, int requiredExperienceYears,
                                 List<String> matchedSkills, List<String> missingSkills) {}
    public static final int HIGH_MATCH_THRESHOLD = 65;

    private static final Set<String> STOPWORDS = Set.of(
            "va", "la", "cho", "voi", "cua", "cac", "mot", "trong", "theo", "duoc", "tren", "tu", "de",
            "an", "dich", "vu", "cong", "viec", "du", "toi", "the", "and", "or", "of", "for", "to", "a",
            "with", "in", "on", "is", "this", "that", "can", "need", "will"
    );

    public Recommendation recommend(Job job, User freelancer) {
        if (job == null || freelancer == null) return emptyRecommendation();
        return rank(job, List.of(freelancer)).getOrDefault(
                freelancer, emptyRecommendation());
    }

    public Map<User, Recommendation> rank(Job job, Collection<User> freelancers) {
        if (job == null || freelancers == null || freelancers.isEmpty()) return Map.of();

        String jobSkills = safe(job.getRequiredSkills());
        String jobText = String.join(" ", jobSkills, safe(job.getTitle()), safe(job.getDescription()));
        List<User> candidates = freelancers.stream().filter(Objects::nonNull).toList();
        if (candidates.isEmpty()) return Map.of();

        List<Map<String, Integer>> documents = new ArrayList<>();
        documents.add(bag(jobText));
        for (User candidate : candidates) {
            documents.add(bag(profileText(candidate)));
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Map<String, Integer> document : documents) {
            document.keySet().forEach(token -> documentFrequency.merge(token, 1, Integer::sum));
        }

        Map<User, Recommendation> results = new LinkedHashMap<>();
        for (User candidate : candidates) {
            double skillScore = cosine(jobSkills, safe(candidate.getSkills()), documentFrequency, documents.size());
            double textScore = cosine(jobText, profileText(candidate), documentFrequency, documents.size());
            int skillPoints = (int) Math.round(50 * skillScore);
            int textPoints = (int) Math.round(10 * textScore);
            int fieldPoints = 0;
            int experiencePoints = 0;
            int budgetPoints = 0;
            int availabilityPoints = 0;
            BigDecimal estimatedBudget = null;
            boolean budgetEvaluated = false;
            int score = skillPoints + textPoints;
            List<String> reasons = new ArrayList<>();
            reasons.add("Tương đồng kỹ năng " + Math.round(skillScore * 100) + "%");
            if (!normalize(job.getCategory()).isEmpty()
                    && normalize(job.getCategory()).equals(normalize(candidate.getField()))) {
                fieldPoints = 20;
                score += fieldPoints;
                reasons.add("Đúng lĩnh vực");
            }

            int years = Optional.ofNullable(candidate.getExperienceYears()).orElse(0);
            String level = safe(job.getExperienceLevel()).toUpperCase(Locale.ROOT);
            int minYears = switch (level) {
                case "SENIOR", "EXPERT" -> 5;
                case "MIDDLE", "INTERMEDIATE" -> 3;
                case "JUNIOR" -> 1;
                default -> 0;
            };
            if (minYears > 0 && years >= minYears) {
                experiencePoints = 10;
                score += experiencePoints;
                reasons.add("Đạt yêu cầu kinh nghiệm");
            }

            if (candidate.getHourlyRate() != null && job.getBudget() != null) {
                int days = Math.max(1, Optional.ofNullable(job.getEstimatedDurationDays()).orElse(14));
                estimatedBudget = candidate.getHourlyRate().multiply(BigDecimal.valueOf(4L * days));
                budgetEvaluated = true;
                if (estimatedBudget.compareTo(job.getBudget()) <= 0) {
                    budgetPoints = 5;
                    score += budgetPoints;
                    reasons.add("Ước tính chi phí trong ngân sách (4 giờ/ngày)");
                }
            }
            if (Boolean.TRUE.equals(candidate.getAvailable())) {
                availabilityPoints = 5;
                score += availabilityPoints;
                reasons.add("Đang sẵn sàng nhận việc");
            }
            List<String> required = requiredSkillTerms(jobSkills);
            Set<String> candidateSkills = requiredSkillTerms(safe(candidate.getSkills())).stream()
                    .map(MatchingService::normalize)
                    .collect(java.util.stream.Collectors.toSet());
            List<String> matchedSkills = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();
            for (String term : required) {
                boolean matched = candidateSkills.contains(normalize(term));
                (matched ? matchedSkills : missingSkills).add(term);
            }
            results.put(candidate, new Recommendation(
                    Math.min(100, Math.max(0, score)), String.join(" · ", reasons),
                    skillPoints, textPoints, fieldPoints, experiencePoints, budgetPoints,
                    availabilityPoints, estimatedBudget, budgetEvaluated, minYears,
                    List.copyOf(matchedSkills), List.copyOf(missingSkills)));
        }
        return results;
    }

    private static Recommendation emptyRecommendation() {
        return new Recommendation(0, "Thiếu hồ sơ hoặc thông tin công việc.",
                0, 0, 0, 0, 0, 0, null, false, 0, List.of(), List.of());
    }

    private static List<String> requiredSkillTerms(String skills) {
        if (skills.isBlank()) return List.of();
        return Arrays.stream(skills.split("[,;\\n]+"))
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
    }

    private static String profileText(User user) {
        return String.join(" ", safe(user.getSkills()), safe(user.getProfessionalTitle()), safe(user.getBio()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(safe(value).toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static Map<String, Integer> bag(String source) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : normalize(source).split("\\s+")) {
            if (token.length() > 1 && !STOPWORDS.contains(token)) frequencies.merge(token, 1, Integer::sum);
        }
        return frequencies;
    }

    private static double cosine(String first, String second, Map<String, Integer> documentFrequency,
                                 int documentCount) {
        Map<String, Integer> one = bag(first);
        Map<String, Integer> two = bag(second);
        if (one.isEmpty() || two.isEmpty()) return 0;

        Set<String> vocabulary = new HashSet<>(one.keySet());
        vocabulary.addAll(two.keySet());
        double dot = 0;
        double normOne = 0;
        double normTwo = 0;
        for (String word : vocabulary) {
            double idf = 1 + Math.log((documentCount + 1.0) / (documentFrequency.getOrDefault(word, 0) + 1.0));
            double tfidfOne = one.getOrDefault(word, 0) * idf;
            double tfidfTwo = two.getOrDefault(word, 0) * idf;
            dot += tfidfOne * tfidfTwo;
            normOne += tfidfOne * tfidfOne;
            normTwo += tfidfTwo * tfidfTwo;
        }
        return normOne == 0 || normTwo == 0 ? 0 : Math.min(1, dot / Math.sqrt(normOne * normTwo));
    }
}
