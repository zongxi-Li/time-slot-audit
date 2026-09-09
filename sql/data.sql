-- =============================================================================
-- 会议室预约与时间冲突检查系统 —— 初始化测试数据
-- -----------------------------------------------------------------------------
-- 执行顺序：先执行 schema.sql，再执行本文件
-- 说明：
--   1. 预约时间使用相对当前日期(CURDATE)的动态时间，保证任何时候导入都处于
--      “未来”区间，适合前后端联调（未来可查、可审批、可取消）。
--   2. 密码为演示明文 123456，接入认证后需替换为 BCrypt 摘要。
--   3. 预约单号为演示值；实际由服务端按 “RSV+日期+序号” 规则生成。
-- =============================================================================

USE meeting_room;

-- 清空旧数据（逆序清空，避免外键干扰）
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE operation_log;
TRUNCATE TABLE approval_record;
TRUNCATE TABLE reservation;
TRUNCATE TABLE room_facility;
TRUNCATE TABLE meeting_room;
TRUNCATE TABLE room_category;
TRUNCATE TABLE sys_user;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 1. 用户：1 管理员 + 2 普通用户
-- =============================================================================
INSERT INTO sys_user (id, username, password, real_name, email, phone, role, status) VALUES
(1, 'admin',    '123456', '系统管理员', 'admin@timeslot.demo',    '13800000001', 'ADMIN', 1),
(2, 'zhangsan', '123456', '张三',       'zhangsan@timeslot.demo', '13800000002', 'USER',  1),
(3, 'lisi',     '123456', '李四',       'lisi@timeslot.demo',     '13800000003', 'USER',  1);

-- =============================================================================
-- 2. 会议室分类：审批开关配置在分类上
--    小型/普通免审批（提交即 CONFIRMED），大型/特殊需审批（提交进 PENDING）
-- =============================================================================
INSERT INTO room_category (id, category_name, min_capacity, max_capacity, approval_required, max_duration_minutes, advance_days, description) VALUES
(1, '小型会议室', 3,   8,   0, 120, 7,  '3-8人日常讨论，免审批，单次最长2小时，可提前7天预约'),
(2, '普通会议室', 9,   20,  0, 240, 7,  '9-20人常规会议，免审批，单次最长4小时，可提前7天预约'),
(3, '大型会议室', 21,  100, 1, 240, 14, '21人以上大型会议，需管理员审批，单次最长4小时，可提前14天预约'),
(4, '特殊会议室', 10,  60,  1, 480, 14, '路演厅、多功能厅等受控场地，需管理员审批，单次最长8小时，可提前14天预约');

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
INSERT INTO room_facility (id, room_id, facility_name, quantity, description) VALUES
(1,  1, '投影仪',       1, NULL),
(2,  1, '白板',         1, NULL),
(3,  2, '显示屏',       1, '55寸电视'),
(4,  2, '白板',         1, NULL),
(5,  3, '投影仪',       1, NULL),
(6,  3, '麦克风',       2, '无线手持'),
(7,  4, '投影仪',       1, NULL),
(8,  4, '视频会议设备', 1, NULL),
(9,  5, '投影仪',       2, NULL),
(10, 5, '视频会议设备', 1, NULL),
(11, 5, '无线麦克风',   4, NULL),
(12, 6, '视频会议设备', 1, NULL),
(13, 6, '音响系统',     1, NULL),
(14, 6, '舞台灯光',     1, '路演用，需管理员协助开启');

-- =============================================================================
-- 5. 预约：覆盖全部 5 个状态，同一会议室的 PENDING/CONFIRMED 预约互不重叠
--    room_id=3 B201 : CONFIRMED（普通会议室，免审批直接确认）
--    room_id=5 B502 : PENDING   （大型会议室，待管理员审批）
--    room_id=6 S101 : CONFIRMED + REJECTED（特殊会议室，含审批历史）
--    room_id=1 A301 : CANCELLED（管理员强制取消，带取消原因）
--    room_id=4 B202 : COMPLETED（过去的会议，自然完结）
-- =============================================================================
INSERT INTO reservation (id, reservation_no, room_id, user_id, title, start_time, end_time, participant_count, status, remark, cancel_reason) VALUES
(1, 'RSV20260901001', 3, 2, '项目周会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '11:30:00'),
   12, 'CONFIRMED', '需要投影仪', NULL),
(2, 'RSV20260901002', 5, 3, '全院月度总结会',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '16:00:00'),
   45, 'PENDING', '需提前调试话筒', NULL),
(3, 'RSV20260901003', 6, 2, '新产品内部路演',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00'),
   30, 'CONFIRMED', '需要灯光和音响', NULL),
(4, 'RSV20260901004', 6, 3, '社团招新宣讲',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '14:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '16:00:00'),
   35, 'REJECTED', NULL, NULL),
(5, 'RSV20260901005', 1, 3, '小组讨论',
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '16:00:00'),
   5, 'CANCELLED', NULL, '该时段安排设备检修，管理员强制取消'),
(6, 'RSV20260901006', 4, 2, '迭代评审会',
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '10:00:00'),
   TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '11:30:00'),
   15, 'COMPLETED', NULL, NULL);

-- =============================================================================
-- 6. 审批记录：仅特殊会议室（受控分类）的预约产生
--    id=3 已审批通过；id=4 已驳回；id=2 仍为 PENDING，尚无审批记录
-- =============================================================================
INSERT INTO approval_record (id, reservation_id, approver_id, action, remark) VALUES
(1, 3, 1, 'APPROVE', '场地与设备已确认，同意使用'),
(2, 4, 1, 'REJECT',  '该时段需预留场地维护，建议改期至其他日期');

-- =============================================================================
-- 7. 操作日志：覆盖审批、驳回、建会议室、改分类、强制取消五类关键管理行为
-- =============================================================================
INSERT INTO operation_log (id, user_id, operation_type, business_type, business_id, content, ip_address) VALUES
(1, 1, 'CREATE_ROOM',              'MEETING_ROOM',  5, '新增会议室 B502（阶梯报告厅，大型会议室分类）',        '127.0.0.1'),
(2, 1, 'UPDATE_CATEGORY',          'ROOM_CATEGORY', 4, '调整特殊会议室规则：需审批，单次最长8小时',            '127.0.0.1'),
(3, 1, 'APPROVE_RESERVATION',      'RESERVATION',   3, '审批通过预约 RSV20260901003（新产品内部路演）',        '127.0.0.1'),
(4, 1, 'REJECT_RESERVATION',       'RESERVATION',   4, '驳回预约 RSV20260901004（社团招新宣讲）',              '127.0.0.1'),
(5, 1, 'FORCE_CANCEL_RESERVATION', 'RESERVATION',   5, '强制取消预约 RSV20260901005（小组讨论），原因：设备检修', '127.0.0.1');
