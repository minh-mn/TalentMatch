package com.talentmatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class GroqAssistantService {
    public record Result(String text, String error) {}
    private final ObjectMapper json;
    private final String key, model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Semaphore slots = new Semaphore(3);
    public GroqAssistantService(ObjectMapper json, @Value("${app.chat.groq.key:}") String key,
            @Value("${app.chat.groq.model:openai/gpt-oss-20b}") String model) {
        this.json=json; this.key=key.trim(); this.model=model.trim();
    }
    public boolean enabled(){return !key.isBlank() && model.matches("[a-zA-Z0-9._/-]+");}
    public Result answer(String question, String role, List<Map<String,String>> history){
        if(!enabled()) return new Result(null,"Groq chưa được cấu hình.");
        if(!slots.tryAcquire()) return new Result(null,"Groq đang bận, vui lòng thử lại.");
        try {
            List<Map<String,String>> messages = new ArrayList<>();
            messages.add(Map.of("role","system","content","Bạn là trợ lý TalentMatch. Trả lời tiếng Việt, rõ ràng, hỗ trợ viết nội dung và nghiệp vụ freelancer. Vai trò hiện tại: "+role+". Không có quyền truy cập dữ liệu cá nhân, database hoặc thực hiện giao dịch. Không bịa số dư, trạng thái, mã giao dịch hay đường dẫn. Thanh toán VNPay Sandbox và rút tiền chỉ là mô phỏng; giải ngân nội bộ sau nghiệm thu và thời gian chờ theo cấu hình. Không yêu cầu mật khẩu, OTP, API key hoặc thông tin ngân hàng. Hướng dẫn người dùng xem chức năng nội bộ nếu cần dữ liệu thật."));
            messages.addAll(history);
            messages.add(Map.of("role","user","content",question));
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("max_completion_tokens", 2048);
            payload.put("temperature", 0.6);
            payload.put("stream", false);
            // Only send reasoning options to models that support them.
            if (model.equals("openai/gpt-oss-20b") || model.equals("openai/gpt-oss-120b")) {
                payload.put("reasoning_effort", "low");
                payload.put("include_reasoning", false);
            }
            var request=HttpRequest.newBuilder(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) return new Result(null,"Groq HTTP "+response.statusCode()+switch(response.statusCode()){
                case 401,403 -> ": kiểm tra API key và quyền truy cập.";
                case 429 -> ": vượt hạn mức, thử lại sau.";
                case 400,404 -> ": kiểm tra model và cấu hình.";
                default -> ": dịch vụ chưa sẵn sàng.";
            });
            return parseResponse(response.body());
        } catch(InterruptedException e){Thread.currentThread().interrupt();return new Result(null,"Yêu cầu Groq bị gián đoạn.");}
        catch(java.net.http.HttpTimeoutException e){return new Result(null,"Groq phản hồi quá thời gian chờ.");}
        catch(Exception e){return new Result(null,"Không thể kết nối hoặc đọc phản hồi Groq.");}
        finally{slots.release();}
    }
    // Read only the public answer, never the model's reasoning field.
    Result parseResponse(String body) throws com.fasterxml.jackson.core.JsonProcessingException {
        var choice = json.readTree(body).path("choices").path(0);
        var content = choice.path("message").path("content");
        String text = content.isTextual() ? content.asText().trim() : "";
        String finish = choice.path("finish_reason").asText("");
        if (text.isBlank()) {
            String detail = switch (finish) {
                case "length" -> "Groq hết giới hạn token trước khi tạo câu trả lời. Hãy thử câu hỏi ngắn hơn.";
                case "content_filter" -> "Groq không thể trả lời do bộ lọc nội dung.";
                case "tool_calls" -> "Groq yêu cầu công cụ không được chatbot hỗ trợ.";
                default -> "Groq không trả về nội dung trả lời (finish_reason: " +
                    (Set.of("stop", "length", "content_filter", "tool_calls").contains(finish) ? finish : "unknown") + ").";
            };
            return new Result(null, detail);
        }
        if ("length".equals(finish)) text += "\n\n[Câu trả lời đã đạt giới hạn độ dài.]";
        return new Result(text.substring(0, Math.min(text.length(), 4000)), null);
    }
}
