/**
 * 文件职责：表示用户的一条违规及其信用分变化记录。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：autoTriggered 判断记录是否由系统自动规则触发；record 访问器由 Java 自动生成。
*/

package com.timeslot.identity.domain;

import java.time.LocalDateTime;

/** 违规/信用记录（读模型）：operator_name 通过 LEFT JOIN 冗余查询。 */
public record UserViolation(Long id, Long userId, String violationType, Integer creditChange, String reason,
                            Long operatorId, LocalDateTime createdAt, String operatorName) {
    public boolean autoTriggered() {
        return operatorId == null || (reason != null && reason.startsWith(ViolationType.AUTO_REASON_PREFIX));
    }
}
