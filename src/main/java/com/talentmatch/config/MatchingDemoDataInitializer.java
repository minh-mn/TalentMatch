package com.talentmatch.config;

import com.talentmatch.model.Role;
import com.talentmatch.model.User;
import com.talentmatch.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class MatchingDemoDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(MatchingDemoDataInitializer.class);
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public MatchingDemoDataInitializer(UserRepository users, PasswordEncoder passwordEncoder,
                                        @Value("${app.demo.matching.seed:false}") boolean enabled) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;

        List<Profile> profiles = List.of(
                new Profile("Ngọc Hà", "uiux", "Senior UI/UX Designer", "Thiết kế & Sáng tạo",
                        "Figma, UI/UX, Product Design, Prototype, Design System, User Research",
                        6, "150000", "TP. Hồ Chí Minh", "REMOTE",
                        "Thiết kế sản phẩm số, nghiên cứu người dùng, xây dựng user flow và design system; bàn giao prototype rõ ràng cho đội phát triển."),
                new Profile("Đức Anh", "graphic", "Brand & Graphic Designer", "Thiết kế & Sáng tạo",
                        "Branding, Logo, Illustrator, Photoshop, Figma, Social Media Design",
                        4, "120000", "Hà Nội", "HYBRID",
                        "Thiết kế nhận diện thương hiệu, logo, guideline và bộ ấn phẩm truyền thông cho doanh nghiệp vừa và nhỏ."),
                new Profile("Quang Minh", "backend", "Senior Java Backend Developer", "Lập trình & CNTT",
                        "Java, Spring Boot, REST API, MySQL, Spring Security, Docker",
                        7, "220000", "Đà Nẵng", "REMOTE",
                        "Phát triển backend Java Spring Boot, REST API và tích hợp cơ sở dữ liệu MySQL; chú trọng bảo mật và kiểm thử."),
                new Profile("Thảo Linh", "frontend", "Frontend React Developer", "Lập trình & CNTT",
                        "React, TypeScript, JavaScript, TailwindCSS, HTML, CSS, REST API",
                        5, "180000", "TP. Hồ Chí Minh", "REMOTE",
                        "Xây dựng giao diện web responsive bằng React và TypeScript, kết nối API và tối ưu trải nghiệm trên thiết bị di động."),
                new Profile("Hoàng Nam", "mobile", "Mobile App Developer", "Lập trình & CNTT",
                        "Flutter, Dart, Firebase, Android, iOS, REST API",
                        4, "170000", "Hà Nội", "HYBRID",
                        "Phát triển ứng dụng Flutter đa nền tảng, tích hợp Firebase, API và quy trình phát hành ứng dụng."),
                new Profile("Minh Châu", "writer", "SEO Content Writer", "Viết lách & Nội dung",
                        "SEO, Content Writing, Keyword Research, Copywriting, WordPress, Editing",
                        5, "65000", "Hà Nội", "REMOTE",
                        "Nghiên cứu từ khóa, lập dàn ý và viết bài SEO tiếng Việt; có kinh nghiệm nội dung công nghệ, giáo dục và thương mại điện tử."),
                new Profile("Gia Hân", "social", "Social Media Content Creator", "Viết lách & Nội dung",
                        "Social Media, Content Creation, Copywriting, TikTok, Instagram, Storytelling",
                        3, "55000", "TP. Hồ Chí Minh", "REMOTE",
                        "Lên kế hoạch nội dung mạng xã hội, viết caption và phát triển ý tưởng video ngắn cho thương hiệu bán lẻ và F&B."),
                new Profile("Tuấn Kiệt", "performance", "Performance Marketing Specialist", "Marketing & Bán hàng",
                        "Google Ads, Meta Ads, GA4, GTM, Conversion Tracking, SEO",
                        6, "160000", "TP. Hồ Chí Minh", "HYBRID",
                        "Tối ưu chiến dịch quảng cáo Google và Meta, thiết lập theo dõi chuyển đổi, phân tích GA4 và báo cáo hiệu quả."),
                new Profile("Bảo Trân", "seo", "Technical SEO Consultant", "Marketing & Bán hàng",
                        "Technical SEO, SEO, Google Search Console, Keyword Research, Analytics, Content Strategy",
                        5, "140000", "Hà Nội", "REMOTE",
                        "Audit SEO kỹ thuật, nghiên cứu từ khóa và xây dựng kế hoạch nội dung dựa trên dữ liệu tìm kiếm."),
                new Profile("Hữu Phước", "architect", "Interior 3D Visualization Artist", "Kỹ thuật & Kiến trúc",
                        "AutoCAD, SketchUp, 3Ds Max, Revit, Interior Design, Rendering",
                        8, "190000", "Đà Nẵng", "ONSITE",
                        "Triển khai bản vẽ nội thất, dựng phối cảnh 3D và bóc tách vật liệu cho nhà ở, văn phòng và quán cà phê."),
                new Profile("Mai Phương", "accounting", "Freelance Accountant", "Tài chính & Kế toán",
                        "Kế toán, Excel, Báo cáo tài chính, Đối soát, Thuế, Google Sheets",
                        7, "150000", "Hà Nội", "HYBRID",
                        "Hỗ trợ hạch toán chứng từ, đối soát công nợ, lập báo cáo quản trị và chuẩn hóa bảng tính cho doanh nghiệp SME."),
                new Profile("Thanh Tùng", "translator", "English-Vietnamese Translator", "Viết lách & Nội dung",
                        "Translation, English, Vietnamese, Proofreading, Localization, Editing",
                        6, "90000", "Huế", "REMOTE",
                        "Biên dịch và hiệu đính tài liệu Anh-Việt, ưu tiên tài liệu giáo dục, phần mềm và hướng dẫn sử dụng.")
        );

        long inserted = profiles.stream().filter(this::createIfMissing).count();
        log.info("Matching demo data enabled: created {} sample Freelancer profiles.", inserted);
    }

    private boolean createIfMissing(Profile profile) {
        String email = "matching.demo." + profile.key() + "@talentmatch.vn";
        if (users.existsByEmailIgnoreCase(email)) return false;

        User user = new User();
        user.setFullName(profile.name());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(Role.FREELANCER);
        user.setEnabled(true);
        user.setLocked(false);
        user.setAvailable(true);
        user.setProfessionalTitle(profile.title());
        user.setField(profile.field());
        user.setSkills(profile.skills());
        user.setExperienceYears(profile.experienceYears());
        user.setHourlyRate(new BigDecimal(profile.hourlyRate()));
        user.setLocation(profile.location());
        user.setWorkPreference(profile.workPreference());
        user.setRating(4.5 + (profile.experienceYears() % 5) * 0.1);
        user.setCompletedProjects(profile.experienceYears() * 3);
        user.setBio(profile.bio() + " Hồ sơ mẫu minh họa cho tính năng gợi ý TalentMatch.");
        users.save(user);
        return true;
    }

    private record Profile(String name, String key, String title, String field, String skills,
                           int experienceYears, String hourlyRate, String location,
                           String workPreference, String bio) {}
}
