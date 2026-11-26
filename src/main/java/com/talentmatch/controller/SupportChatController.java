package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.*;
import com.talentmatch.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.text.Normalizer;
import java.util.*;

/** Read-only, role-scoped support assistant. No external AI or financial actions. */
@RestController
@RequestMapping("/assistant")
public class SupportChatController {
    private final UserService users;
    private final ProjectRepository projects;
    private final PaymentRepository payments;
    private final WalletDemoService wallet;
    private final GeminiAssistantService gemini;
    private final GroqAssistantService groq;
    public SupportChatController(UserService users, ProjectRepository projects, PaymentRepository payments, WalletDemoService wallet, GeminiAssistantService gemini, GroqAssistantService groq) {
        this.users=users; this.projects=projects; this.payments=payments; this.wallet=wallet; this.gemini=gemini; this.groq=groq;
    }
    public record Reply(String message, String link, String label, String mode) {}
    public record Context(String role, List<String> suggestions, boolean geminiEnabled, boolean groqEnabled) {}
    public record Turn(String role, String content) {}
    public record Question(String text, boolean useGemini, String provider, List<Turn> history) {}
    private static List<Map<String,String>> safeHistory(List<Turn> history){
        if(history==null) return List.of();
        return history.stream().skip(Math.max(0,history.size()-8)).filter(Objects::nonNull)
            .filter(t->("user".equals(t.role()) || "assistant".equals(t.role())) && t.content()!=null && !t.content().isBlank())
            .map(t->Map.of("role",t.role(),"content",t.content().substring(0,Math.min(1500,t.content().length())))).toList();
    }
    @GetMapping("/context")
    public ResponseEntity<Context> context(Authentication auth) {
        Role role=users.byEmail(auth.getName()).getRole();
        List<String> suggestions=switch(role){
            case FREELANCER -> List.of("Số dư ví của tôi", "Trạng thái rút tiền", "Dự án của tôi", "Tìm công việc phù hợp");
            case CUSTOMER -> List.of("Giao dịch của tôi", "Dự án của tôi", "Thanh toán milestone thế nào?", "Tìm Freelancer");
            case ADMIN -> List.of("Giao dịch đang chờ", "Yêu cầu rút tiền đang chờ", "Quy trình giải ngân", "Xử lý tranh chấp");
        };
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Context(role.name(), suggestions, gemini.enabled(), groq.enabled()));
    }
    private static String norm(String s){return Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ','d'), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");}
    private static boolean has(String s,String... words){return Arrays.stream(words).anyMatch(s::contains);}
    private static Reply r(String message,String link,String label){return new Reply(message,link,label,"LOCAL");}
    @PostMapping("/ask")
    public ResponseEntity<Reply> ask(@RequestBody Question question, Authentication auth){
        String raw=question.text();
        if(raw==null || raw.isBlank() || raw.length()>500) return ResponseEntity.badRequest().body(r("Vui lòng nhập câu hỏi từ 1 đến 500 ký tự.",null,null));
        User user=users.byEmail(auth.getName()); String s=norm(raw); Role role=user.getRole();
        Reply reply;
        if(has(s,"so du","vi cua toi") && role==Role.FREELANCER){
            var b=wallet.summary(user);
            reply=r("Số dư đã ghi nhận: khả dụng "+b.available().toPlainString()+"đ; đang giữ cho yêu cầu rút "+b.held().toPlainString()+"đ; đã rút "+b.withdrawn().toPlainString()+"đ. Xem trang thu nhập để đối chiếu số dư mới nhất.","/freelancer/earnings","Thanh toán & Thu nhập");
        } else if(has(s,"rut tien","chuyen tien") && role==Role.ADMIN){
            var rows=wallet.allWithdrawals();
            long pending=rows.stream().filter(w->w.getStatus()==WalletWithdrawal.State.PENDING).count();
            long processing=rows.stream().filter(w->w.getStatus()==WalletWithdrawal.State.PROCESSING).count();
            reply=r("Có "+pending+" yêu cầu chờ duyệt và "+processing+" yêu cầu đang chuyển tiền thử nghiệm. Duyệt, bắt đầu chuyển, rồi xác nhận kết quả; không chuyển tiền thật.","/admin/wallet-withdrawals","Quản lý rút tiền");
        } else if(has(s,"rut tien","chuyen tien") && role==Role.FREELANCER){
            var rows=wallet.withdrawals(user);
            String latest=rows.isEmpty()?"Bạn chưa có yêu cầu rút tiền.":"Yêu cầu gần nhất WD-"+rows.get(0).getId()+": "+rows.get(0).getStatusLabel()+".";
            reply=r(latest+" Chọn ngân hàng và số tiền khả dụng trên trang thu nhập. Yêu cầu thất bại hoặc bị từ chối sẽ được hoàn lại số dư.","/freelancer/earnings?tab=withdrawals","Yêu cầu rút tiền");
        } else if(has(s,"giao dich cua toi","giao dich dang cho")){
            List<Payment> rows=role==Role.ADMIN?payments.findAll():role==Role.CUSTOMER?payments.findByPayerOrderByCreatedAtDesc(user):List.of();
            if(role==Role.FREELANCER) reply=r("Bạn có thể xem giao dịch thuộc dự án của mình trong trang thu nhập.","/freelancer/earnings","Xem thu nhập");
            else reply=r("Có "+rows.size()+" giao dịch; "+rows.stream().filter(p->p.getStatus()==PaymentStatus.PENDING).count()+" đang chờ xác minh. Không thanh toán lại khi giao dịch vẫn đang chờ.",role==Role.ADMIN?"/admin/payments":"/customer/payments","Xem giao dịch");
        } else if(has(s,"du an cua toi","tien do")){
            var rows=role==Role.ADMIN?projects.findAll():role==Role.CUSTOMER?projects.findByCustomerOrderByCreatedAtDesc(user):projects.findByFreelancerOrderByCreatedAtDesc(user);
            reply=r("Có "+rows.size()+" dự án trong phạm vi của bạn; "+rows.stream().filter(p->p.getStatus()==ProjectStatus.IN_PROGRESS).count()+" đang thực hiện, "+rows.stream().filter(p->p.getStatus()==ProjectStatus.WAITING_ACCEPTANCE).count()+" chờ nghiệm thu.",role==Role.ADMIN?"/admin/projects":role==Role.CUSTOMER?"/customer/projects":"/freelancer/projects","Xem dự án");
        } else if(has(s,"tranh chap","khieu nai")){
            reply=r("Hãy cung cấp bằng chứng và nội dung hợp đồng để Admin xem xét. Không tự động giải ngân khoản đang tranh chấp.",role==Role.ADMIN?"/admin/disputes":role==Role.CUSTOMER?"/customer/support":"/freelancer/projects","Xem khu vực xử lý");
        } else if(has(s,"giai ngan","nhan tien","nghiem thu","thanh toan","milestone","hoan tien")){
            reply=r("Hai bên xác nhận hợp đồng trước khi thanh toán. VNPay Sandbox xác minh giao dịch; sau bàn giao và nghiệm thu, khoản đủ điều kiện được giải ngân nội bộ theo chính sách nền tảng. Không trả lại khi giao dịch đang chờ xác minh. Hoàn tiền cần được đối soát; chatbot không thực hiện giao dịch.",role==Role.ADMIN?"/admin/payments":role==Role.CUSTOMER?"/customer/payments":"/freelancer/earnings","Xem tài chính");
        } else if(has(s,"tim","goi y","ung tuyen","freelancer","cong viec")){
            reply=role==Role.CUSTOMER?r("Chọn công việc đang tuyển để xếp hạng Freelancer theo kỹ năng và hồ sơ. Quản lý ứng viên là nơi xử lý đơn đã nộp.","/customer/talent","Tìm & gợi ý Freelancer"):role==Role.FREELANCER?r("Bổ sung kỹ năng trong hồ sơ và xem công việc được xếp hạng phù hợp. Điểm matching chỉ là gợi ý.","/ai/matching","Gợi ý công việc"):r("Admin có thể kiểm tra công việc và hồ sơ trên trang quản lý.","/admin/jobs","Quản lý công việc");
        } else if(has(s,"cam xuc","danh gia","phan hoi")) reply=r("Phân tích phản hồi dựa trên từ khóa tiếng Việt và chỉ là tín hiệu hỗ trợ.","/ai/sentiment","Phân tích phản hồi");
        else {
            String provider=question.provider()==null?(question.useGemini()?"gemini":"local"):question.provider();
            String failure="";
            if("groq".equals(provider)){
                var answer=groq.answer(raw,role.name(),safeHistory(question.history()));
                if(answer.text()!=null) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Reply(answer.text(),null,null,"GROQ"));
                failure=answer.error()+" Đang chuyển về hỗ trợ nội bộ. ";
            }
            if("gemini".equals(provider) && gemini.enabled()){
                var answer=gemini.answer(raw,role.name());
                if(answer.isPresent()) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Reply(answer.get(),null,null,"GEMINI"));
                failure="Gemini không trả lời được; kiểm tra quyền API, model hoặc hạn mức. Đang chuyển về hỗ trợ nội bộ. ";
            } else if("gemini".equals(provider)) failure="Gemini chưa được cấu hình. ";
            reply=r(failure+"Tôi là trợ lý theo quy tắc của TalentMatch. Tôi hỗ trợ tra cứu dự án, tài chính, tuyển dụng và phản hồi theo vai trò của bạn. Hãy chọn một câu hỏi gợi ý hoặc mô tả rõ nghiệp vụ cần hỗ trợ.",null,null);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reply);
    }
}
