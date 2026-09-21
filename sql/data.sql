-- =============================================================================
-- 会议室预约与时间冲突检查系统 —— 初始化测试数据
-- -----------------------------------------------------------------------------
-- 执行顺序：先执行 schema.sql，再执行本文件
-- 说明：
--   1. 预约时间使用相对当前日期(CURDATE)的动态时间，保证任何时候导入都处于
--      “未来”区间，适合前后端联调（未来可查、可审批、可取消）。
--   2. 测试密码仍为 123456，但数据库只保存 BCrypt 摘要。
--   3. 预约单号为演示值；实际由服务端按 “RSV+日期+序号” 规则生成。
-- =============================================================================

USE meeting_room;

-- 清空旧数据（逆序清空，避免外键干扰）
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE user_violation;
TRUNCATE TABLE operation_log;
TRUNCATE TABLE meeting_execution;
TRUNCATE TABLE notification;
TRUNCATE TABLE reservation_attendee;
TRUNCATE TABLE approval_record;
TRUNCATE TABLE reservation;
TRUNCATE TABLE room_open_rule;
TRUNCATE TABLE room_facility;
TRUNCATE TABLE meeting_room;
TRUNCATE TABLE room_category;
TRUNCATE TABLE sys_user;
TRUNCATE TABLE department;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 0. 部门：最小组织模型演示数据（v1.3 身份治理）
-- =============================================================================
INSERT INTO department (id, dept_name, description) VALUES
(1, '信息中心',   '校园信息化建设与运维部门'),
(2, '软件学院',   '软件工程专业教学单位'),
(3, '后勤保障处', '场地与后勤保障部门');

