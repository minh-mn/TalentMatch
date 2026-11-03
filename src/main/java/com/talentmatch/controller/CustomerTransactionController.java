package com.talentmatch.controller;

import com.talentmatch.model.Payment;
import com.talentmatch.model.PaymentStatus;
import com.talentmatch.model.Role;
import com.talentmatch.model.User;
import com.talentmatch.service.PaymentService;
import com.talentmatch.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Dashboard lịch sử GIAO DỊCH của Client. Không phải cổng thanh toán hay hóa đơn thuế. */
@Controller
@RequestMapping("/customer/history")
public class CustomerTransactionController {
    private static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final UserService users;
    private final PaymentService payments;

    public CustomerTransactionController(UserService users, PaymentService payments) {
        this.users = users;
        this.payments = payments;
    }

    private User customer(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(FORBIDDEN);
        User user = users.byEmail(auth.getName());
        if (user == null || user.getRole() != Role.CUSTOMER) throw new ResponseStatusException(FORBIDDEN);
        return user;
    }

    @GetMapping
    public String history(Authentication auth, Model model,
            @RequestParam(defaultValue = "transactions") String tab,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page) {
        User user = customer(auth);
        String selectedTab = "receipts".equals(tab) ? "receipts" : "transactions";
        String search = cleanSearch(q);
        String selectedType = validType(type);
        String selectedStatus = validStatus(status);
        List<TransactionRow> all = allRows(user);
        List<TransactionRow> found = filter(all, selectedTab, search, selectedType, selectedStatus, from, to);
        int pages = Math.max(1, (found.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(1, Math.min(page, pages));
        int start = Math.min((current - 1) * PAGE_SIZE, found.size());
        List<TransactionRow> visible = found.subList(start, Math.min(start + PAGE_SIZE, found.size()));

        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        for (TransactionRow row : all) {
            if (row.getStatus() == PaymentStatus.PAID || row.getStatus() == PaymentStatus.DISBURSED) {
                paid = paid.add(row.getAmount());
            } else if (row.getStatus() == PaymentStatus.PENDING) {
                pending = pending.add(row.getAmount());
            } else if (row.getStatus() == PaymentStatus.REFUNDED) {
                refunded = refunded.add(row.getAmount());
            }
        }
        model.addAttribute("paidTotal", paid);
        model.addAttribute("pendingTotal", pending);
        model.addAttribute("refundTotal", refunded);
        model.addAttribute("transactionCount", all.size());
        model.addAttribute("receiptCount", all.stream().filter(TransactionRow::isReceiptAvailable).count());
        model.addAttribute("rows", visible);
        model.addAttribute("totalResults", found.size());
        model.addAttribute("tab", selectedTab);
        model.addAttribute("q", search);
        model.addAttribute("type", selectedType);
        model.addAttribute("status", selectedStatus);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("page", current);
        model.addAttribute("pages", pages);
        model.addAttribute("pageNumbers", java.util.stream.IntStream.rangeClosed(1, pages).boxed().toList());
        return "customer/history";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication auth,
            @RequestParam(defaultValue = "transactions") String tab,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        User user = customer(auth);
        List<TransactionRow> list = filter(allRows(user), "receipts".equals(tab) ? "receipts" : "transactions",
                cleanSearch(q), validType(type), validStatus(status), from, to);
        StringBuilder csv = new StringBuilder("Mã giao dịch,Loại,Nội dung,Ảnh hưởng số dư (VND),Ngày giao dịch,Trạng thái,Phương thức\r\n");
        for (TransactionRow row : list) {
            csv.append(csvCell(row.getCode())).append(',')
               .append(csvCell(row.getTypeLabel())).append(',')
               .append(csvCell(row.getDescription())).append(',')
               .append(row.getSignedAmount().toPlainString()).append(',')
               .append(csvCell(row.getDateText())).append(',')
               .append(csvCell(row.getStatusText())).append(',')
               .append(csvCell(row.getMethod())).append("\r\n");
        }
        // UTF-8 BOM để Excel trên Windows hiển thị chính xác tiếng Việt.
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=talentmatch-transactions.csv")
                .body(body);
    }

    @GetMapping("/transaction/{id}")
    public String transaction(Authentication auth, @PathVariable Long id, Model model) {
        User user = customer(auth);
        TransactionRow row = allRows(user).stream().filter(r -> r.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy giao dịch."));
        model.addAttribute("transaction", row);
        return "customer/transaction-detail";
    }

    private static String csvCell(String input) {
        String value = Objects.toString(input, "");
        // Tránh CSV formula injection khi nhập bảng vào Excel/Sheets.
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }

    private static String cleanSearch(String query) {
        return query == null ? "" : query.trim().substring(0, Math.min(query.trim().length(), 120));
    }

    private static String validType(String value) {
        return List.of("all", "project", "milestone", "refund").contains(value) ? value : "all";
    }

    private static String validStatus(String value) {
        return List.of("all", "PENDING", "PAID", "DISBURSED", "FAILED", "REFUNDED").contains(value) ? value : "all";
    }

    private List<TransactionRow> allRows(User user) {
        List<TransactionRow> result = new ArrayList<>();
        for (Payment payment : payments.history(user)) {
            if (payment.getPayer() == null || !user.getId().equals(payment.getPayer().getId())) continue;
            result.add(new TransactionRow(payment));
        }
        result.sort(Comparator.comparing(TransactionRow::getCreatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return result;
    }

    private static List<TransactionRow> filter(List<TransactionRow> rows, String tab, String q, String type,
                                                String status, LocalDate from, LocalDate to) {
        String keyword = q.toLowerCase(Locale.ROOT);
        return rows.stream().filter(row -> {
            if ("receipts".equals(tab) && !row.isReceiptAvailable()) return false;
            if (!"all".equals(type) && !type.equals(row.getType())) return false;
            if (!"all".equals(status) && !status.equals(row.getStatus().name())) return false;
            if (!keyword.isBlank() && !(row.getCode() + " " + row.getDescription() + " "
                    + row.getFreelancer()).toLowerCase(Locale.ROOT).contains(keyword)) return false;
            if (from != null && (row.getCreatedAt() == null || row.getCreatedAt().toLocalDate().isBefore(from))) return false;
            if (to != null && (row.getCreatedAt() == null || row.getCreatedAt().toLocalDate().isAfter(to))) return false;
            return true;
        }).toList();
    }

    public static final class TransactionRow {
        private final Long id;
        private final Long projectId;
        private final String code;
        private final String description;
        private final String freelancer;
        private final String type;
        private final String typeLabel;
        private final String method;
        private final PaymentStatus status;
        private final BigDecimal amount;
        private final LocalDateTime createdAt;

        TransactionRow(Payment p) {
            id = p.getId();
            projectId = p.getProject() == null ? null : p.getProject().getId();
            code = Objects.toString(p.getTransactionCode(), "TM-" + p.getId());
            String project = "Dự án";
            String partner = "Freelancer";
            if (p.getProject() != null) {
                if (p.getProject().getApplication() != null && p.getProject().getApplication().getJob() != null) {
                    project = Objects.toString(p.getProject().getApplication().getJob().getTitle(), project);
                }
                if (p.getProject().getFreelancer() != null) {
                    partner = Objects.toString(p.getProject().getFreelancer().getFullName(), partner);
                }
            }
            freelancer = partner;
            status = p.getStatus() == null ? PaymentStatus.PENDING : p.getStatus();
            if (status == PaymentStatus.REFUNDED) {
                type = "refund";
                typeLabel = "Hoàn tiền";
                description = "Hoàn tiền dự án: " + project;
            } else if (p.getMilestone() != null) {
                type = "milestone";
                typeLabel = "Thanh toán giai đoạn";
                description = "Thanh toán " + p.getMilestone().getTitle() + " – " + project;
            } else {
                type = "project";
                typeLabel = "Thanh toán dự án";
                description = "Thanh toán dự án: " + project;
            }
            amount = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
            method = Objects.toString(p.getMethod(), "Mô phỏng");
            createdAt = p.getCreatedAt() != null ? p.getCreatedAt() : p.getPaidAt();
        }
        public Long getId(){return id;}
        public Long getProjectId(){return projectId;}
        public String getCode(){return code;}
        public String getDescription(){return description;}
        public String getFreelancer(){return freelancer;}
        public String getType(){return type;}
        public String getTypeLabel(){return typeLabel;}
        public String getMethod(){return method;}
        public PaymentStatus getStatus(){return status;}
        public BigDecimal getAmount(){return amount;}
        public LocalDateTime getCreatedAt(){return createdAt;}
        public String getDateText(){return createdAt==null?"Chưa có ngày":createdAt.format(DATE_TIME);}
        public String getDateOnly(){return createdAt==null?"—":createdAt.format(DATE);}
        public String getTimeOnly(){return createdAt==null?"":createdAt.format(DateTimeFormatter.ofPattern("HH:mm"));}
        public boolean isReceiptAvailable(){return status==PaymentStatus.PAID || status==PaymentStatus.DISBURSED || status==PaymentStatus.REFUNDED;}
        public BigDecimal getSignedAmount(){return status==PaymentStatus.REFUNDED ? amount : ((status==PaymentStatus.PAID || status==PaymentStatus.DISBURSED) ? amount.negate() : BigDecimal.ZERO);}
        public String getAmountPrefix(){return status==PaymentStatus.REFUNDED ? "+" : ((status==PaymentStatus.PAID || status==PaymentStatus.DISBURSED) ? "-" : "");}
        public String getAmountClass(){return status==PaymentStatus.REFUNDED?"incoming":(status==PaymentStatus.PENDING?"pending":"outgoing");}
        public String getStatusClass(){return switch(status){case PAID,DISBURSED->"success";case PENDING->"pending";case FAILED->"failed";case REFUNDED->"refunded";};}
        public String getStatusText(){return switch(status){case PAID->"Đã thanh toán";case DISBURSED->"Đã giải ngân";case PENDING->"Đang xử lý";case FAILED->"Thất bại";case REFUNDED->"Đã hoàn tiền";};}
        public String getTypeIcon(){return switch(type){case "milestone"->"fa-file-invoice-dollar";case "refund"->"fa-rotate-left";default->"fa-wallet";};}
    }
}
