package com.talentmatch.service;

import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;

/** Transparent Vietnamese lexicon baseline. A zero score means neutral/undetermined, not objective truth. */
@Service
public class VietnameseSentimentService {
    public record Prediction(String label, String code, int score, String evidence) {}
    private static final Map<String,Integer> WORDS = new LinkedHashMap<>();
    static {
        for (String w : List.of("tot", "tuyet voi", "hai long", "xuat sac", "chuyen nghiep", "nhanh", "dung han", "uy tin", "de su dung", "dep", "cam on", "chat luong", "hieu qua", "than thien", "rat tot", "on dinh", "ho tro tot", "chu dao", "hai long lam")) WORDS.put(w, 2);
        for (String w : List.of("te", "that vong", "cham", "tre han", "loi", "kem", "khong tot", "khong hai long", "qua te", "lua dao", "kho su dung", "khong dung", "thieu", "hong", "kho chiu", "that bai", "khong chuyen nghiep", "khong uy tin", "khong hieu qua", "bi loi")) WORDS.put(w, -2);
    }
    private String standardize(String s) {
        if (s == null) return "";
        return " " + Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ','d'), Normalizer.Form.NFD)
          .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim() + " ";
    }
    public Prediction classify(String raw) {
        if (raw == null || raw.isBlank()) return new Prediction("Chưa đủ tín hiệu", "UNCLEAR", 0, "Không có nội dung");
        String text = standardize(raw); int total = 0;
        List<String> hits = new ArrayList<>();
        // Prefer longer expressions and avoid counting a short word inside a matched phrase.
        List<String> phrases = new ArrayList<>(WORDS.keySet());
        phrases.sort(Comparator.comparingInt(String::length).reversed());
        for (String phrase : phrases) {
            String pattern = " " + phrase + " ";
            int index;
            while ((index = text.indexOf(pattern)) >= 0) {
                int val = WORDS.get(phrase);
                String before = text.substring(Math.max(0,index-20), index).trim();
                if (val > 0 && (before.endsWith("khong") || before.endsWith("chua") || before.endsWith("chang"))) val = -val;
                total += val; hits.add(phrase + (val > 0 ? " (+)" : " (-)"));
                text = text.substring(0,index) + " ".repeat(pattern.length()) + text.substring(index+pattern.length());
            }
        }
        if (hits.isEmpty()) {
            // Factual, descriptive feedback can be neutral without containing any evaluative word.
            for (String phrase : List.of("da nhan", "da gui", "da doi chieu", "hai ben xac nhan", "buoi hop", "theo ke hoach", "da ban giao", "cap nhat tien do")) {
                if (standardize(raw).contains(" " + phrase + " "))
                    return new Prediction("Trung lập", "NEUTRAL", 0, "Mô tả sự kiện: " + phrase);
            }
            return new Prediction("Chưa đủ tín hiệu", "UNCLEAR", 0, "Không phát hiện từ khóa cảm xúc rõ ràng");
        }
        String label = total > 0 ? "Tích cực" : total < 0 ? "Tiêu cực" : "Trung lập";
        String code = total > 0 ? "POSITIVE" : total < 0 ? "NEGATIVE" : "NEUTRAL";
        return new Prediction(label, code, total, hits.isEmpty()?"Không phát hiện từ khóa cảm xúc rõ ràng":String.join(", ", hits));
    }
}