-- =============================================================================
-- 1. 用户：1 管理员 + 2 普通用户（信用分与违规记录保持一致，见第 8 节）
-- =============================================================================
INSERT INTO sys_user (id, username, password, real_name, email, phone, role, department_id, credit_score, status) VALUES
(1, 'admin',    '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '系统管理员', 'admin@timeslot.demo',    '13800000001', 'ADMIN', 1,    100, 1),
(2, 'zhangsan', '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '张三',       'zhangsan@timeslot.demo', '13800000002', 'USER',  2,    110, 1),
(3, 'lisi',     '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', '李四',       'lisi@timeslot.demo',     '13800000003', 'USER',  1,    80,  1);

-- =============================================================================
-- 2. 会议室分类：审批开关配置在分类上
--    小型/普通免审批（提交即 CONFIRMED），大型/特殊需审批（提交进 PENDING）
-- =============================================================================
INSERT INTO sys_user (id, username, password, real_name, email, phone, role, department_id, credit_score, status) VALUES
(4, 'lzx', '$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm', 'LZX', 'lzx@timeslot.demo', '13800000004', 'ADMIN', 1, 100, 1);

INSERT INTO room_category (id, category_name, min_capacity, max_capacity, approval_required, max_duration_minutes, advance_days, description) VALUES
(1, '小型会议室', 3,   8,   0, 1440, 7,  '3-8人日常讨论，免审批，单次最长24小时，可提前7天预约'),
(2, '普通会议室', 9,   20,  0, 1440, 7,  '9-20人常规会议，免审批，单次最长24小时，可提前7天预约'),
(3, '大型会议室', 21,  100, 1, 1440, 14, '21人以上大型会议，需管理员审批，单次最长24小时，可提前14天预约'),
(4, '特殊会议室', 10,  60,  1, 1440, 14, '路演厅、多功能厅等受控场地，需管理员审批，单次最长24小时，可提前14天预约');

-- =============================================================================
-- 3. 会议室：6 间（容量均落在所属分类 [min_capacity, max_capacity] 区间内）
-- =============================================================================
INSERT INTO meeting_room (id, category_id, room_name, location, capacity, status, description) VALUES
(1, 1, 'A301', '教学楼A栋3层', 8,  1, '4-8人圆桌讨论间，配白板'),
(2, 1, 'A302', '教学楼A栋3层', 6,  1, '6人小组讨论间'),
(3, 2, 'B201', '教学楼B栋2层', 16, 1, '常规投影会议室'),
(4, 2, 'B202', '教学楼B栋2层', 20, 1, '支持视频会议的常规会议室'),
(5, 3, 'B502', '教学楼B栋5层', 60, 1, '阶梯报告厅，大型会议场地'),
(6, 4, 'S101', '综合楼1层',    40, 1, '路演厅，含灯光音响，使用需审批');

-- =============================================================================
-- 4. 会议室设施
-- =============================================================================
INSERT INTO meeting_room (id, category_id, room_name, location, capacity, status, description) VALUES
(7,  1, 'C101', 'Teaching Building C, 1F',  8,  1, 'Small team room with projector and whiteboard'),
(8,  2, 'C201', 'Teaching Building C, 2F', 18,  1, 'Standard meeting room with video equipment'),
(9,  3, 'D301', 'Teaching Building D, 3F', 50,  1, 'Large lecture room for department events'),
(10, 4, 'D401', 'Teaching Building D, 4F', 35,  1, 'Multi-purpose presentation room'),
(11, 2, 'E201', 'Library Building E, 2F', 16,  1, 'Quiet discussion room');

-- =============================================================================
-- 4. 会议室设施：覆盖任务书"投影、视频会议、白板等"并扩充种类，
--    统一中文命名便于按关键字筛选（如 投影 / 白板 / 麦克风 / 投屏）。
-- =============================================================================
INSERT INTO room_facility (id, room_id, facility_name, quantity, description) VALUES
(1,  1, '投影仪',       1, NULL),
(2,  1, '白板',         1, NULL),
(3,  1, '无线投屏',     1, NULL),
(4,  2, '显示屏',       1, '55寸电视'),
(5,  2, '白板',         1, NULL),
(6,  3, '投影仪',       1, NULL),
(7,  3, '麦克风',       2, '无线手持'),
(8,  3, '讲台电脑',     1, NULL),
(9,  4, '投影仪',       1, NULL),
(10, 4, '视频会议设备', 1, NULL),
(11, 4, '白板',         1, NULL),
(12, 5, '投影仪',       2, NULL),
(13, 5, '视频会议设备', 1, NULL),
(14, 5, '无线麦克风',   4, NULL),
(15, 5, '音响系统',     1, NULL),
(16, 6, '视频会议设备', 1, NULL),
(17, 6, '音响系统',     1, NULL),
(18, 6, '舞台灯光',     1, '路演用，需管理员协助开启'),
(19, 6, '无线投屏',     1, NULL),
(20, 7, '投影仪',       1, '吸顶安装'),
(21, 7, '白板',         1, NULL),
(22, 7, '电子白板',     1, NULL),
(23, 8, '投影仪',       1, NULL),
(24, 8, '视频会议设备', 1, NULL),
(25, 8, '讲台电脑',     1, NULL),
(26, 9, '投影仪',       2, NULL),
(27, 9, '无线麦克风',   4, NULL),
(28, 9, '视频摄像头',   1, '录播用'),
(29, 10, '音响系统',    1, NULL),
(30, 10, '舞台灯光',    1, NULL),
(31, 10, '麦克风',      2, NULL),
(32, 11, '显示屏',      1, '65寸显示屏'),
(33, 11, '白板',        1, NULL),
(34, 11, '无线投屏',    1, NULL);

-- =============================================================================
-- 5. 每间会议室每周开放时间：周一至周日 08:00-19:00。
--    课程基线不包含节假日和特殊日期规则。
-- =============================================================================

INSERT INTO room_open_rule (room_id, weekday, open_time, close_time, enabled)
SELECT r.id, d.weekday, '08:00:00', '19:00:00', 1
FROM meeting_room r
CROSS JOIN (
    SELECT 1 AS weekday UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
) d;

-- =============================================================================
-- 6. 预约：覆盖四个持久化状态；已结束通过 CONFIRMED + 时间动态推导
--    room_id=3 B201 : CONFIRMED（普通会议室，免审批直接确认）
--    room_id=5 B502 : PENDING   （大型会议室，待管理员审批）
--    room_id=6 S101 : CONFIRMED + REJECTED（特殊会议室，含审批历史）
--    room_id=1 A301 : CANCELLED（管理员强制取消，带取消原因）
--    room_id=4 B202 : CONFIRMED（过去的会议，API 动态展示为 COMPLETED）
-- =============================================================================
INSERT INTO reservation (id, request_id, reservation_no, room_id, user_id, title, start_time, end_time, participant_count, status, remark, cancel_reason) VALUES
(1, 'seed-rsv-1', 'RSV20260901001', 3, 2, '项目周会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:30:00'),
   12, 'CONFIRMED', '需要投影仪', NULL),
(2, 'seed-rsv-2', 'RSV20260901002', 5, 3, '全院月度总结会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '16:00:00'),
   45, 'PENDING', '需提前调试话筒', NULL),
(3, 'seed-rsv-3', 'RSV20260901003', 6, 2, '新产品内部路演',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00'),
   30, 'CONFIRMED', '需要灯光和音响', NULL),
(4, 'seed-rsv-4', 'RSV20260901004', 6, 3, '社团招新宣讲',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '16:00:00'),
   35, 'REJECTED', NULL, NULL),
(5, 'seed-rsv-5', 'RSV20260901005', 1, 3, '小组讨论',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '16:00:00'),
   5, 'CANCELLED', NULL, '该时段安排设备检修，管理员强制取消'),
(6, 'seed-rsv-6', 'RSV20260901006', 4, 2, '迭代评审会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:30:00'),
   15, 'CONFIRMED', NULL, NULL);

