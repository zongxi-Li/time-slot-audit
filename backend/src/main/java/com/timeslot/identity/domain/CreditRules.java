/**
 * 文件职责：集中定义信用分变化和自动限制规则使用的业务常量。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；常量供信用分和黑名单规则引用。
*/

package com.timeslot.identity.domain;

/**
 * 信用与限制规则常量：身份域唯一权威来源。
 * reservation 域不得直接读取本类或身份表，只能调用 BookingQualificationService 获取判定结论。
 */
public final class CreditRules {
    /** 新用户/默认信用分。 */
    public static final int DEFAULT_CREDIT_SCORE = 100;
    /** 预约资格信用门槛：低于该值不允许预约。 */
    public static final int MIN_BOOKING_CREDIT_SCORE = 60;
    /** 自动进入黑名单阈值：信用分低于该值时自动限制。 */
    public static final int AUTO_BLACKLIST_THRESHOLD = 40;
    /** 自动进入黑名单的默认限制天数。 */
    public static final int AUTO_BLACKLIST_DAYS = 30;

    private CreditRules() {
    }
}
