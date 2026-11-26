package com.talentmatch.service;

import com.talentmatch.model.Job;
import com.talentmatch.model.User;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;

/** Explainable rule-based matching. No trained ML model is used. */
@Service
public class AiMatchingService {
    public record MatchResult(long id, String title, int score, String skills, String reason, String url) {}
    private static final Map<String,String> ALIASES=Map.ofEntries(
        Map.entry("javascript","js"), Map.entry("js","js"),
        Map.entry("spring boot","spring boot"), Map.entry("springboot","spring boot"),
        Map.entry("reactjs","react"), Map.entry("react js","react"),
        Map.entry("ui ux","ui ux"), Map.entry("ui/ux","ui ux"));
    public String normalize(String input) {
        if (input==null) return "";
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT).replace('đ','d'),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9+#.]+", " ").trim().replaceAll("\\s+", " ");
    }
    private Set<String> skillSet(String value) {
        Set<String> skills=new LinkedHashSet<>();
        if(value==null) return skills;
        for(String part:value.split("[,;\\n|]+")) {
            String s=normalize(part);
            if (!s.isBlank()) skills.add(ALIASES.getOrDefault(s,s));
        }
        return skills;
    }
    private int expLevel(String level) {
        String s=normalize(level);
        if(s.contains("senior")||s.contains("expert")) return 5;
        if(s.contains("middle")||s.contains("mid")) return 2;
        return 0;
    }
    public MatchResult rankJob(User freelancer, Job job) {
        Set<String> wanted=skillSet(job.getRequiredSkills());
        Set<String> owned=skillSet(freelancer.getSkills());
        Set<String> matched=new LinkedHashSet<>(wanted); matched.retainAll(owned);
        int skillScore=wanted.isEmpty()?0:(int)Math.round(45.0*matched.size()/wanted.size());
        int years=Optional.ofNullable(freelancer.getExperienceYears()).orElse(0);
        int required=expLevel(job.getExperienceLevel());
        int experience=(int)Math.round(20.0*Math.min(1.0,(years+1.0)/(required+1.0)));
        int field= !normalize(freelancer.getField()).isBlank() && normalize(job.getCategory()).equals(normalize(freelancer.getField())) ? 15:0;
        int format= !normalize(freelancer.getWorkPreference()).isBlank() && normalize(freelancer.getWorkPreference()).equals(normalize(job.getWorkType())) ?10:0;
        // An hourly rate is only comparable with a job budget when the offer is hourly.
        boolean comparable="HOURLY".equalsIgnoreCase(job.getPriceType()) && job.getBudget()!=null && freelancer.getHourlyRate()!=null;
        int budget=comparable && freelancer.getHourlyRate().compareTo(job.getBudget())<=0 ? 10:0;
        // When price units differ, exclude the budget criterion rather than guessing a price.
        int maximum=comparable?100:90;
        int score=(int)Math.round(100.0*(skillScore+experience+field+format+budget)/maximum);
        score=Math.min(100,Math.max(0,score));
        // Required skill mismatch cannot rank as a strong match on soft criteria alone.
        if (!wanted.isEmpty() && matched.isEmpty()) score=Math.min(score,39);
        String reason="Kỹ năng trùng: "+matched.size()+"/"+wanted.size()+
           (matched.isEmpty()?"":" ("+String.join(", ",matched)+")")+
           " · Kinh nghiệm: "+years+" năm"+
           (format>0?" · Đúng hình thức làm việc":" · Chưa khớp hình thức làm việc")+
           (comparable?" · Đã đối chiếu đơn giá theo giờ":" · Chưa so sánh ngân sách vì thiếu đơn vị giá tương đương");
        return new MatchResult(job.getId(),job.getTitle(),score,job.getRequiredSkills(),reason,"/jobs/"+job.getId());
    }
    public MatchResult rankFreelancer(Job job,User freelancer) {
        MatchResult result=rankJob(freelancer,job);
        return new MatchResult(freelancer.getId(),freelancer.getFullName(),result.score(),freelancer.getSkills(),result.reason(),"/freelancers/"+freelancer.getId());
    }
}
