package com.timeslot.identity.domain;

/** user_violation.violation_type 的取值常量（与 migration V1_3 注释保持一致）。 */
public final class ViolationType {
    public static final String CREDIT_DEDUCT = "CREDIT_DEDUCT";
    public static final String CREDIT_REWARD = "CREDIT_REWARD";
    public static final String BLACKLIST_SET = "BLACKLIST_SET";
    public static final String BLACKLIST_RELEASE = "BLACKLIST_RELEASE";
    public static final String ACCOUNT_DISABLE = "ACCOUNT_DISABLE";
    public static final String ACCOUNT_ENABLE = "ACCOUNT_ENABLE";

    /** 系统自动记录的原因前缀，用于区分自动黑名单与人工黑名单。 */
    public static final String AUTO_REASON_PREFIX = "系统自动";

    private ViolationType() {
    }
}
