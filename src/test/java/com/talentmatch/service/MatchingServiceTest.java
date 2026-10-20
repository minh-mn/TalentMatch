package com.talentmatch.service;
import com.talentmatch.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MatchingServiceTest {
 @Test void favorsRelevantSkillsAndExplainsWhy(){
     var engine=new MatchingService();
     Job job=new Job();job.setRequiredSkills("Java, Spring Boot, MySQL");
     job.setCategory("Lập trình & CNTT");job.setDescription("Phát triển REST API Java Spring Boot");
     User relevant=new User();relevant.setSkills("Java, MySQL, Spring Boot, REST API");
     relevant.setField("Lập trình & CNTT"); relevant.setExperienceYears(5);
     User unrelated=new User();unrelated.setSkills("Photography, Blender, Premiere Pro");
     unrelated.setField("Thiết kế & Sáng tạo");unrelated.setExperienceYears(0);
     var good=engine.recommend(job,relevant);var bad=engine.recommend(job,unrelated);
     assertTrue(good.score()>bad.score());
     assertTrue(good.reason().contains("Tương đồng kỹ năng"));
 }
}
