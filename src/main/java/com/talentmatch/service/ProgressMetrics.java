package com.talentmatch.service;

import com.talentmatch.model.Milestone;
import com.talentmatch.model.MilestoneStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Tỷ lệ theo giá trị mốc; phần đã nghiệm thu chỉ tính mốc COMPLETED. */
public final class ProgressMetrics {
    private ProgressMetrics() {}
    public static int reported(List<Milestone> stages) {
        BigDecimal total = BigDecimal.ZERO, weighted = BigDecimal.ZERO;
        for (Milestone stage: stages) {
            BigDecimal amount = positive(stage.getAmount());
            total = total.add(amount);
            int progress = Math.max(0, Math.min(100, stage.getProgressPercent()));
            weighted = weighted.add(amount.multiply(BigDecimal.valueOf(progress)));
        }
        return percentage(weighted, total);
    }
    public static int accepted(List<Milestone> stages) {
        BigDecimal total = BigDecimal.ZERO, weighted = BigDecimal.ZERO;
        for (Milestone stage: stages) {
            BigDecimal amount = positive(stage.getAmount());
            total = total.add(amount);
            if (stage.getStatus() == MilestoneStatus.COMPLETED)
                weighted = weighted.add(amount.multiply(BigDecimal.valueOf(100)));
        }
        return percentage(weighted, total);
    }
    private static BigDecimal positive(BigDecimal amount) {
        return amount == null || amount.signum()<0 ? BigDecimal.ZERO : amount;
    }
    private static int percentage(BigDecimal weighted, BigDecimal total) {
        return total.signum()==0 ? 0 : weighted.divide(total, 0, RoundingMode.HALF_UP).intValue();
    }
}
