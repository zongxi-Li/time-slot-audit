/**
 * 文件职责：返回一次会议的应到、已签到和缺席人数等考勤汇总。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件定义考勤视图字段。
*/

package com.timeslot.meeting.dto;

import java.util.List;

/** 单场预约的出勤总览：参与人明细 + 状态汇总 + 当前查看人自己的出勤。 */
public record AttendanceView(Long reservationId, int totalCount, long expectedCount, long checkedInCount,
                             long checkedOutCount, long noShowCount, List<AttendeeView> attendees,
                             AttendeeView mine) {
}
