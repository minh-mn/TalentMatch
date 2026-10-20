package com.talentmatch.controller;
import com.talentmatch.model.*;
public final class AdminLabels {
    private AdminLabels(){}
    public static String role(Role v){return v==null?"—":switch(v){case ADMIN->"Quản trị viên";case FREELANCER->"Freelancer";case CUSTOMER->"Khách hàng";};}
    public static String job(JobStatus v){return v==null?"—":switch(v){case DRAFT->"Nháp";case OPEN->"Đang tuyển";case PAUSED->"Tạm ẩn";case CLOSED->"Đã đóng";case CANCELLED->"Đã hủy";};}
    public static String project(ProjectStatus v){return v==null?"—":switch(v){case WAITING_PAYMENT->"Chờ thanh toán";case IN_PROGRESS->"Đang thực hiện";case WAITING_ACCEPTANCE->"Chờ nghiệm thu";case COMPLETED->"Hoàn thành";case CANCELLED->"Đã hủy";case DISPUTED->"Tranh chấp";};}
    public static String payment(PaymentStatus v){return v==null?"—":switch(v){case PENDING->"Chờ thanh toán";case PAID->"Đã ghi nhận";case DISBURSED->"Đã giải ngân";case FAILED->"Thất bại";case REFUNDED->"Hoàn tiền demo";};}
    public static String report(String v){if(v==null)return "—";return switch(v){case "PENDING"->"Chờ xử lý";case "REVIEWED"->"Đã xem";case "RESOLVED"->"Đã giải quyết";case "REJECTED"->"Không vi phạm";default->v;};}
}
