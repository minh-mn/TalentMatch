package com.talentmatch.service;

import com.talentmatch.model.Job;
import com.talentmatch.model.User;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/**
 * Explainable text matching for the demo. TF-IDF cosine similarity is an information-retrieval
 * technique, NOT a trained large language model. Scores should not be treated as guarantees.
 */
@Service
public class MatchingService {
    public record Recommendation(int score,String reason){}
    private static final Set<String> STOPWORDS=Set.of(
        "va","la","cho","voi","cua","cac","mot","trong","theo","duoc","tren","tu","de",
        "an","dich","vu","cong","viec","du","toi","the","and","or","of","for","to","a",
        "with","in","on","is","this","that","can","need","will");
    public Recommendation recommend(Job job,User freelancer){
        if(job==null||freelancer==null)return new Recommendation(0,"Thiếu hồ sơ hoặc thông tin công việc.");
        String needed=safe(job.getRequiredSkills());
        String actual=safe(freelancer.getSkills());
        double skillScore=cosine(needed,actual);
        double textScore=cosine(needed+" "+safe(job.getDescription()),actual+" "+safe(freelancer.getBio()));
        int score=(int)Math.round(50*skillScore+10*textScore);
        List<String> why=new ArrayList<>();
        why.add("Tương đồng kỹ năng " + Math.round(skillScore*100) + "%");
        if(normalize(job.getCategory()).equals(normalize(freelancer.getField()))){score+=20;why.add("Đúng lĩnh vực");}
        int years=Optional.ofNullable(freelancer.getExperienceYears()).orElse(0);
        String level=safe(job.getExperienceLevel()).toUpperCase(Locale.ROOT);
        int minYears=switch(level){
            case "SENIOR","EXPERT" -> 5;
            case "MIDDLE","INTERMEDIATE" -> 3;
            case "JUNIOR" -> 1;
            default -> 0;
        };
        if(years>=minYears){score+=10;why.add("Đạt yêu cầu kinh nghiệm");}
        // Budget assumption for recommendations only: 4 billable hours per planned day.
        if(freelancer.getHourlyRate()!=null&&job.getBudget()!=null){
            int days=Math.max(1,Optional.ofNullable(job.getEstimatedDurationDays()).orElse(14));
            BigDecimal estimate=freelancer.getHourlyRate().multiply(BigDecimal.valueOf(4L*days));
            if(estimate.compareTo(job.getBudget())<=0){score+=5;why.add("Ước tính chi phí trong ngân sách (4 giờ/ngày)");}
        }
        if(Boolean.TRUE.equals(freelancer.getAvailable())){score+=5;why.add("Đang sẵn sàng nhận việc");}
        return new Recommendation(Math.min(100,Math.max(0,score)),String.join(" · ",why));
    }
    private static String safe(String s){return s==null?"":s;}
    private static String normalize(String s){
        return Normalizer.normalize(safe(s).toLowerCase(Locale.ROOT).replace('đ','d'),Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","").replaceAll("[^a-z0-9]+"," ").trim();
    }
    private static Map<String,Integer> bag(String source){
        Map<String,Integer> bag=new HashMap<>();
        for(String token:normalize(source).split("\\s+")){
            if(token.length()>1&&!STOPWORDS.contains(token))bag.merge(token,1,Integer::sum);
        }
        return bag;
    }
    private static double cosine(String s1,String s2){
        Map<String,Integer> one=bag(s1),two=bag(s2);
        if(one.isEmpty()||two.isEmpty())return 0;
        Set<String> vocab=new HashSet<>(one.keySet());vocab.addAll(two.keySet());
        double dot=0,n1=0,n2=0;
        for(String word:vocab){
            int a=one.getOrDefault(word,0),b=two.getOrDefault(word,0);
            int df=(a>0?1:0)+(b>0?1:0);
            double idf=1+Math.log(3.0/(df+1));
            double tfidf1=a*idf,tfidf2=b*idf;
            dot+=tfidf1*tfidf2;n1+=tfidf1*tfidf1;n2+=tfidf2*tfidf2;
        }
        return n1==0||n2==0?0:Math.min(1,dot/Math.sqrt(n1*n2));
    }
}