-- =============================================================================
-- 6. 审批记录：仅特殊会议室（受控分类）的预约产生
--    id=3 已审批通过；id=4 已驳回；id=2 仍为 PENDING，尚无审批记录
-- =============================================================================
INSERT INTO reservation (id, request_id, reservation_no, room_id, user_id, title, start_time, end_time, participant_count, status, remark, cancel_reason) VALUES
(29, 'seed-extra-rsv-29', 'RSV20260917029', 9,  3, 'Department annual review',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '13:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00'),
   40, 'CONFIRMED', 'Approved large-room event', NULL),
(30, 'seed-extra-rsv-30', 'RSV20260917030', 10, 2, 'Product launch rehearsal',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 4 DAY), '16:00:00'),
   25, 'REJECTED', 'Waiting for a different venue', NULL),
(31, 'seed-extra-rsv-31', 'RSV20260917031', 11, 3, 'Study group discussion',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 5 DAY), '12:00:00'),
   10, 'CONFIRMED', 'Regular discussion', NULL),
(32, 'seed-extra-rsv-32', 'RSV20260917032', 8,  4, 'Administrator coordination meeting',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 6 DAY), '16:00:00'),
   12, 'CONFIRMED', 'Internal administration meeting', NULL),
(33, 'seed-extra-rsv-33', 'RSV20260917033', 7,  2, 'Team planning session',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '10:00:00'),
   6, 'CANCELLED', NULL, 'Schedule changed by the organizer'),
(34, 'seed-extra-rsv-34', 'RSV20260917034', 8,  3, 'Cross-team sync',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '16:00:00'),
   12, 'CONFIRMED', 'Weekly cross-team sync', NULL),
(35, 'seed-extra-rsv-35', 'RSV20260917035', 9,  4, 'Public lecture preparation',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '12:00:00'),
   30, 'PENDING', 'Awaiting administrator approval', NULL);

INSERT INTO approval_record (id, reservation_id, approver_id, action, remark) VALUES
(1, 3, 1, 'APPROVE', '场地与设备已确认，同意使用'),
(2, 4, 1, 'REJECT',  '该时段需预留场地维护，建议改期至其他日期');

-- =============================================================================
-- 7. 操作日志：覆盖审批、驳回、建会议室、改分类、强制取消五类关键管理行为
-- =============================================================================
INSERT INTO approval_record (id, reservation_id, approver_id, action, remark) VALUES
(7, 29, 4, 'APPROVE', 'Approved by lzx for the large-room event'),
(8, 30, 4, 'REJECT',  'The special room is reserved for another event');

INSERT INTO operation_log (id, user_id, operation_type, business_type, business_id, content, ip_address) VALUES
(1, 1, 'CREATE_ROOM',              'MEETING_ROOM',  5, '新增会议室 B502（阶梯报告厅，大型会议室分类）',        '127.0.0.1'),
(2, 1, 'UPDATE_CATEGORY',          'ROOM_CATEGORY', 4, '调整特殊会议室规则：需审批，单次最长8小时',            '127.0.0.1'),
(3, 1, 'APPROVE_RESERVATION',      'RESERVATION',   3, '审批通过预约 RSV20260901003（新产品内部路演）',        '127.0.0.1'),
(4, 1, 'REJECT_RESERVATION',       'RESERVATION',   4, '驳回预约 RSV20260901004（社团招新宣讲）',              '127.0.0.1'),
(5, 1, 'FORCE_CANCEL_RESERVATION', 'RESERVATION',   5, '强制取消预约 RSV20260901005（小组讨论），原因：设备检修', '127.0.0.1');

-- =============================================================================
-- 8. 违规与信用记录（v1.3 身份治理）：与第 1 节信用分保持一致
--    zhangsan 100+10=110；lisi 100-20=80（仍高于预约资格门槛 60，可正常预约）
-- =============================================================================
INSERT INTO operation_log (id, user_id, operation_type, business_type, business_id, content, ip_address) VALUES
(11, 4, 'CREATE_ROOM',         'MEETING_ROOM', 7,  'Created meeting room C101', '127.0.0.1'),
(12, 4, 'CREATE_ROOM',         'MEETING_ROOM', 8,  'Created meeting room C201', '127.0.0.1'),
(13, 4, 'CREATE_ROOM',         'MEETING_ROOM', 9,  'Created meeting room D301', '127.0.0.1'),
(14, 4, 'CREATE_ROOM',         'MEETING_ROOM', 10, 'Created meeting room D401', '127.0.0.1'),
(15, 4, 'APPROVE_RESERVATION', 'RESERVATION',  29, 'Approved reservation RSV20260917029', '127.0.0.1'),
(16, 4, 'REJECT_RESERVATION',  'RESERVATION',  30, 'Rejected reservation RSV20260917030', '127.0.0.1');

INSERT INTO user_violation (id, user_id, violation_type, credit_change, reason, operator_id) VALUES
(1, 2, 'CREDIT_REWARD', 10,  '协助保障多场大型会议顺利举行，信用奖励', 1),
(2, 3, 'CREDIT_DEDUCT', -20, '预约后未到场且未提前取消，信用扣分',     1);
