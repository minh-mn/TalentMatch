package com.talentmatch.config;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final PaymentRepository payments;
    private final SavedJobRepository savedJobs;
    private final PasswordEncoder encoder;
    private final boolean seed;

    public DataInitializer(UserRepository users, JobRepository jobs, JobApplicationRepository applications,
                           ProjectRepository projects, MilestoneRepository milestones, PaymentRepository payments,
                           SavedJobRepository savedJobs, PasswordEncoder encoder, @Value("${app.demo.seed:true}") boolean seed) {
        this.users = users;
        this.jobs = jobs;
        this.applications = applications;
        this.projects = projects;
        this.milestones = milestones;
        this.payments = payments;
        this.savedJobs = savedJobs;
        this.encoder = encoder;
        this.seed = seed;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seed) return;
        // Do not overwrite real Client changes on every application restart after demo data has been seeded.
        if (jobs.count() >= 375) return;

        User admin = ensure("Quản trị TalentMatch", "admin@talentmatch.vn", Role.ADMIN, "admin123");
        User client = ensure("Nguyễn Minh Anh", "client@talentmatch.vn", Role.CUSTOMER, "123456");
        client.setCompanyName("Nova Digital");
        client.setCompanySize("11-50");
        client.setContactName("Nguyễn Minh Anh");
        client.setTaxCode("0318888888");
        client.setField("Công nghệ & Thương mại điện tử");
        client.setLocation("TP. Hồ Chí Minh");
        client.setPhone("0909123456");
        client.setWebsite("https://novadigital.vn");
        client.setBio("Nova Digital xây dựng sản phẩm số, website thương mại điện tử và chiến dịch tăng trưởng cho doanh nghiệp Việt Nam.");
        users.save(client);

        User client2 = ensure("Trần Quốc Bảo", "customer2@talentmatch.vn", Role.CUSTOMER, "123456");
        client2.setCompanyName("TechVision");
        client2.setCompanySize("51-100");
        client2.setContactName("Trần Quốc Bảo");
        client2.setField("Phần mềm");
        client2.setLocation("Hà Nội");
        client2.setPhone("0911222333");
        client2.setBio("TechVision thường xuyên hợp tác Freelancer cho các dự án frontend, content và thiết kế.");
        users.save(client2);

        User client3 = ensure("Lê Khánh Linh", "customer3@talentmatch.vn", Role.CUSTOMER, "123456");
        client3.setCompanyName("EduSpark Academy");
        client3.setCompanySize("11-50");
        client3.setContactName("Lê Khánh Linh");
        client3.setField("Giáo dục");
        client3.setLocation("Hà Nội");
        client3.setPhone("0909888777");
        users.save(client3);

        // Doanh nghiệp demo dùng cho nhóm job nổi bật ở trang "Tìm dự án".
        User vega = ensure("Vega Dynamics", "vega@talentmatch.vn", Role.CUSTOMER, "123456");
        vega.setCompanyName("Vega Dynamics");
        vega.setCompanySize("51-100");
        vega.setField("3D / Game / Media");
        vega.setLocation("TP. Hồ Chí Minh");
        users.save(vega);

        User frameStudio = ensure("Frame Studio", "frame@talentmatch.vn", Role.CUSTOMER, "123456");
        frameStudio.setCompanyName("Frame Studio");
        frameStudio.setCompanySize("11-50");
        frameStudio.setField("Media & Production");
        frameStudio.setLocation("TP. Hồ Chí Minh");
        users.save(frameStudio);

        User littleBean = ensure("Little Bean Coffee", "littlebean@talentmatch.vn", Role.CUSTOMER, "123456");
        littleBean.setCompanyName("Little Bean Coffee");
        littleBean.setCompanySize("11-50");
        littleBean.setField("F&B");
        littleBean.setLocation("TP. Hồ Chí Minh");
        users.save(littleBean);

        User talentMatchClient = ensure("TalentMatch Team", "talentmatch.hiring@talentmatch.vn", Role.CUSTOMER, "123456");
        talentMatchClient.setCompanyName("TalentMatch");
        talentMatchClient.setCompanySize("11-50");
        talentMatchClient.setField("Công nghệ");
        talentMatchClient.setLocation("TP. Hồ Chí Minh");
        users.save(talentMatchClient);

        User f1 = freelancer("Nguyễn Minh Anh", "freelancer@talentmatch.vn", "UI/UX Designer", "Thiết kế & Sáng tạo", "Figma, UI/UX, Product Design, Prototype, Design System", 4, "250000", "TP. Hồ Chí Minh", 4.9, "Thiết kế sản phẩm số với 4 năm kinh nghiệm, tập trung trải nghiệm người dùng, design system và handoff rõ ràng cho đội phát triển.");
        User f2 = freelancer("Trần Quốc Bảo", "developer@talentmatch.vn", "Full-stack Developer", "Lập trình & CNTT", "Java, Spring Boot, MySQL, React, Node.js, AWS", 5, "300000", "Đà Nẵng", 4.8, "Full-stack Developer chuyên Java Spring Boot, REST API, MySQL và React. Ưu tiên kiến trúc sạch và bàn giao có tài liệu.");
        User f3 = freelancer("Phạm Thảo Vy", "writer@talentmatch.vn", "Content Writer", "Viết lách & Nội dung", "Content, SEO, Social Media, Copywriting", 3, "150000", "Hà Nội", 4.9, "Content Writer chuyên nội dung thương hiệu, SEO và social media trong lĩnh vực làm đẹp, bán lẻ và công nghệ.");
        User f4 = freelancer("Lê Quang Hưng", "marketer@talentmatch.vn", "Performance Marketer", "Marketing & Bán hàng", "Meta Ads, Google Ads, SEO, Landing Page", 6, "280000", "TP. Hồ Chí Minh", 4.7, "Tập trung tăng trưởng chuyển đổi, thiết kế funnel và tối ưu chi phí quảng cáo cho SME.");
        User f5 = freelancer("Bùi Minh Tâm", "architect@talentmatch.vn", "CAD Designer", "Kỹ thuật & Kiến trúc", "AutoCAD, SketchUp, 3Ds Max, Revit", 5, "260000", "Hải Phòng", 4.8, "Thiết kế kỹ thuật, dựng phối cảnh nội thất và bóc tách bản vẽ cho nhà ở, shophouse và quán cà phê.");
        users.saveAll(List.of(f1, f2, f3, f4, f5));

        ensureJobIfAbsent(client, "Thiết kế bộ nhận diện thương hiệu", "Thiết kế & Sáng tạo", "Cần thiết kế logo, hệ màu, typography, card visit và banner social cho thương hiệu mỹ phẩm mới. Yêu cầu file nguồn và guideline sử dụng rõ ràng.", "Branding, Logo, Figma, Illustrator", "12000000", 18, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client2, "Lập trình website thương mại điện tử", "Lập trình & CNTT", "Xây dựng website bán hàng gồm sản phẩm, giỏ hàng, checkout, đơn hàng, quản trị và báo cáo. Ưu tiên Java Spring Boot, MySQL và giao diện responsive.", "Java, Spring Boot, MySQL, React", "28000000", 35, "HYBRID", "SENIOR");
        ensureJobIfAbsent(client, "Viết bài content ngành làm đẹp", "Viết lách & Nội dung", "Viết bộ 20 bài content social cho thương hiệu skincare, bao gồm caption, định hướng hình ảnh và lịch đăng. Nội dung cần đúng insight người dùng trẻ.", "Content, SEO, Social Media", "5500000", 20, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client2, "Tối ưu SEO website dịch vụ", "Marketing & Bán hàng", "Audit SEO technical, nghiên cứu từ khóa, đề xuất content plan và theo dõi hiệu quả trong 8 tuần. Cần báo cáo rõ ràng theo từng giai đoạn.", "SEO, Google Analytics, Keyword Research", "9000000", 45, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client3, "Tuyển gia sư online và cộng tác viên tuyển sinh", "Đào tạo & Giáo dục", "Dạy online qua Zoom các môn Toán, Lý, Hóa, Sinh, tiếng Anh và kết hợp tư vấn tuyển sinh. Linh hoạt thời gian, ưu tiên ứng viên có kỹ năng giao tiếp tốt.", "Teaching, Zoom, Communication", "6000000", 12, "REMOTE", "JUNIOR");
        ensureJobIfAbsent(client3, "Tuyển giáo viên tiếng Anh online part-time", "Đào tạo & Giáo dục", "Phụ trách lớp online 1-1 hoặc nhóm nhỏ, xây dựng bài giảng cơ bản và theo dõi tiến độ học viên. Có thể dạy buổi tối hoặc cuối tuần.", "English Teaching, ESL, Pronunciation", "8000000", 15, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client3, "Tuyển CTV Content Creator Giáo dục", "Viết lách & Nội dung", "Viết nội dung fanpage, landing page và email nurturing cho trung tâm giáo dục. Cần giọng văn rõ, dễ hiểu và có kinh nghiệm social marketing.", "Content Creation, Copywriting, Social Media", "5000000", 14, "REMOTE", "JUNIOR");
        ensureJobIfAbsent(client2, "Thiết kế landing page tuyển dụng", "Thiết kế & Sáng tạo", "Thiết kế landing page tuyển dụng theo guideline thương hiệu, tối ưu hiển thị trên mobile và desktop.", "Landing Page, Figma, UI Design", "7000000", 10, "REMOTE", "JUNIOR");
        ensureJobIfAbsent(client, "Thiết kế nội thất quán cà phê", "Kỹ thuật & Kiến trúc", "Lên layout, concept và phối cảnh 3D cho quán cà phê diện tích 110m2. Cần moodboard, bản vẽ cơ bản và vật liệu đề xuất.", "AutoCAD, SketchUp, Interior Design", "18000000", 24, "ONSITE", "MIDDLE");
        ensureJobIfAbsent(client, "Tư vấn pháp lý hợp đồng dịch vụ", "Luật & Pháp lý", "Rà soát mẫu hợp đồng dịch vụ phần mềm, điều chỉnh điều khoản thanh toán, bảo mật và trách nhiệm hai bên.", "Pháp lý, Hợp đồng, Tư vấn", "3500000", 7, "REMOTE", "SENIOR");
        ensureJobIfAbsent(client2, "Nhân sự part-time tuyển mass", "Hành chính & Nhân sự", "Hỗ trợ đăng tin tuyển dụng, lọc CV và hẹn lịch phỏng vấn cho vị trí telesales và chăm sóc khách hàng.", "Recruitment, Excel, Communication", "4500000", 18, "HYBRID", "JUNIOR");
        ensureJobIfAbsent(client, "Kế toán tổng hợp theo dự án 3 tháng", "Tài chính & Kế toán", "Hỗ trợ hạch toán chứng từ, đối soát công nợ và lên báo cáo quản trị cho doanh nghiệp SME.", "Kế toán, Excel, Báo cáo", "12000000", 21, "ONSITE", "MIDDLE");
        ensureJobIfAbsent(client2, "Phát triển chatbot CSKH Facebook", "Lập trình & CNTT", "Tích hợp chatbot trả lời tự động, phân luồng câu hỏi và lưu lead về Google Sheet/CRM. Ưu tiên ứng viên có kinh nghiệm chatbot thực chiến.", "API, Chatbot, Automation", "11000000", 16, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client, "Chạy quảng cáo Meta cho mỹ phẩm", "Marketing & Bán hàng", "Thiết lập chiến dịch chuyển đổi cho 3 dòng sản phẩm, tối ưu chi phí/đơn hàng và báo cáo hằng tuần.", "Meta Ads, Pixel, Creative", "10000000", 14, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client3, "Biên dịch tài liệu Anh - Việt", "Viết lách & Nội dung", "Biên dịch 80 trang tài liệu đào tạo, đảm bảo thuật ngữ giáo dục thống nhất và trình bày sạch đẹp.", "Translation, Proofreading", "4200000", 9, "REMOTE", "JUNIOR");

        // Bổ sung dữ liệu để trang tìm dự án có mật độ tương tự marketplace thực tế.
        upsertShowcaseJob(vega, "Tuyển 3D Animator - Remote - Junior", "Thiết kế & Sáng tạo",
                "Vega Dynamics đang mở rộng team 3D và tìm 3D Animator Junior làm việc remote. Bạn sẽ tham gia các dự án cinematic, TVC và game trailer cùng đội ngũ Art Director nhiều kinh nghiệm.",
                "3D Animation, Blender, Character Animation, Storytelling", "15000000", 14, "REMOTE", "JUNIOR", 1);
        upsertShowcaseJob(frameStudio, "Freelance Video Editor phong cách BLog", "Thiết kế & Sáng tạo",
                "Tìm Video Editor chuyên dựng vlog YouTube, phong cách clean - cinematic. Khối lượng 4-6 video/tháng, mỗi video 10-15 phút; ưu tiên ứng viên có portfolio thực tế.",
                "Video Editing, Adobe Premiere Pro, After Effects, Color Grading", "12000000", 16, "REMOTE", "MIDDLE", 22);
        upsertShowcaseJob(littleBean, "Content Creator Part-time cho thương hiệu F&B", "Viết lách & Nội dung",
                "Little Bean Coffee tìm Content Creator part-time lên ý tưởng nội dung TikTok và Instagram, viết caption và tham gia quay video ngắn cho các chiến dịch theo tháng.",
                "Content Creator, Copywriting, Storytelling, Social Media", "7500000", 20, "REMOTE", "MIDDLE", 23);
        upsertShowcaseJob(talentMatchClient, "Frontend Developer React cho Dashboard SaaS", "Lập trình & CNTT",
                "Tham gia phát triển dashboard SaaS B2B phục vụ hơn 100 doanh nghiệp. Tech stack React, TypeScript, TailwindCSS; ưu tiên code sạch và khả năng phối hợp API tốt.",
                "React, TypeScript, TailwindCSS, REST API", "18000000", 28, "REMOTE", "SENIOR", 24);
        ensureJobIfAbsent(client2, "Backend Java Spring Boot tích hợp thanh toán", "Lập trình & CNTT", "Xây dựng REST API cho đơn hàng, thanh toán, webhook và lịch sử giao dịch. Cần kinh nghiệm Spring Security và MySQL.", "Java, Spring Boot, MySQL, REST API", "20000000", 28, "REMOTE", "SENIOR");
        ensureJobIfAbsent(client, "UI/UX Designer cho ứng dụng học ngoại ngữ", "Thiết kế & Sáng tạo", "Thiết kế flow onboarding, lesson, streak, leaderboard và prototype mobile. Bàn giao Figma component + design system.", "Figma, UI/UX, Prototype, Design System", "14000000", 18, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client, "Thiết kế Banner Performance Ads", "Thiết kế & Sáng tạo", "Thiết kế bộ 30 banner chạy quảng cáo Meta/Google, đa kích thước, bám key visual và ưu tiên khả năng chuyển đổi.", "Photoshop, Illustrator, Ads Creative", "5500000", 10, "REMOTE", "JUNIOR");
        ensureJobIfAbsent(client, "Google Ads Specialist cho dịch vụ B2B", "Marketing & Bán hàng", "Audit tài khoản quảng cáo, tái cấu trúc campaign, tối ưu conversion tracking và CPL theo tuần.", "Google Ads, GA4, GTM, B2B", "13000000", 25, "REMOTE", "SENIOR");
        ensureJobIfAbsent(client2, "SEO Content 30 bài chủ đề công nghệ", "Marketing & Bán hàng", "Nghiên cứu keyword cluster, outline và viết 30 bài chuẩn SEO cho website công nghệ. Cần đảm bảo internal link và search intent.", "SEO, Content, Keyword Research", "10500000", 30, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client3, "Online English Teacher Part-time", "Đào tạo & Giáo dục", "Dạy tiếng Anh giao tiếp online cho người lớn, 2-4 lớp/tuần, theo giáo trình có sẵn và báo cáo tiến độ học viên.", "English Teaching, Online Teaching, TESOL", "7000000", 20, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client3, "Gia sư Toán THPT online", "Đào tạo & Giáo dục", "Dạy kèm 1-1 học sinh lớp 10-12 qua Zoom, ôn kiến thức nền và luyện đề theo mục tiêu của học viên.", "Toán học, Zoom, Teaching", "4500000", 12, "REMOTE", "JUNIOR");
        ensureJobIfAbsent(client3, "Thiết kế slide bài giảng E-learning", "Đào tạo & Giáo dục", "Thiết kế 60 slide bài giảng trực quan, chuẩn hóa icon, layout và infographic để phục vụ quay khóa học online.", "PowerPoint, Canva, E-learning, Infographic", "6500000", 14, "REMOTE", "JUNIOR");
        ensureJobIfAbsent(client, "HR Freelancer hỗ trợ tuyển dụng 20 vị trí", "Hành chính & Nhân sự", "Đăng tuyển, lọc CV, sơ vấn và theo dõi pipeline ứng viên cho nhóm sales và chăm sóc khách hàng.", "Recruitment, Interview, Excel", "9000000", 20, "HYBRID", "MIDDLE");
        ensureJobIfAbsent(client, "Trợ lý online quản lý lịch và báo cáo", "Hành chính & Nhân sự", "Quản lý lịch họp, tổng hợp báo cáo tuần, theo dõi đầu việc và hỗ trợ chuẩn bị tài liệu cho quản lý.", "Google Workspace, Excel, Communication", "5000000", 30, "REMOTE", "JUNIOR");
        ensureJobIfAbsent(client2, "Rà soát điều khoản NDA và hợp đồng SaaS", "Luật & Pháp lý", "Rà soát NDA, điều khoản sử dụng và hợp đồng dịch vụ SaaS, đề xuất chỉnh sửa rủi ro và trách nhiệm pháp lý.", "NDA, Hợp đồng, SaaS, Tư vấn pháp lý", "7000000", 9, "REMOTE", "SENIOR");
        ensureJobIfAbsent(client, "Lập dự toán nội thất căn hộ 2 phòng ngủ", "Kỹ thuật & Kiến trúc", "Bóc tách khối lượng, lập dự toán vật tư và kiểm tra bản vẽ thi công nội thất cho căn hộ 85m2.", "AutoCAD, BOQ, Interior, Excel", "8000000", 11, "REMOTE", "MIDDLE");
        ensureJobIfAbsent(client2, "Dựng Revit mô hình kiến trúc văn phòng", "Kỹ thuật & Kiến trúc", "Dựng mô hình Revit từ bản CAD hiện trạng, tạo sheet cơ bản và kiểm tra clash các hạng mục kiến trúc.", "Revit, AutoCAD, BIM", "16000000", 22, "REMOTE", "SENIOR");
        ensureJobIfAbsent(client, "Kế toán thuế hỗ trợ quý IV", "Tài chính & Kế toán", "Kiểm tra chứng từ, đối soát hóa đơn, lập tờ khai và checklist thuế quý cho doanh nghiệp thương mại điện tử.", "Kế toán thuế, Excel, Hóa đơn", "7500000", 16, "HYBRID", "MIDDLE");
        ensureJobIfAbsent(client2, "Phân tích báo cáo tài chính và dòng tiền", "Tài chính & Kế toán", "Phân tích P&L, cashflow, biên lợi nhuận và xây dashboard tài chính đơn giản phục vụ ban điều hành.", "Finance, Excel, Dashboard, Cashflow", "12500000", 18, "REMOTE", "SENIOR");
        ensureJobIfAbsent(client, "Voice-over tiếng Việt cho video quảng cáo", "Khác", "Thu âm voice-over giọng trẻ trung cho 12 video quảng cáo ngắn, bàn giao file WAV sạch và 2 lựa chọn tone giọng.", "Voice Over, Recording, Vietnamese", "3000000", 7, "REMOTE", "JUNIOR");

        // Chuẩn hóa dữ liệu cũ và bổ sung bộ dữ liệu marketplace đủ 375 dự án mở.
        normalizeMarketplaceValues();
        seedReferenceJobs(client, client2, client3, frameStudio, littleBean, vega);

        // Tạo vài proposal mẫu để thống kê cạnh tranh và trang ứng tuyển có dữ liệu thật.
        Job seoJob = jobs.findAll().stream().filter(j -> j.getTitle().equalsIgnoreCase("Tối ưu SEO website dịch vụ")).findFirst().orElse(null);
        Job uiJob = jobs.findAll().stream().filter(j -> j.getTitle().equalsIgnoreCase("UI/UX Designer cho ứng dụng học ngoại ngữ")).findFirst().orElse(null);
        Job contentJob = jobs.findAll().stream().filter(j -> j.getTitle().equalsIgnoreCase("Content Creator Part-time cho thương hiệu F&B")).findFirst().orElse(null);
        if (seoJob != null) ensureApplication(seoJob, f4, "Tôi sẽ audit technical SEO, xây keyword map, content plan và dashboard theo dõi tăng trưởng organic theo từng tuần.", "8500000", 35, ApplicationStatus.SUBMITTED);
        if (uiJob != null) ensureApplication(uiJob, f1, "Tôi có kinh nghiệm thiết kế mobile app và design system. Tôi đề xuất làm flow, wireframe, UI kit và prototype để review theo sprint.", "13000000", 17, ApplicationStatus.SHORTLISTED);
        if (contentJob != null) ensureApplication(contentJob, f3, "Tôi sẽ xây content pillar, lịch đăng, caption và đề xuất concept quay chụp cho F&B theo đúng tone thương hiệu.", "5200000", 14, ApplicationStatus.SUBMITTED);

        if (projects.count() == 0) {
            Job devJob = jobs.findAll().stream().filter(j -> j.getTitle().contains("website thương mại điện tử")).findFirst().orElse(null);
            if (devJob != null) {
                JobApplication a = applications.findByJobAndFreelancer(devJob, f2).orElse(null);
                if (a == null) {
                    a = new JobApplication();
                    a.setJob(devJob);
                    a.setFreelancer(f2);
                    a.setProposal("Tôi đề xuất xây dựng hệ thống theo Spring Boot MVC, Spring Security và MySQL, chia rõ các module sản phẩm, giỏ hàng, đơn hàng và dashboard. Tôi sẽ bàn giao source code, SQL và tài liệu chạy trên IntelliJ.");
                    a.setProposedPrice(new BigDecimal("26000000"));
                    a.setEstimatedDays(32);
                    a.setPortfolioLink("https://github.com/");
                }
                a.setStatus(ApplicationStatus.ACCEPTED);
                a = applications.save(a);
                devJob.setStatus(JobStatus.CLOSED);
                jobs.save(devJob);

                Project p = new Project();
                p.setApplication(a);
                p.setCustomer(client2);
                p.setFreelancer(f2);
                p.setTotalPrice(a.getProposedPrice());
                p.setStatus(ProjectStatus.IN_PROGRESS);
                p = projects.save(p);
                p.setCreatedAt(LocalDateTime.now().minusDays(10));
                projects.save(p);

                Milestone m1 = new Milestone();
                m1.setProject(p);
                m1.setTitle("Giao diện & Database");
                m1.setDescription("Thiết kế database, giao diện chính và luồng sản phẩm.");
                m1.setAmount(new BigDecimal("10000000"));
                m1.setDeadline(LocalDate.now().plusDays(5));
                m1.setStatus(MilestoneStatus.COMPLETED);
                m1.setCompletedAt(LocalDateTime.now().minusDays(1));
                m1 = milestones.save(m1);

                Milestone m2 = new Milestone();
                m2.setProject(p);
                m2.setTitle("Backend & Checkout");
                m2.setDescription("Auth, giỏ hàng, checkout, đơn hàng và hoàn thiện.");
                m2.setAmount(new BigDecimal("16000000"));
                m2.setDeadline(LocalDate.now().plusDays(20));
                m2.setStatus(MilestoneStatus.PAID);
                milestones.save(m2);

                Payment pay1 = payment(p, m1, client2, "10000000", "VNPAY_DEMO", PaymentStatus.DISBURSED);
                pay1.setPaidAt(LocalDateTime.now().minusDays(8));
                pay1.setDisbursedAt(LocalDateTime.now().minusDays(1));
                payments.save(pay1);
                Payment pay2 = payment(p, m2, client2, "16000000", "MOMO_DEMO", PaymentStatus.PAID);
                pay2.setPaidAt(LocalDateTime.now().minusDays(2));
                payments.save(pay2);
            }
        }

        // Dữ liệu workflow riêng cho tài khoản freelancer@talentmatch.vn để demo ứng tuyển,
        // dự án, milestone, thanh toán và biểu đồ theo thời gian có số liệu thật.
        seedFreelancerDemoWorkflow(f1, List.of(client, client2, client3, vega, frameStudio, littleBean, talentMatchClient));

        // Chạy sau khi các project demo đã đóng job được chọn để tổng số OPEN cuối cùng đúng 375.
        seedMarketplaceScale(List.of(client, client2, client3, vega, frameStudio, littleBean, talentMatchClient));
        // Bổ sung chỉ báo có dữ liệu cho giao diện V28; lặp lại không sinh thêm tin.
        seedJobBoardAttributes();
    }

    private void seedFreelancerDemoWorkflow(User freelancer, List<User> customers) {
        User nova = customers.get(0);
        User tech = customers.get(1);
        User edu = customers.get(2);
        User vega = customers.get(3);
        User frame = customers.get(4);
        User bean = customers.get(5);

        Job submitted = workflowJob(nova, "Redesign checkout cho website thương mại điện tử", "Thiết kế & Sáng tạo",
                "Tối ưu checkout desktop/mobile, giảm bước nhập liệu và bàn giao prototype có thể test người dùng.",
                "Figma, UI/UX, Prototype, E-commerce", "15000000", 18, "REMOTE", "MIDDLE", 1);
        ensureWorkflowApplication(submitted, freelancer, ApplicationStatus.SUBMITTED, "13500000", 14, 1,
                "Tôi sẽ audit luồng checkout hiện tại, xác định điểm rơi conversion và bàn giao wireframe, UI hoàn chỉnh cùng prototype để test.");

        Job shortlisted = workflowJob(tech, "UI/UX cho ứng dụng quản lý vận chuyển Logistics", "Thiết kế & Sáng tạo",
                "Thiết kế dashboard điều phối, tracking đơn hàng và app tài xế. Ưu tiên design system rõ ràng.",
                "Figma, Dashboard, Mobile UI, Design System", "22000000", 24, "HYBRID", "MIDDLE", 3);
        ensureWorkflowApplication(shortlisted, freelancer, ApplicationStatus.SHORTLISTED, "19500000", 20, 3,
                "Tôi đề xuất bắt đầu bằng user flow cho dispatcher và tài xế, sau đó xây UI kit và prototype theo từng sprint để đội vận hành review sớm.");

        Job rejected = workflowJob(bean, "Thiết kế nhận diện cho chuỗi cà phê trẻ", "Thiết kế & Sáng tạo",
                "Xây key visual, menu board, social template và guideline sử dụng cơ bản cho chiến dịch khai trương.",
                "Branding, Figma, Illustrator, Social Design", "12000000", 16, "REMOTE", "MIDDLE", 8);
        ensureWorkflowApplication(rejected, freelancer, ApplicationStatus.REJECTED, "11000000", 15, 8,
                "Tôi sẽ phát triển hai hướng moodboard, chốt hệ màu và typography trước khi triển khai key visual và bộ social template đồng nhất.");

        Job withdrawn = workflowJob(frame, "Thiết kế landing page tuyển dụng ngành Media", "Thiết kế & Sáng tạo",
                "Thiết kế landing page tuyển dụng hiện đại, responsive và có prototype cho desktop/mobile.",
                "Figma, Landing Page, Responsive, UI Design", "8000000", 10, "REMOTE", "JUNIOR", 16);
        ensureWorkflowApplication(withdrawn, freelancer, ApplicationStatus.WITHDRAWN, "7500000", 9, 16,
                "Tôi có thể hoàn thiện layout desktop/mobile trong 9 ngày, kèm component tái sử dụng và prototype cho luồng nộp hồ sơ.");

        Job p1Job = workflowJob(nova, "Thiết kế Dashboard quản trị SaaS B2B", "Thiết kế & Sáng tạo",
                "Thiết kế dashboard quản trị nhiều vai trò, biểu đồ KPI và hệ thống component dùng lại.",
                "Figma, Dashboard, UX Research, Design System", "24000000", 28, "REMOTE", "MIDDLE", 6);
        JobApplication p1App = ensureWorkflowApplication(p1Job, freelancer, ApplicationStatus.ACCEPTED, "22000000", 24, 6,
                "Tôi sẽ chuẩn hóa information architecture, xây component library và prototype dashboard theo luồng nghiệp vụ trước khi handoff.");
        Project p1 = ensureWorkflowProject(p1App, nova, freelancer, ProjectStatus.IN_PROGRESS, 4, null);
        Milestone p1m1 = ensureWorkflowMilestone(p1, "UX Flow & Wireframe", "Hoàn thiện IA, user flow và wireframe các màn hình chính.", "7000000", -1, MilestoneStatus.COMPLETED, 3);
        Milestone p1m2 = ensureWorkflowMilestone(p1, "UI Dashboard & Design System", "Thiết kế high-fidelity và bộ component chính.", "9000000", 8, MilestoneStatus.IN_PROGRESS, 2);
        Milestone p1m3 = ensureWorkflowMilestone(p1, "Prototype & Handoff", "Prototype, review và handoff cho đội frontend.", "6000000", 16, MilestoneStatus.PAID, 1);
        ensureWorkflowPayment(p1, p1m1, nova, "7000000", PaymentStatus.DISBURSED, "TM-DEMO-F1-P1-M1", 3, 2);
        ensureWorkflowPayment(p1, p1m2, nova, "9000000", PaymentStatus.PAID, "TM-DEMO-F1-P1-M2", 2, null);
        ensureWorkflowPayment(p1, p1m3, nova, "6000000", PaymentStatus.PAID, "TM-DEMO-F1-P1-M3", 1, null);

        Job p2Job = workflowJob(tech, "Thiết kế UI Mobile Banking cho Fintech", "Thiết kế & Sáng tạo",
                "Thiết kế luồng chuyển tiền, quản lý thẻ và thống kê chi tiêu cho ứng dụng mobile.",
                "Mobile UI, Fintech, Figma, Prototype", "18000000", 20, "REMOTE", "MIDDLE", 14);
        JobApplication p2App = ensureWorkflowApplication(p2Job, freelancer, ApplicationStatus.ACCEPTED, "17000000", 18, 14,
                "Tôi sẽ ưu tiên tính rõ ràng của luồng tài chính, trạng thái giao dịch và khả năng sử dụng một tay trên mobile.");
        Project p2 = ensureWorkflowProject(p2App, tech, freelancer, ProjectStatus.WAITING_ACCEPTANCE, 11, null);
        Milestone p2m1 = ensureWorkflowMilestone(p2, "Mobile UI & Prototype", "Bàn giao 24 màn hình chính và prototype tương tác.", "17000000", 1, MilestoneStatus.SUBMITTED, 10);
        ensureWorkflowPayment(p2, p2m1, tech, "17000000", PaymentStatus.PAID, "TM-DEMO-F1-P2", 10, null);

        Job p3Job = workflowJob(vega, "Xây dựng Design System cho nền tảng 3D", "Thiết kế & Sáng tạo",
                "Chuẩn hóa token, component, trạng thái và guideline handoff cho sản phẩm quản lý asset 3D.",
                "Design System, Figma, UI Kit, Documentation", "16000000", 18, "REMOTE", "SENIOR", 22);
        JobApplication p3App = ensureWorkflowApplication(p3Job, freelancer, ApplicationStatus.ACCEPTED, "15500000", 16, 22,
                "Tôi sẽ audit component hiện có, xây token theo semantic layer và tài liệu usage để đội product/frontend sử dụng thống nhất.");
        Project p3 = ensureWorkflowProject(p3App, vega, freelancer, ProjectStatus.COMPLETED, 18, 5);
        Milestone p3m1 = ensureWorkflowMilestone(p3, "Design System V1", "Token, component và guideline sử dụng.", "15500000", -6, MilestoneStatus.COMPLETED, 17);
        ensureWorkflowPayment(p3, p3m1, vega, "15500000", PaymentStatus.DISBURSED, "TM-DEMO-F1-P3", 16, 6);

        Job p4Job = workflowJob(frame, "Landing page chiến dịch ra mắt Studio", "Marketing & Bán hàng",
                "Thiết kế landing page tập trung chuyển đổi, visual mạnh và tối ưu cho chiến dịch paid media.",
                "Landing Page, Conversion, Figma, Ads Creative", "10500000", 14, "REMOTE", "MIDDLE", 40);
        JobApplication p4App = ensureWorkflowApplication(p4Job, freelancer, ApplicationStatus.ACCEPTED, "9800000", 12, 40,
                "Tôi sẽ kết hợp CRO với visual hierarchy rõ ràng, bàn giao desktop/mobile và guideline cho các section chạy A/B test.");
        Project p4 = ensureWorkflowProject(p4App, frame, freelancer, ProjectStatus.COMPLETED, 34, 27);
        ensureWorkflowPayment(p4, null, frame, "9800000", PaymentStatus.DISBURSED, "TM-DEMO-F1-P4", 30, 27);

        Job p5Job = workflowJob(edu, "Prototype ứng dụng học từ vựng E-learning", "Đào tạo & Giáo dục",
                "Thiết kế prototype học từ vựng có streak, flashcard, quiz và theo dõi tiến độ.",
                "E-learning, Mobile UX, Gamification, Figma", "9000000", 15, "REMOTE", "MIDDLE", 62);
        JobApplication p5App = ensureWorkflowApplication(p5Job, freelancer, ApplicationStatus.ACCEPTED, "8500000", 14, 62,
                "Tôi tập trung vào retention loop, streak và feedback sau quiz để tăng động lực học, kèm prototype test nhanh với người dùng.");
        Project p5 = ensureWorkflowProject(p5App, edu, freelancer, ProjectStatus.COMPLETED, 56, 43);
        ensureWorkflowPayment(p5, null, edu, "8500000", PaymentStatus.DISBURSED, "TM-DEMO-F1-P5", 50, 43);

        Job p6Job = workflowJob(bean, "Website portfolio thương hiệu F&B cao cấp", "Lập trình & CNTT",
                "Thiết kế UI trước khi triển khai website giới thiệu thương hiệu, menu và hệ thống booking cơ bản.",
                "UI/UX, Web Design, Figma, Responsive", "13000000", 22, "HYBRID", "MIDDLE", 2);
        JobApplication p6App = ensureWorkflowApplication(p6Job, freelancer, ApplicationStatus.ACCEPTED, "12000000", 20, 2,
                "Tôi sẽ hoàn thiện visual direction, responsive layout và prototype booking trước khi đội phát triển bắt đầu code.");
        ensureWorkflowProject(p6App, bean, freelancer, ProjectStatus.WAITING_PAYMENT, 1, null);

        // Một vài công việc quan tâm để demo đúng chức năng lưu/tìm lại cơ hội.
        jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN).stream()
                .filter(j -> j.getRequiredSkills() != null && (j.getRequiredSkills().contains("Figma") || j.getRequiredSkills().contains("UI")))
                .limit(3)
                .forEach(j -> ensureSavedJob(freelancer, j));

        freelancer.setCompletedProjects(Math.max(freelancer.getCompletedProjects() == null ? 0 : freelancer.getCompletedProjects(), 3));
        users.save(freelancer);
    }

    private Job workflowJob(User customer, String title, String category, String description, String skills,
                            String budget, int deadlineDays, String workType, String level, int createdDaysAgo) {
        Job j = jobs.findAll().stream().filter(x -> title.equalsIgnoreCase(x.getTitle())).findFirst()
                .orElseGet(() -> jobs.save(job(customer, title, category, description, skills, budget, deadlineDays, workType, level)));
        j.setCustomer(customer);
        j.setTitle(title);
        j.setCategory(category);
        j.setDescription(description);
        j.setRequiredSkills(skills);
        j.setBudget(new BigDecimal(budget));
        j.setPriceType("FIXED");
        j.setDeadline(LocalDate.now().plusDays(deadlineDays));
        j.setWorkType(workType);
        j.setExperienceLevel(level);
        j.setStatus(JobStatus.OPEN);
        j.setCreatedAt(LocalDateTime.now().minusDays(createdDaysAgo));
        return jobs.save(j);
    }

    private JobApplication ensureWorkflowApplication(Job job, User freelancer, ApplicationStatus status,
                                                       String price, int days, int createdDaysAgo, String proposal) {
        JobApplication a = applications.findByJobAndFreelancer(job, freelancer).orElseGet(JobApplication::new);
        a.setJob(job);
        a.setFreelancer(freelancer);
        a.setProposal(proposal);
        a.setProposedPrice(new BigDecimal(price));
        a.setEstimatedDays(days);
        a.setPortfolioLink("https://portfolio.example/uiux");
        a.setStatus(status);
        a = applications.save(a);
        a.setCreatedAt(LocalDateTime.now().minusDays(createdDaysAgo));
        a.setUpdatedAt(LocalDateTime.now().minusDays(Math.max(0, createdDaysAgo - 1)));
        a = applications.save(a);
        if (status == ApplicationStatus.ACCEPTED) {
            job.setStatus(JobStatus.CLOSED);
            jobs.save(job);
        }
        return a;
    }

    private Project ensureWorkflowProject(JobApplication app, User customer, User freelancer,
                                          ProjectStatus status, int createdDaysAgo, Integer completedDaysAgo) {
        Project p = projects.findByApplication(app).orElseGet(Project::new);
        p.setApplication(app);
        p.setCustomer(customer);
        p.setFreelancer(freelancer);
        p.setTotalPrice(app.getProposedPrice());
        p.setStatus(status);
        p = projects.save(p);
        p.setCreatedAt(LocalDateTime.now().minusDays(createdDaysAgo));
        if (completedDaysAgo != null) p.setCompletedAt(LocalDateTime.now().minusDays(completedDaysAgo));
        return projects.save(p);
    }

    private Milestone ensureWorkflowMilestone(Project project, String title, String description, String amount,
                                              int deadlineOffsetDays, MilestoneStatus status, int createdDaysAgo) {
        Milestone m = milestones.findByProjectOrderByCreatedAtAsc(project).stream()
                .filter(x -> title.equalsIgnoreCase(x.getTitle())).findFirst().orElseGet(Milestone::new);
        m.setProject(project);
        m.setTitle(title);
        m.setDescription(description);
        m.setAmount(new BigDecimal(amount));
        m.setDeadline(LocalDate.now().plusDays(deadlineOffsetDays));
        m.setStatus(status);
        if (status == MilestoneStatus.COMPLETED) m.setCompletedAt(LocalDateTime.now().minusDays(Math.max(1, createdDaysAgo - 1)));
        m = milestones.save(m);
        m.setCreatedAt(LocalDateTime.now().minusDays(createdDaysAgo));
        return milestones.save(m);
    }

    private void ensureWorkflowPayment(Project project, Milestone milestone, User payer, String amount, PaymentStatus status,
                                       String code, int paidDaysAgo, Integer disbursedDaysAgo) {
        Payment pay = payments.findAll().stream().filter(x -> code.equals(x.getTransactionCode())).findFirst().orElseGet(Payment::new);
        pay.setProject(project);
        pay.setMilestone(milestone);
        pay.setPayer(payer);
        pay.setAmount(new BigDecimal(amount));
        pay.setMethod("VNPAY_DEMO");
        pay.setStatus(status);
        pay.setTransactionCode(code);
        pay.setPaidAt(LocalDateTime.now().minusDays(paidDaysAgo));
        pay.setDisbursedAt(disbursedDaysAgo == null ? null : LocalDateTime.now().minusDays(disbursedDaysAgo));
        pay = payments.save(pay);
        pay.setCreatedAt(LocalDateTime.now().minusDays(paidDaysAgo));
        payments.save(pay);
    }

    private void ensureSavedJob(User freelancer, Job job) {
        if (savedJobs.findByFreelancerAndJob(freelancer, job).isPresent()) return;
        SavedJob saved = new SavedJob();
        saved.setFreelancer(freelancer);
        saved.setJob(job);
        savedJobs.save(saved);
    }

    private void normalizeMarketplaceValues() {
        for (Job j : jobs.findAll()) {
            boolean changed = false;
            if ("Marketing".equalsIgnoreCase(j.getCategory())) {
                j.setCategory("Marketing & Bán hàng");
                changed = true;
            }
            if ("Viết lách & Dịch thuật".equalsIgnoreCase(j.getCategory())) {
                j.setCategory("Viết lách & Nội dung");
                changed = true;
            }
            if ("INTERMEDIATE".equalsIgnoreCase(j.getExperienceLevel())) {
                j.setExperienceLevel("MIDDLE");
                changed = true;
            }
            if ("EXPERT".equalsIgnoreCase(j.getExperienceLevel())) {
                j.setExperienceLevel("SENIOR");
                changed = true;
            }
            if (changed) jobs.save(j);
        }
    }

    private void seedReferenceJobs(User client, User client2, User client3, User frameStudio,
                                   User littleBean, User vega) {
        upsertShowcaseJob(client, "Tuyển bảo vệ biết tiếng Anh khu Hoàng Mai, ca 12 tiếng",
                "Khác",
                "Bảo vệ biết tiếng Anh khu Hoàng Mai, ca 12 tiếng (7h–19h). Ưu tiên có kinh nghiệm, giao tiếp tốt và có thể nhận việc ngay.",
                "Bảo vệ, Tiếng Anh giao tiếp, An ninh, Trực ca", "16000000", 12, "ONSITE", "JUNIOR", 1);
        upsertShowcaseJob(client2, "U.S Pickleball Club tuyển Kế toán trưởng tại Hà Nội",
                "Tài chính & Kế toán",
                "U.S Pickleball Club tuyển Kế toán trưởng phụ trách hệ thống sổ sách, báo cáo quản trị, thuế và phối hợp kiểm soát dòng tiền.",
                "Kế toán trưởng, Kế toán tài chính, Báo cáo tài chính, Thuế", "22000000", 11, "ONSITE", "SENIOR", 2);
        upsertShowcaseJob(client2, "Cần tìm Kế toán Tổng hợp tại Cầu Giấy, Hà Nội",
                "Tài chính & Kế toán",
                "Cần Kế toán tổng hợp 3–5 năm kinh nghiệm, phụ trách hạch toán, công nợ, thuế và báo cáo nội bộ theo tháng.",
                "Kế toán tổng hợp, Hạch toán kế toán, Nghiệp vụ thuế, Báo cáo nội bộ", "18000000", 15, "HYBRID", "MIDDLE", 3);
        upsertShowcaseJob(frameStudio, "MUNBEAUTY TUYỂN DỤNG FREELANCE VIDEO EDITOR – PHONG CÁCH BLOG",
                "Thiết kế & Sáng tạo",
                "Munbeauty đang tìm Video Editor đồng hành cùng team để phát triển nội dung video theo phong cách blog, đời sống và short-form trên Social/TikTok.",
                "Dựng video, Biên tập video, CapCut, Adobe Premiere Pro", "8500000", 14, "REMOTE", "MIDDLE", 4);
        upsertShowcaseJob(vega, "Tuyển 3D Animator - Remote - Junior",
                "Thiết kế & Sáng tạo",
                "Future Studio tìm 3D Animator làm việc từ xa cho dự án phim hoạt hình 3D sắp tới. Công việc gồm diễn hoạt nhân vật, camera và cảnh quay theo storyboard.",
                "3D Animation, Blender, Character Animation, Storytelling", "14000000", 14, "REMOTE", "JUNIOR", 5);
        upsertShowcaseJob(littleBean, "Tuyển dụng Content Creator Part-time / Freelance tại Kebin Coffee in Bed",
                "Viết lách & Nội dung",
                "Kebin Coffee in Bed tìm Content Creator part-time/freelance lên ý tưởng nội dung, viết caption và storytelling cho các kênh social.",
                "Sáng tạo nội dung, Content Creator, Viết caption, Storytelling", "5000000", 15, "HYBRID", "FRESHER", 6);

        upsertShowcaseJob(client3, "Tuyển NV Chăm sóc Khách hàng Full-time (Song ngữ Nhật + Anh)",
                "Hành chính & Nhân sự",
                "Tuyển nhân viên Chăm sóc Khách hàng Full-time làm việc tại nhà theo ca. Ưu tiên ứng viên giao tiếp tốt bằng tiếng Nhật và tiếng Anh.",
                "Chăm sóc khách hàng, Tiếng Nhật N2, Tiếng Anh giao tiếp, Giao tiếp", "4200000", 6, "REMOTE", "FRESHER", 7);
        upsertShowcaseJob(client, "Tuyển dụng HR Coordinator và Accountant Support Coordinator Remote",
                "Hành chính & Nhân sự",
                "Tuyển HR Coordinator và Accountant Support Coordinator làm việc remote, hỗ trợ tiếp nhận CV, lịch phỏng vấn, hồ sơ và công việc hành chính cơ bản.",
                "Tuyển dụng, HR Operations, Quản lý hồ sơ ứng viên, Kế toán cơ bản", "4800000", 7, "REMOTE", "JUNIOR", 8);
        upsertShowcaseJob(client2, "Tuyển CTV Tuyển dụng / HR Freelancer Online",
                "Hành chính & Nhân sự",
                "Tuyển cộng tác viên tuyển dụng online 100%, chủ động thời gian. Công việc gồm đăng tin, tìm kiếm, lọc hồ sơ và hỗ trợ liên hệ ứng viên.",
                "Tuyển dụng, HR, Đăng tin tuyển dụng, Tìm kiếm ứng viên", "3500000", 9, "REMOTE", "INTERN", 9);
    }

    private void seedMarketplaceScale(List<User> customers) {
        java.util.LinkedHashMap<String, int[]> targets = new java.util.LinkedHashMap<>();
        targets.put("Hành chính & Nhân sự", new int[]{21, 5, 1});
        targets.put("Khác", new int[]{20, 8, 2});
        targets.put("Kỹ thuật & Kiến trúc", new int[]{18, 13, 5});
        targets.put("Luật & Pháp lý", new int[]{17, 8, 3});
        targets.put("Lập trình & CNTT", new int[]{34, 25, 13});
        targets.put("Marketing & Bán hàng", new int[]{30, 13, 5});
        targets.put("Thiết kế & Sáng tạo", new int[]{30, 16, 6});
        targets.put("Tài chính & Kế toán", new int[]{25, 11, 4});
        targets.put("Viết lách & Nội dung", new int[]{15, 7, 2});
        targets.put("Đào tạo & Giáo dục", new int[]{11, 4, 3});

        int globalSequence = 0;
        for (var entry : targets.entrySet()) {
            String category = entry.getKey();
            int[] target = entry.getValue();
            List<Job> current = jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN).stream()
                    .filter(j -> category.equalsIgnoreCase(j.getCategory()))
                    .toList();
            int[] currentBuckets = budgetBuckets(current);
            int[] missingBuckets = new int[]{
                    Math.max(0, target[0] - currentBuckets[0]),
                    Math.max(0, target[1] - currentBuckets[1]),
                    Math.max(0, target[2] - currentBuckets[2])
            };
            int targetTotal = target[0] + target[1] + target[2];
            int missingTotal = Math.max(0, targetTotal - current.size());

            int created = 0;
            for (int bucket = 0; bucket < 3 && created < missingTotal; bucket++) {
                int amount = Math.min(missingBuckets[bucket], missingTotal - created);
                for (int i = 0; i < amount; i++) {
                    globalSequence++;
                    Job j = demoMarketplaceJob(customers.get(globalSequence % customers.size()), category,
                            current.size() + created + 1, bucket, globalSequence);
                    j = jobs.save(j);
                    j.setCreatedAt(LocalDateTime.now().minusHours(30L + globalSequence * 2L));
                    jobs.save(j);
                    created++;
                }
            }
            while (created < missingTotal) {
                globalSequence++;
                Job j = demoMarketplaceJob(customers.get(globalSequence % customers.size()), category,
                        current.size() + created + 1, 1, globalSequence);
                j = jobs.save(j);
                j.setCreatedAt(LocalDateTime.now().minusHours(30L + globalSequence * 2L));
                jobs.save(j);
                created++;
            }
        }
    }

    private void seedJobBoardAttributes() {
        // Metadata minh họa. verified=true chỉ áp dụng dữ liệu DEMO; hệ thống thật cần quy trình Admin duyệt.
        int index = 0;
        for (Job j : jobs.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN)) {
            index++;
            if (j.getEstimatedDurationDays() == null || j.getEstimatedDurationDays() <= 0)
                j.setEstimatedDurationDays(7 + (index % 32));
            // Cập nhật dự án demo theo tiêu chí xác định để có dữ liệu lọc,
            // không khẳng định các doanh nghiệp thực tế đã được xác minh.
            if (j.getTitle() != null &&
                (j.getTitle().startsWith("Tuyển HR / Recruitment Freelancer cho chiến dịch") ||
                 j.getTitle().startsWith("Dựng bản vẽ CAD / Revit cho dự án") ||
                 j.getTitle().startsWith("Rà soát hợp đồng và tư vấn pháp lý doanh nghiệp") ||
                 j.getTitle().startsWith("Thiết kế Banner Performance Ads") ||
                 j.getTitle().startsWith("Thiết kế landing page tuyển dụng") ||
                 j.getTitle().startsWith("UI/UX Designer cho ứng dụng học ngoại ngữ") ||
                 j.getTitle().startsWith("Tuyển 3D Animator"))) {
                j.setFeatured(index % 4 == 0 || j.getTitle().startsWith("Thiết kế Banner"));
                j.setVerified(index % 3 == 0 || j.getTitle().startsWith("Thiết kế landing"));
            }
            jobs.save(j);
        }
    }

    private int[] budgetBuckets(List<Job> list) {
        int[] counts = new int[3];
        BigDecimal five = new BigDecimal("5000000");
        BigDecimal fifteen = new BigDecimal("15000000");
        for (Job j : list) {
            if (j.getBudget() == null) continue;
            if (j.getBudget().compareTo(five) < 0) counts[0]++;
            else if (j.getBudget().compareTo(fifteen) <= 0) counts[1]++;
            else counts[2]++;
        }
        return counts;
    }

    private Job demoMarketplaceJob(User customer, String category, int index, int budgetBucket, int sequence) {
        String[] workTypes = {"REMOTE", "HYBRID", "ONSITE"};
        String[] levels = {"INTERN", "FRESHER", "JUNIOR", "MIDDLE", "SENIOR"};
        String title;
        String skills;
        String description;
        switch (category) {
            case "Hành chính & Nhân sự" -> {
                title = "Tuyển HR / Recruitment Freelancer cho chiến dịch " + String.format("%02d", index);
                skills = "Tuyển dụng, HR, Excel, Giao tiếp";
                description = "Hỗ trợ đăng tin, tìm kiếm ứng viên, lọc CV và theo dõi lịch phỏng vấn. Quy trình rõ ràng, có biểu mẫu và người phụ trách phối hợp.";
            }
            case "Kỹ thuật & Kiến trúc" -> {
                title = "Dựng bản vẽ CAD / Revit cho dự án kiến trúc " + String.format("%02d", index);
                skills = "AutoCAD, Revit, SketchUp, Kiến trúc";
                description = "Triển khai bản vẽ kỹ thuật, mô hình 3D và hồ sơ cơ bản theo brief. Cần bàn giao file nguồn và cập nhật tiến độ theo mốc.";
            }
            case "Luật & Pháp lý" -> {
                title = "Rà soát hợp đồng và tư vấn pháp lý doanh nghiệp " + String.format("%02d", index);
                skills = "Hợp đồng, Pháp lý, Tư vấn, Doanh nghiệp";
                description = "Rà soát điều khoản hợp đồng dịch vụ, bảo mật, thanh toán và trách nhiệm hai bên; đề xuất nội dung chỉnh sửa dễ áp dụng.";
            }
            case "Lập trình & CNTT" -> {
                title = "Phát triển tính năng Web / API cho sản phẩm SaaS " + String.format("%02d", index);
                skills = "Java, Spring Boot, React, MySQL";
                description = "Phát triển module web, REST API, giao diện responsive và tích hợp dữ liệu. Ưu tiên code rõ ràng, có README và bàn giao source đầy đủ.";
            }
            case "Marketing & Bán hàng" -> {
                title = "Freelancer Digital Marketing tối ưu chiến dịch " + String.format("%02d", index);
                skills = "Meta Ads, Google Ads, GA4, Content";
                description = "Thiết lập và tối ưu chiến dịch marketing theo KPI, theo dõi dữ liệu, đề xuất nội dung và báo cáo hiệu quả định kỳ.";
            }
            case "Thiết kế & Sáng tạo" -> {
                title = "Thiết kế Social / UI / Branding cho chiến dịch " + String.format("%02d", index);
                skills = "Figma, Photoshop, Illustrator, UI/UX";
                description = "Thiết kế key visual và các hạng mục số theo guideline, bảo đảm đồng bộ hình ảnh và bàn giao file nguồn có tổ chức.";
            }
            case "Tài chính & Kế toán" -> {
                title = "Kế toán Freelancer hỗ trợ báo cáo và đối soát " + String.format("%02d", index);
                skills = "Kế toán, Excel, Thuế, Báo cáo tài chính";
                description = "Hỗ trợ hạch toán, đối soát công nợ, kiểm tra chứng từ và lập báo cáo quản trị theo mẫu của doanh nghiệp.";
            }
            case "Viết lách & Nội dung" -> {
                title = "Content Writer / Copywriter cho thương hiệu " + String.format("%02d", index);
                skills = "Content, Copywriting, SEO, Social Media";
                description = "Lên ý tưởng nội dung, viết caption và bài SEO theo tone thương hiệu, có outline rõ ràng và tối đa hai vòng chỉnh sửa.";
            }
            case "Đào tạo & Giáo dục" -> {
                title = "Gia sư / Giáo viên online theo dự án " + String.format("%02d", index);
                skills = "Teaching, Zoom, Communication, E-learning";
                description = "Dạy online theo lịch linh hoạt, chuẩn bị nội dung bài học và theo dõi tiến độ học viên theo biểu mẫu được cung cấp.";
            }
            default -> {
                title = "Cộng tác viên freelance linh hoạt theo dự án " + String.format("%02d", index);
                skills = "Giao tiếp, Google Workspace, Hỗ trợ dự án, Online";
                description = "Hỗ trợ các đầu việc theo checklist, cập nhật tiến độ đúng hạn và phối hợp online với người phụ trách dự án.";
            }
        }

        BigDecimal budget;
        if (budgetBucket == 0) budget = new BigDecimal(2500000 + (sequence % 5) * 500000L);
        else if (budgetBucket == 1) budget = new BigDecimal(6000000 + (sequence % 9) * 1000000L);
        else budget = new BigDecimal(16000000 + (sequence % 9) * 1500000L);

        Job j = job(customer, title, category, description, skills, budget.toPlainString(),
                7 + (sequence % 30), workTypes[sequence % workTypes.length], levels[sequence % levels.length]);
        j.setPriceType("FIXED");
        j.setEscrowFunded(sequence % 7 == 0);
        j.setEstimatedDurationDays(5 + sequence % 42);
        j.setFeatured(sequence % 6 == 0);
        j.setVerified(sequence % 4 == 0);
        return j;
    }

    private void upsertShowcaseJob(User customer, String title, String category, String description, String skills,
                                   String budget, int days, String workType, String exp, int hoursAgo) {
        Job j = jobs.findAll().stream()
                .filter(x -> title.equalsIgnoreCase(x.getTitle()))
                .findFirst()
                .orElseGet(() -> jobs.save(job(customer, title, category, description, skills, budget, days, workType, exp)));
        j.setCustomer(customer);
        j.setTitle(title);
        j.setCategory(category);
        j.setDescription(description);
        j.setRequiredSkills(skills);
        j.setBudget(new BigDecimal(budget));
        j.setPriceType("FIXED");
        j.setDeadline(LocalDate.now().plusDays(days));
        j.setWorkType(workType);
        j.setExperienceLevel(exp);
        j.setStatus(JobStatus.OPEN);
        j.setEscrowFunded(hoursAgo % 3 == 0);
        j.setEstimatedDurationDays(7 + hoursAgo % 25);
        j.setFeatured(hoursAgo % 4 == 0);
        j.setVerified(hoursAgo % 3 == 0);
        j.setCreatedAt(LocalDateTime.now().minusHours(hoursAgo));
        jobs.save(j);
    }

    private void ensureJobIfAbsent(User customer, String title, String category, String description, String skills, String budget, int days, String workType, String exp) {
        boolean exists = jobs.findAll().stream().anyMatch(j -> title.equalsIgnoreCase(j.getTitle()));
        if (!exists) jobs.save(job(customer, title, category, description, skills, budget, days, workType, exp));
    }

    private User ensure(String name, String email, Role role, String pass) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setFullName(name);
            u.setEmail(email);
            u.setPassword(encoder.encode(pass));
            u.setRole(role);
            u.setEnabled(true);
            u.setLocked(false);
            return users.save(u);
        });
    }

    private User freelancer(String name, String email, String title, String field, String skills, int years, String rate, String location, double rating, String bio) {
        User u = ensure(name, email, Role.FREELANCER, "123456");
        u.setProfessionalTitle(title);
        u.setField(field);
        u.setSkills(skills);
        u.setExperienceYears(years);
        u.setHourlyRate(new BigDecimal(rate));
        u.setLocation(location);
        u.setRating(rating);
        u.setAvailable(true);
        u.setBio(bio);
        u.setWebsite("https://portfolio.example/" + u.getId());
        return u;
    }

    private Job job(User customer, String title, String category, String description, String skills, String budget, int days, String workType, String exp) {
        Job j = new Job();
        j.setCustomer(customer);
        j.setTitle(title);
        j.setCategory(category);
        j.setDescription(description);
        j.setRequiredSkills(skills);
        j.setBudget(new BigDecimal(budget));
        j.setDeadline(LocalDate.now().plusDays(days));
        j.setWorkType(workType);
        j.setExperienceLevel(exp);
        j.setAcceptanceCriteria("Đúng phạm vi, đúng deadline, bàn giao đầy đủ file/source và tài liệu cần thiết.");
        j.setEscrowFunded(false);
        j.setEstimatedDurationDays(Math.max(3, days));
        j.setStatus(JobStatus.OPEN);
        return j;
    }

    private void ensureApplication(Job job, User freelancer, String proposal, String price, int days, ApplicationStatus status) {
        if (applications.findByJobAndFreelancer(job, freelancer).isPresent()) return;
        JobApplication a = new JobApplication();
        a.setJob(job);
        a.setFreelancer(freelancer);
        a.setProposal(proposal);
        a.setProposedPrice(new BigDecimal(price));
        a.setEstimatedDays(days);
        a.setStatus(status);
        applications.save(a);
    }

    private Payment payment(Project p, Milestone m, User payer, String amount, String method, PaymentStatus status) {
        Payment x = new Payment();
        x.setProject(p);
        x.setMilestone(m);
        x.setPayer(payer);
        x.setAmount(new BigDecimal(amount));
        x.setMethod(method);
        x.setStatus(status);
        x.setTransactionCode("SEED-" + System.nanoTime());
        return x;
    }
}
