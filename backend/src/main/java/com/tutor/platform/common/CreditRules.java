package com.tutor.platform.common;

/**
 * 老师信用与推荐排序的统一口径（排序演示 + 后台可调权重 + 单测都用这里）。
 *
 * <p>信用分：满分 100 起步，违规扣分、完成订单加分，边界 [0, 100]。
 * <p>推荐分：认证(35%) + 评分(25%) + 完课(20%) + 信用(20%)，权重由 application.yml 注入，
 * 前台展示"推荐理由"时按 {@link #reasonLabel} 生成可解释文案。
 */
public final class CreditRules {

    /** 信用分下限 */
    public static final int MIN_SCORE = 0;
    /** 信用分上限 */
    public static final int MAX_SCORE = 100;
    /** 完成一单加分 */
    public static final int FINISH_ADD = 1;

    /** 等级阈值：>=90 A，>=75 B，>=60 C，其余 D */
    public static final String[] LEVEL_NAMES = {"A", "B", "C", "D"};
    public static final String[] LEVEL_LABELS = {"金牌老师", "优质老师", "普通老师", "受限老师"};

    private final double wCert;
    private final double wRating;
    private final double wFinish;
    private final double wCredit;

    public CreditRules(double wCert, double wRating, double wFinish, double wCredit) {
        this.wCert = wCert;
        this.wRating = wRating;
        this.wFinish = wFinish;
        this.wCredit = wCredit;
    }

    /** 信用分边界收敛 */
    public static int clamp(int score) {
        if (score < MIN_SCORE) {
            return MIN_SCORE;
        }
        return Math.min(score, MAX_SCORE);
    }

    /** 计算新信用分（扣分场景传负数的 delta） */
    public static int apply(int current, int delta) {
        return clamp((current < 0 ? MAX_SCORE : current) + delta);
    }

    /** 等级下标：0/1/2/3 对应 A/B/C/D */
    public static int levelIndex(int score) {
        if (score >= 90) {
            return 0;
        }
        if (score >= 75) {
            return 1;
        }
        if (score >= 60) {
            return 2;
        }
        return 3;
    }

    public static String levelName(int score) {
        return LEVEL_NAMES[levelIndex(score)];
    }

    public static String levelLabel(int score) {
        return LEVEL_LABELS[levelIndex(score)];
    }

    /** 限制可预约的最低信用分：低于 60（C 级以下）的 D 级老师不能再接单 */
    public static final int MIN_BOOKABLE_SCORE = 60;

    public static boolean isBookable(int credit) {
        return credit >= MIN_BOOKABLE_SCORE;
    }

    /** 推荐分：0~1 之间，四个维度加权求和 */
    public double recommendScore(Integer certStatus, Double rating, Long finished, Integer credit) {
        int cs = certStatus == null ? 0 : certStatus;
        double certPart = cs == 2 ? 1.0 : (cs == 1 ? 0.35 : 0.0);
        double r = rating == null ? 0.0 : Math.max(0.0, Math.min(5.0, rating)) / 5.0;
        long fin = finished == null ? 0L : finished;
        double finPart = fin <= 0 ? 0.0 : Math.min(1.0, Math.log10(fin + 1) / 2.0); // 99 单 = 1.0
        int cr = credit == null ? MAX_SCORE : credit;
        double creditPart = Math.max(0.0, Math.min(1.0, cr / (double) MAX_SCORE));
        return round2(wCert * certPart + wRating * r + wFinish * finPart + wCredit * creditPart);
    }

    /** 推荐理由：只取影响最大的两三条，避免前台堆字 */
    public String reasonLabel(Integer certStatus, Double rating, Long finished, Integer credit) {
        StringBuilder sb = new StringBuilder();
        if (certStatus != null && certStatus == 2) {
            sb.append("已认证教师");
        }
        if (finished != null && finished >= 10) {
            append(sb, "完课 " + finished + " 单");
        }
        if (rating != null && rating >= 4.5) {
            append(sb, "好评 " + rating);
        }
        if (credit != null && credit >= 90) {
            append(sb, "信用 " + credit);
        }
        return sb.length() == 0 ? "新入驻老师" : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(part);
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public double weightCert() {
        return wCert;
    }

    public double weightRating() {
        return wRating;
    }

    public double weightFinish() {
        return wFinish;
    }

    public double weightCredit() {
        return wCredit;
    }
}
