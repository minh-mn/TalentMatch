package com.talentmatch.config;

import com.talentmatch.model.TalentReview;
import com.talentmatch.repository.TalentReviewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Opt-in, repeat-safe sentiment fixture refresh. Never touches real review content. */
@Component
@Order(100)
public class SentimentDemoDataInitializer implements CommandLineRunner {
    private final TalentReviewRepository reviews;
    private final boolean enabled;
    public SentimentDemoDataInitializer(TalentReviewRepository reviews,
        @Value("${app.demo.sentiment.seed:false}") boolean enabled) {
        this.reviews=reviews; this.enabled=enabled;
    }
    private static final String[] COMMENTS = {
        "Hoàn thành công việc rất tốt, giao đúng hạn và trao đổi chuyên nghiệp.",
        "Sản phẩm tuyệt vời, chất lượng xuất sắc và hỗ trợ tốt trong suốt dự án.",
        "Làm việc nhanh, chủ động và rất uy tín. Tôi hài lòng với kết quả.",
        "Bản thiết kế đẹp, dễ sử dụng, đúng hạn. Cảm ơn vì hỗ trợ chu đáo.",
        "Giải pháp hiệu quả, hệ thống ổn định, quá trình nghiệm thu rất tốt.",
        "Giao sản phẩm trễ hạn, nhiều lỗi và chất lượng kém.",
        "Tôi rất thất vọng, tiến độ chậm và phản hồi thiếu chuyên nghiệp.",
        "Sản phẩm bị lỗi, khó sử dụng, tôi không hài lòng với kết quả.",
        "Hỗ trợ quá tệ và không đúng yêu cầu đã thống nhất.",
        "Nội dung thiếu nhiều phần quan trọng, giao trễ hạn và hiệu quả kém.",
        "Đã nhận tài liệu thiết kế và nghiệm thu vào ngày đã thống nhất.",
        "Buổi họp dự án diễn ra vào thứ hai, nhóm đã cập nhật tiến độ.",
        "Đã gửi báo cáo tổng hợp các hạng mục trong sprint.",
        "Hai bên xác nhận phạm vi công việc và kế hoạch bàn giao.",
        "Đã đối chiếu danh sách tệp theo hợp đồng hiện tại.",
        "Giao đúng hạn và thiết kế đẹp nhưng một số trang bị lỗi.",
        "Chất lượng tốt nhưng phản hồi hơi chậm khi cần hỗ trợ.",
        "Tiến độ chậm nhưng thái độ hỗ trợ tốt và chuyên nghiệp.",
        "Bản demo dễ sử dụng nhưng có vài lỗi cần khắc phục.",
        "Dự án số 20 đã được ghi nhận vào hệ thống."
    };
    @Override @Transactional public void run(String... args) {
        if (!enabled) return;
        List<TalentReview> existing = reviews.findAll().stream()
            .filter(r -> r.getComment()!=null && r.getComment().startsWith("[DEMO V177] Đánh giá quá trình hợp tác dự án"))
            .sorted(java.util.Comparator.comparing(TalentReview::getId)).toList();
        int index=0;
        for (TalentReview r : existing) {
            int n=index++ % COMMENTS.length;
            r.setComment("[DEMO AI V192] " + COMMENTS[n]);
            r.setRating(n<5?5:n<10?2:n<15?3:n<19?4:5);
        }
        reviews.saveAll(existing);
    }
}
