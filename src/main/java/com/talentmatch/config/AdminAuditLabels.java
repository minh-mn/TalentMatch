package com.talentmatch.config;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component("adminAuditLabels")
public class AdminAuditLabels {
    private static final Map<String,String> ACTIONS=Map.ofEntries(
        Map.entry("LOCK_USER","Khóa tài khoản"),Map.entry("UNLOCK_USER","Mở khóa tài khoản"),
        Map.entry("JOB_STATUS","Thay đổi trạng thái công việc"),Map.entry("JOB_MODERATION","Kiểm duyệt công việc"),
        Map.entry("REPORT_REVIEW","Xử lý báo cáo vi phạm"),Map.entry("DISPUTE_REVIEW_NOTE","Ghi chú kiểm tra tranh chấp"),
        Map.entry("DISPUTE_STATUS","Cập nhật tiến trình tranh chấp"));
    private static final Map<String,String> TARGETS=Map.of(
        "JOB","Công việc","USER","Người dùng","PROJECT","Dự án","DISPUTE","Tranh chấp","REPORT","Báo cáo");
    public String action(String code){return ACTIONS.getOrDefault(code,code==null?"Không xác định":code);}
    public String target(String code){return TARGETS.getOrDefault(code,code==null?"Không xác định":code);}
}
