mysqldump: [Warning] Using a password on the command line interface can be insecure.
-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: meeting_room
-- ------------------------------------------------------
-- Server version	8.0.43

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `approval_record`
--

DROP TABLE IF EXISTS `approval_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reservation_id` bigint NOT NULL COMMENT '预约ID',
  `approver_id` bigint NOT NULL COMMENT '审批人（管理员）用户ID',
  `action` varchar(20) NOT NULL COMMENT '审批动作：APPROVE-通过 / REJECT-驳回',
  `remark` varchar(500) DEFAULT NULL COMMENT '审批意见（驳回时由应用层要求必填）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间（审批行为事实只记录在此，不在reservation上冗余approved_at）',
  PRIMARY KEY (`id`),
  KEY `idx_approval_reservation` (`reservation_id`),
  KEY `idx_approval_approver` (`approver_id`),
  KEY `idx_approval_created_at` (`created_at`),
  CONSTRAINT `fk_approval_approver` FOREIGN KEY (`approver_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_approval_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录表（审批行为历史）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `approval_record`
--

LOCK TABLES `approval_record` WRITE;
/*!40000 ALTER TABLE `approval_record` DISABLE KEYS */;
INSERT INTO `approval_record` VALUES (1,3,1,'APPROVE','场地与设备已确认，同意使用','2026-09-21 20:43:16'),(2,4,1,'REJECT','该时段需预留场地维护，建议改期至其他日期','2026-09-21 20:43:16'),(3,13,1,'APPROVE','跨天彩排已确认，注意闭楼时间与用电安全','2026-09-21 20:43:16'),(4,18,4,'APPROVE','全院大会已备案，同意使用阶梯报告厅','2026-09-21 20:43:16'),(5,20,1,'APPROVE','彩排需要灯光音响，已协调设备管理员到场','2026-09-21 20:43:16'),(6,23,4,'APPROVE','学术讲座已审核，同意使用并安排录播','2026-09-21 20:43:16'),(7,29,4,'APPROVE','Approved by lzx for the large-room event','2026-09-21 20:43:16'),(8,30,4,'REJECT','The special room is reserved for another event','2026-09-21 20:43:16'),(9,24,1,'APPROVE','路演场地已确认，同意使用','2026-09-21 20:43:16'),(10,40,1,'REJECT','商业宣讲不属于校内教学科研活动，不予通过','2026-09-21 20:43:16'),(11,41,4,'REJECT','该时段已安排研究生答辩，建议改用其他场地','2026-09-21 20:43:16');
/*!40000 ALTER TABLE `approval_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `booking_window_config`
--

DROP TABLE IF EXISTS `booking_window_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_window_config` (
  `id` tinyint NOT NULL COMMENT 'singleton row; always 1',
  `start_minute` int NOT NULL COMMENT '可预约开始，相对预约日期 00:00 的分钟数（含）',
  `end_minute` int NOT NULL COMMENT '可预约结束，相对预约日期 00:00 的分钟数（含）；超过 1440 表示次日',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_booking_window_range` CHECK (((`start_minute` >= 0) and (`start_minute` < `end_minute`) and (`end_minute` <= 2880))),
  CONSTRAINT `chk_booking_window_singleton` CHECK ((`id` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局可预约时段配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `booking_window_config`
--

LOCK TABLES `booking_window_config` WRITE;
/*!40000 ALTER TABLE `booking_window_config` DISABLE KEYS */;
INSERT INTO `booking_window_config` VALUES (1,480,1920,'2026-09-21 20:43:15');
/*!40000 ALTER TABLE `booking_window_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `department`
--

DROP TABLE IF EXISTS `department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dept_name` varchar(50) NOT NULL COMMENT '部门名称（唯一）',
  `description` varchar(200) DEFAULT NULL COMMENT '部门说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_name` (`dept_name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表（最小组织模型）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `department`
--

LOCK TABLES `department` WRITE;
/*!40000 ALTER TABLE `department` DISABLE KEYS */;
INSERT INTO `department` VALUES (1,'信息中心','校园信息化建设与运维部门','2026-09-21 20:43:15','2026-09-21 20:43:15'),(2,'软件学院','软件工程专业教学单位','2026-09-21 20:43:15','2026-09-21 20:43:15'),(3,'后勤保障处','场地与后勤保障部门','2026-09-21 20:43:15','2026-09-21 20:43:15'),(4,'教务处','教学运行与课程协调部门','2026-09-21 20:43:15','2026-09-21 20:43:15'),(5,'图书馆','文献资源与学习空间管理','2026-09-21 20:43:15','2026-09-21 20:43:15'),(6,'外国语学院','外语教学与交流单位','2026-09-21 20:43:15','2026-09-21 20:43:15');
/*!40000 ALTER TABLE `department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `facility_repair_ticket`
--

DROP TABLE IF EXISTS `facility_repair_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `facility_repair_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `room_id` bigint NOT NULL COMMENT '会议室ID',
  `facility_id` bigint DEFAULT NULL COMMENT '设施ID（空=整室报修）',
  `facility_name` varchar(50) NOT NULL COMMENT '设施名称（报修时快照）',
  `issue` varchar(500) NOT NULL COMMENT '故障描述',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT '工单状态：OPEN/RESOLVED',
  `reporter_id` bigint NOT NULL COMMENT '报修人用户ID',
  `reporter_name` varchar(50) NOT NULL COMMENT '报修人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报修时间',
  `resolved_at` datetime DEFAULT NULL COMMENT '解决时间',
  `resolve_remark` varchar(500) DEFAULT NULL COMMENT '处理说明',
  PRIMARY KEY (`id`),
  KEY `idx_repair_room` (`room_id`),
  KEY `idx_repair_status` (`status`),
  CONSTRAINT `fk_repair_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设施报修工单表（轻量）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `facility_repair_ticket`
--

LOCK TABLES `facility_repair_ticket` WRITE;
/*!40000 ALTER TABLE `facility_repair_ticket` DISABLE KEYS */;
/*!40000 ALTER TABLE `facility_repair_ticket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `meeting_execution`
--

DROP TABLE IF EXISTS `meeting_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meeting_execution` (
  `reservation_id` bigint NOT NULL COMMENT '预约ID（一对一）',
  `actual_start_time` datetime NOT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime NOT NULL COMMENT '实际结束时间',
  `actual_attendee_count` int NOT NULL COMMENT '实际参会人数，可为0',
  `recorded_by` bigint NOT NULL COMMENT '登记人用户ID（组织者或管理员）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次登记时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修正时间',
  PRIMARY KEY (`reservation_id`),
  KEY `idx_execution_recorded_by` (`recorded_by`),
  CONSTRAINT `fk_execution_recorded_by` FOREIGN KEY (`recorded_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_execution_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`),
  CONSTRAINT `chk_execution_attendee_count` CHECK ((`actual_attendee_count` >= 0)),
  CONSTRAINT `chk_execution_period` CHECK ((`actual_end_time` > `actual_start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议实际使用记录（meeting 域）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `meeting_execution`
--

LOCK TABLES `meeting_execution` WRITE;
/*!40000 ALTER TABLE `meeting_execution` DISABLE KEYS */;
INSERT INTO `meeting_execution` VALUES (6,'2026-09-19 10:05:00','2026-09-19 11:20:00',14,2,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(15,'2026-09-20 09:00:00','2026-09-20 09:55:00',6,3,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(16,'2026-09-20 14:10:00','2026-09-20 15:35:00',13,2,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(17,'2026-09-18 09:35:00','2026-09-18 10:20:00',9,5,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(18,'2026-09-17 15:00:00','2026-09-17 16:40:00',52,6,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(19,'2026-09-16 09:10:00','2026-09-16 10:50:00',14,1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(20,'2026-09-15 14:00:00','2026-09-15 16:10:00',30,2,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(22,'2026-09-13 11:00:00','2026-09-13 11:45:00',5,9,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(23,'2026-09-12 14:05:00','2026-09-12 15:50:00',41,3,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(25,'2026-09-20 16:00:00','2026-09-20 16:30:00',3,6,'2026-09-21 20:43:16','2026-09-21 20:43:16');
/*!40000 ALTER TABLE `meeting_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `meeting_room`
--

DROP TABLE IF EXISTS `meeting_room`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meeting_room` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_id` bigint NOT NULL COMMENT '所属分类ID（决定是否需要审批等规则）',
  `room_name` varchar(50) NOT NULL COMMENT '会议室名称（唯一，如A301）',
  `location` varchar(100) DEFAULT NULL COMMENT '位置（楼栋+楼层，如教学楼A栋3层）',
  `capacity` int NOT NULL COMMENT '实际容纳人数（真实资源属性）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '会议室状态：1-可用 0-维护中 2-停用',
  `description` varchar(500) DEFAULT NULL COMMENT '会议室说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_name` (`room_name`),
  KEY `idx_room_category` (`category_id`),
  CONSTRAINT `fk_room_category` FOREIGN KEY (`category_id`) REFERENCES `room_category` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室表（具体资源）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `meeting_room`
--

LOCK TABLES `meeting_room` WRITE;
/*!40000 ALTER TABLE `meeting_room` DISABLE KEYS */;
INSERT INTO `meeting_room` VALUES (1,1,'A301','教学楼A栋3层',8,1,'4-8人圆桌讨论间，配白板','2026-09-21 20:43:16','2026-09-21 20:43:16'),(2,1,'A302','教学楼A栋3层',6,1,'6人小组讨论间','2026-09-21 20:43:16','2026-09-21 20:43:16'),(3,2,'B201','教学楼B栋2层',16,1,'常规投影会议室','2026-09-21 20:43:16','2026-09-21 20:43:16'),(4,2,'B202','教学楼B栋2层',20,1,'支持视频会议的常规会议室','2026-09-21 20:43:16','2026-09-21 20:43:16'),(5,3,'B502','教学楼B栋5层',60,1,'阶梯报告厅，大型会议场地','2026-09-21 20:43:16','2026-09-21 20:43:16'),(6,4,'S101','综合楼1层',40,1,'路演厅，含灯光音响，使用需审批','2026-09-21 20:43:16','2026-09-21 20:43:16'),(7,1,'C101','教学楼C栋1层',8,1,'小型团队讨论室，配投影仪与白板','2026-09-21 20:43:16','2026-09-21 20:43:16'),(8,2,'C201','教学楼C栋2层',18,1,'标准会议室，配视频会议设备','2026-09-21 20:43:16','2026-09-21 20:43:16'),(9,3,'D301','教学楼D栋3层',50,1,'大型阶梯教室，用于院系活动','2026-09-21 20:43:16','2026-09-21 20:43:16'),(10,4,'D401','教学楼D栋4层',35,1,'多功能报告厅','2026-09-21 20:43:16','2026-09-21 20:43:16'),(11,2,'E201','图书馆E栋2层',16,1,'安静型讨论室','2026-09-21 20:43:16','2026-09-21 20:43:16'),(12,2,'E202','图书馆E栋2层',18,1,'图书馆研讨室，配视频会议设备','2026-09-21 20:43:16','2026-09-21 20:43:16'),(13,1,'A303','教学楼A栋3层',8,1,'小型面试与谈话间','2026-09-21 20:43:16','2026-09-21 20:43:16'),(14,3,'B503','教学楼B栋5层',80,1,'大型多功能厅，支持舞台灯光与扩音','2026-09-21 20:43:16','2026-09-21 20:43:16'),(15,2,'B203','教学楼B栋2层',20,0,'维护中：更换投影设备，暂停预约','2026-09-21 20:43:16','2026-09-21 20:43:16'),(16,1,'A304','教学楼A栋3层',8,2,'已停用：改造为教学储物间','2026-09-21 20:43:16','2026-09-21 20:43:16');
/*!40000 ALTER TABLE `meeting_room` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '接收人用户ID',
  `type` varchar(30) NOT NULL COMMENT '通知类型：ATTENDEE_ADDED/ATTENDEE_REMOVED/MEETING_REMINDER/NO_SHOW_MARKED（预留 MEETING_CANCELLED/APPROVAL_RESULT）',
  `title` varchar(100) NOT NULL COMMENT '通知标题',
  `content` varchar(500) NOT NULL COMMENT '通知内容',
  `reservation_id` bigint DEFAULT NULL COMMENT '关联预约ID（可空；当前通知均源自会议执行）',
  `dedup_key` varchar(120) NOT NULL COMMENT '业务幂等键：同一业务事实对同一用户只产生一条通知',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：0-未读 1-已读',
  `read_at` datetime DEFAULT NULL COMMENT '已读时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_dedup` (`user_id`,`dedup_key`),
  KEY `idx_notification_user_read` (`user_id`,`is_read`,`created_at`),
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=323 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人通知表（meeting 域）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notification`
--

LOCK TABLES `notification` WRITE;
/*!40000 ALTER TABLE `notification` DISABLE KEYS */;
INSERT INTO `notification` VALUES (1,2,'RESERVATION_CREATED','预约创建成功','你预约的「每周项目例会（第 1 周）」已创建，时间：A301 三天后 14:00-15:00。',7,'RESERVATION_CREATED:7',0,NULL,'2026-09-21 20:43:16'),(2,5,'RESERVATION_CREATED','预约创建成功','你预约的「图书馆研讨：课程设计」已创建。',26,'RESERVATION_CREATED:26',1,'2026-09-21 18:43:16','2026-09-21 20:43:16'),(3,1,'RESERVATION_PENDING_APPROVAL','有待审批预约','「跨天系统割接演练」等待审批（B502）。',14,'RESERVATION_PENDING_APPROVAL:14',0,NULL,'2026-09-21 20:43:16'),(4,4,'RESERVATION_PENDING_APPROVAL','有待审批预约','「跨天系统割接演练」等待审批（B502）。',14,'RESERVATION_PENDING_APPROVAL:14',1,'2026-09-21 20:13:16','2026-09-21 20:43:16'),(5,1,'RESERVATION_PENDING_APPROVAL','有待审批预约','「社团联合汇演」等待审批（S101）。',36,'RESERVATION_PENDING_APPROVAL:36',0,NULL,'2026-09-21 20:43:16'),(6,4,'RESERVATION_PENDING_APPROVAL','有待审批预约','「社团联合汇演」等待审批（S101）。',36,'RESERVATION_PENDING_APPROVAL:36',0,NULL,'2026-09-21 20:43:16'),(7,1,'RESERVATION_PENDING_APPROVAL','有待审批预约','「研究生开题报告会」等待审批（D301）。',37,'RESERVATION_PENDING_APPROVAL:37',0,NULL,'2026-09-21 20:43:16'),(8,4,'RESERVATION_PENDING_APPROVAL','有待审批预约','「研究生开题报告会」等待审批（D301）。',37,'RESERVATION_PENDING_APPROVAL:37',1,'2026-09-20 20:43:16','2026-09-21 20:43:16'),(9,5,'RESERVATION_APPROVED','预约已通过审批','「跨天场地布置与彩排」已通过审批，请按预约时间使用场地。',13,'RESERVATION_APPROVED:13',1,'2026-09-21 15:43:16','2026-09-21 20:43:16'),(10,6,'RESERVATION_APPROVED','预约已通过审批','「全院教职工大会」已通过审批。',18,'RESERVATION_APPROVED:18',1,'2026-09-17 20:43:16','2026-09-21 20:43:16'),(11,8,'RESERVATION_REJECTED','预约被驳回','「商业宣讲活动」未通过审批：商业宣讲不属于校内教学科研活动。',40,'RESERVATION_REJECTED:40',0,NULL,'2026-09-21 20:43:16'),(12,9,'RESERVATION_REJECTED','预约被驳回','「外部培训占用申请」未通过审批：该时段已安排研究生答辩。',41,'RESERVATION_REJECTED:41',0,NULL,'2026-09-21 20:43:16'),(13,5,'RESERVATION_CANCELLED','预约已取消','「临时取消的评审会」已取消，原因：主讲人行程变更。',42,'RESERVATION_CANCELLED:42',1,'2026-09-21 17:43:16','2026-09-21 20:43:16'),(14,6,'RESERVATION_CANCELLED','预约被管理员取消','「改期后的研讨」已被管理员强制取消，原因：该时段安排设备检修。',43,'RESERVATION_CANCELLED:43',0,NULL,'2026-09-21 20:43:16'),(15,9,'RESERVATION_CANCELLED','预约已取消','「取消的小组会」已取消，原因：参会人数不足。',44,'RESERVATION_CANCELLED:44',0,NULL,'2026-09-21 20:43:16'),(16,6,'ATTENDEE_ADDED','被加入会议','你被加入「图书馆研讨：课程设计」。',26,'ATTENDEE_ADDED:26:6',0,NULL,'2026-09-21 20:43:16'),(17,7,'ATTENDEE_ADDED','被加入会议','你被加入「图书馆研讨：课程设计」。',26,'ATTENDEE_ADDED:26:7',0,NULL,'2026-09-21 20:43:16'),(18,8,'ATTENDEE_ADDED','被加入会议','你被加入「图书馆研讨：课程设计」。',26,'ATTENDEE_ADDED:26:8',1,'2026-09-21 00:43:16','2026-09-21 20:43:16'),(19,5,'ATTENDEE_ADDED','被加入会议','你被加入「跨部门协调会」。',28,'ATTENDEE_ADDED:28:5',0,NULL,'2026-09-21 20:43:16'),(20,6,'ATTENDEE_ADDED','被加入会议','你被加入「跨部门协调会」。',28,'ATTENDEE_ADDED:28:6',0,NULL,'2026-09-21 20:43:16'),(21,5,'ATTENDEE_ADDED','被加入会议','你被加入「通宵调试保障」。',45,'ATTENDEE_ADDED:45:5',0,NULL,'2026-09-21 20:43:16'),(22,6,'ATTENDEE_ADDED','被加入会议','你被加入「通宵调试保障」。',45,'ATTENDEE_ADDED:45:6',0,NULL,'2026-09-21 20:43:16'),(23,10,'ATTENDEE_ADDED','被加入会议','你被加入「通宵调试保障」。',45,'ATTENDEE_ADDED:45:10',0,NULL,'2026-09-21 20:43:16'),(24,8,'ATTENDEE_REMOVED','被移出会议','你已被移出「图书馆研讨：课程设计」。',26,'ATTENDEE_REMOVED:26:8',0,NULL,'2026-09-21 20:43:16'),(25,2,'MEETING_REMINDER','会议即将开始','「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。',7,'MEETING_REMINDER:7:2',0,NULL,'2026-09-21 20:43:16'),(26,3,'MEETING_REMINDER','会议即将开始','「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。',7,'MEETING_REMINDER:7:3',0,NULL,'2026-09-21 20:43:16'),(27,5,'MEETING_REMINDER','会议即将开始','「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。',7,'MEETING_REMINDER:7:5',1,'2026-09-21 20:18:16','2026-09-21 20:43:16'),(28,6,'MEETING_REMINDER','会议即将开始','「每周项目例会（第 1 周）」将在 30 分钟后开始（A301）。',7,'MEETING_REMINDER:7:6',0,NULL,'2026-09-21 20:43:16'),(29,7,'MEETING_REMINDER','会议即将开始','「面试：后端开发实习生」将在 30 分钟后开始（A303）。',27,'MEETING_REMINDER:27:7',0,NULL,'2026-09-21 20:43:16'),(30,9,'NO_SHOW_MARKED','缺席记录','你在「小组讨论：迭代任务拆分」中未签到，已记为缺席。',15,'NO_SHOW_MARKED:15:9',0,NULL,'2026-09-21 20:43:16'),(31,9,'NO_SHOW_MARKED','缺席记录','你在「需求评审会」中未签到，已记为缺席。',16,'NO_SHOW_MARKED:16:9',0,NULL,'2026-09-21 20:43:16'),(32,9,'NO_SHOW_MARKED','缺席记录','你在「新员工入职培训」中未签到，已记为缺席。',19,'NO_SHOW_MARKED:19:9',1,'2026-09-17 20:43:16','2026-09-21 20:43:16'),(33,5,'NO_SHOW_MARKED','缺席记录','你在「学术讲座：分布式系统实践」中未签到，已记为缺席。',23,'NO_SHOW_MARKED:23:5',1,'2026-09-13 20:43:16','2026-09-21 20:43:16'),(34,2,'NO_SHOW_MARKED','缺席记录','会议「维护前最后一次会议」已结束，你未签到，已被记录为 NO_SHOW。',46,'NO_SHOW_MARKED:46:2',0,NULL,'2026-09-21 20:44:12'),(35,6,'NO_SHOW_MARKED','缺席记录','会议「全院教职工大会」已结束，你未签到，已被记录为 NO_SHOW。',18,'NO_SHOW_MARKED:18:6',0,NULL,'2026-09-21 20:44:12'),(36,2,'NO_SHOW_MARKED','缺席记录','会议「迭代评审会」已结束，你未签到，已被记录为 NO_SHOW。',6,'NO_SHOW_MARKED:6:2',0,NULL,'2026-09-21 20:44:12'),(37,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-22T10:00 开始）已确认，请准时参会并签到。',48,'RESERVATION_CREATED:ce49acc7-6641-4131-9ba0-66608e6966e3',0,NULL,'2026-09-21 20:50:35'),(38,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-23T10:00 开始）已确认，请准时参会并签到。',49,'RESERVATION_CREATED:1477ed68-0a9a-4877-8544-b83a06d3b425',0,NULL,'2026-09-21 20:50:35'),(39,3,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A302，2026-09-23T10:00 开始）已确认，请准时参会并签到。',50,'RESERVATION_CREATED:a705d0d2-6b74-4c1c-9ddf-681f05cdcbc7',0,NULL,'2026-09-21 20:50:35'),(40,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（B502，2026-09-22T14:00 开始）已提交，等待管理员审批。',51,'RESERVATION_CREATED:2d23b4eb-a527-41ac-92e0-dcf4d5da211f',0,NULL,'2026-09-21 20:50:35'),(41,1,'RESERVATION_PENDING_APPROVAL','待审批预约','有新的待审批预约「并发集成测试」（B502，2026-09-22T14:00 开始），请及时处理。',51,'RESERVATION_PENDING_APPROVAL:4b90e5ee-66f1-45ab-97de-02d56f88d1ae',0,NULL,'2026-09-21 20:50:35'),(42,4,'RESERVATION_PENDING_APPROVAL','待审批预约','有新的待审批预约「并发集成测试」（B502，2026-09-22T14:00 开始），请及时处理。',51,'RESERVATION_PENDING_APPROVAL:9cf7d548-36fa-49cc-a822-07c2b76df731',0,NULL,'2026-09-21 20:50:35'),(43,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-24T10:00 开始）已确认，请准时参会并签到。',52,'RESERVATION_CREATED:b7015f91-04c9-4bb5-a139-69d70537022f',0,NULL,'2026-09-21 20:50:36'),(44,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-22T14:00 开始）已确认，请准时参会并签到。',53,'RESERVATION_CREATED:87b635f7-6988-49cc-ae35-d8597a3b7296',0,NULL,'2026-09-21 20:50:36'),(45,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-22T16:00 开始）已确认，请准时参会并签到。',54,'RESERVATION_CREATED:2d51c338-a4a4-4720-86fa-1d76ad65be95',0,NULL,'2026-09-21 20:50:36'),(46,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A302，2026-09-22T16:00 开始）已确认，请准时参会并签到。',55,'RESERVATION_CREATED:015051de-8202-424a-bec1-03341bc65cdf',0,NULL,'2026-09-21 20:50:36'),(47,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「test2」（A301，2026-09-22T11:00 开始）已确认，请准时参会并签到。',56,'RESERVATION_CREATED:8cb7cd22-911d-4e85-bc81-374bb2c3bc44',0,NULL,'2026-09-22 09:12:47'),(48,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「test2」（A301，2026-09-29T11:00 开始）已确认，请准时参会并签到。',57,'RESERVATION_CREATED:8fbc46ad-5306-4404-a8d8-01b3c113e65f',0,NULL,'2026-09-22 09:12:47'),(49,2,'MEETING_REMINDER','会议即将开始','「test2」将于 2026-09-22T11:00 在 A301 开始，请按时签到。',56,'MEETING_REMINDER:56:2:2026-09-22T11:00',0,NULL,'2026-09-22 09:15:05'),(50,7,'NO_SHOW_MARKED','缺席记录','会议「面试：后端开发实习生」已结束，你未签到，已被记录为 NO_SHOW。',27,'NO_SHOW_MARKED:27:7',0,NULL,'2026-09-22 09:15:05'),(51,9,'NO_SHOW_MARKED','缺席记录','会议「面试：后端开发实习生」已结束，你未签到，已被记录为 NO_SHOW。',27,'NO_SHOW_MARKED:27:9',0,NULL,'2026-09-22 09:15:05'),(52,10,'NO_SHOW_MARKED','缺席记录','会议「面试：后端开发实习生」已结束，你未签到，已被记录为 NO_SHOW。',27,'NO_SHOW_MARKED:27:10',0,NULL,'2026-09-22 09:15:05'),(63,2,'NO_SHOW_MARKED','缺席记录','会议「项目周会」已结束，你未签到，已被记录为 NO_SHOW。',1,'NO_SHOW_MARKED:1:2',0,NULL,'2026-09-22 09:26:06'),(64,2,'NO_SHOW_MARKED','缺席记录','会议「test2」已结束，你未签到，已被记录为 NO_SHOW。',56,'NO_SHOW_MARKED:56:2',0,NULL,'2026-09-22 09:26:06'),(65,3,'NO_SHOW_MARKED','缺席记录','会议「Cross-team sync」已结束，你未签到，已被记录为 NO_SHOW。',34,'NO_SHOW_MARKED:34:3',0,NULL,'2026-09-22 09:26:06'),(66,2,'NO_SHOW_MARKED','缺席记录','会议「Cross-team sync」已结束，你未签到，已被记录为 NO_SHOW。',34,'NO_SHOW_MARKED:34:2',0,NULL,'2026-09-22 09:26:06'),(67,5,'NO_SHOW_MARKED','缺席记录','会议「Cross-team sync」已结束，你未签到，已被记录为 NO_SHOW。',34,'NO_SHOW_MARKED:34:5',0,NULL,'2026-09-22 09:26:06'),(68,5,'NO_SHOW_MARKED','缺席记录','会议「图书馆研讨：课程设计」已结束，你未签到，已被记录为 NO_SHOW。',26,'NO_SHOW_MARKED:26:5',0,NULL,'2026-09-22 09:26:06'),(69,6,'NO_SHOW_MARKED','缺席记录','会议「图书馆研讨：课程设计」已结束，你未签到，已被记录为 NO_SHOW。',26,'NO_SHOW_MARKED:26:6',0,NULL,'2026-09-22 09:26:06'),(70,7,'NO_SHOW_MARKED','缺席记录','会议「图书馆研讨：课程设计」已结束，你未签到，已被记录为 NO_SHOW。',26,'NO_SHOW_MARKED:26:7',0,NULL,'2026-09-22 09:26:06'),(71,8,'NO_SHOW_MARKED','缺席记录','会议「图书馆研讨：课程设计」已结束，你未签到，已被记录为 NO_SHOW。',26,'NO_SHOW_MARKED:26:8',0,NULL,'2026-09-22 09:26:06'),(78,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-23T10:00 开始）已确认，请准时参会并签到。',58,'RESERVATION_CREATED:7c65c8ba-fca5-4ce2-bb7f-76e7e165b284',0,NULL,'2026-09-22 09:35:08'),(79,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-24T10:00 开始）已确认，请准时参会并签到。',59,'RESERVATION_CREATED:2e792eba-2dc3-4762-96f9-ac3942b76680',0,NULL,'2026-09-22 09:35:08'),(80,3,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A302，2026-09-24T10:00 开始）已确认，请准时参会并签到。',60,'RESERVATION_CREATED:8f81db2b-ae55-448b-b57e-003780c9e768',0,NULL,'2026-09-22 09:35:08'),(81,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（B502，2026-09-24T14:00 开始）已提交，等待管理员审批。',61,'RESERVATION_CREATED:1fb5277a-8080-48ef-bb07-8ab40907ade9',0,NULL,'2026-09-22 09:35:08'),(82,1,'RESERVATION_PENDING_APPROVAL','待审批预约','有新的待审批预约「并发集成测试」（B502，2026-09-24T14:00 开始），请及时处理。',61,'RESERVATION_PENDING_APPROVAL:9fda6376-71a7-40ab-b6a5-1956a866ab44',0,NULL,'2026-09-22 09:35:08'),(83,4,'RESERVATION_PENDING_APPROVAL','待审批预约','有新的待审批预约「并发集成测试」（B502，2026-09-24T14:00 开始），请及时处理。',61,'RESERVATION_PENDING_APPROVAL:1936c116-de88-477d-825a-d2923a5bea90',0,NULL,'2026-09-22 09:35:08'),(84,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-25T10:00 开始）已确认，请准时参会并签到。',62,'RESERVATION_CREATED:2d3676a7-23fd-4113-b119-259e2ce5a816',0,NULL,'2026-09-22 09:35:08'),(85,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-23T14:00 开始）已确认，请准时参会并签到。',63,'RESERVATION_CREATED:7d3e3f84-ed91-4226-85d2-630a83e37e0f',0,NULL,'2026-09-22 09:35:08'),(86,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A302，2026-09-23T16:00 开始）已确认，请准时参会并签到。',64,'RESERVATION_CREATED:b65a33d9-ca94-48f8-9d6b-db1970505679',0,NULL,'2026-09-22 09:35:08'),(87,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「并发集成测试」（A301，2026-09-23T16:00 开始）已确认，请准时参会并签到。',65,'RESERVATION_CREATED:c38a8a8a-b21c-4143-8746-6c101651b5ee',0,NULL,'2026-09-22 09:35:08'),(88,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「Concurrency baseline」（A301，2026-09-23T10:00 开始）已确认，请准时参会并签到。',66,'RESERVATION_CREATED:41329d09-7800-46a0-90bd-08d7a2849b74',0,NULL,'2026-09-22 09:35:51'),(96,2,'NO_SHOW_MARKED','缺席记录','会议「Concurrency baseline」已结束，你未签到，已被记录为 NO_SHOW。',66,'NO_SHOW_MARKED:66:2',0,NULL,'2026-09-22 09:44:11'),(97,3,'ATTENDEE_ADDED','被加入会议','你被加入会议「smoke-with-attendees」（A301 2026-09-24T09:00 开始），请按时签到。',67,'ATTENDEE_ADDED:6add0422-8ccf-40c7-838d-0186de8e5bb7',0,NULL,'2026-09-22 09:45:01'),(98,4,'ATTENDEE_ADDED','被加入会议','你被加入会议「smoke-with-attendees」（A301 2026-09-24T09:00 开始），请按时签到。',67,'ATTENDEE_ADDED:11caa07e-f3f1-4a4e-a1c7-80e2a37e0d27',0,NULL,'2026-09-22 09:45:01'),(99,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「smoke-with-attendees」（A301，2026-09-24T09:00 开始）已确认，请准时参会并签到。',67,'RESERVATION_CREATED:07ba51cb-bfa0-4764-99a0-05cc1468f510',0,NULL,'2026-09-22 09:45:01'),(100,2,'MEETING_REMINDER','会议即将开始','「smoke-with-attendees」将于 2026-09-24T09:00 在 A301 开始，请按时签到。',67,'MEETING_REMINDER:67:2:2026-09-24T09:00',0,NULL,'2026-09-22 09:46:11'),(101,3,'MEETING_REMINDER','会议即将开始','「smoke-with-attendees」将于 2026-09-24T09:00 在 A301 开始，请按时签到。',67,'MEETING_REMINDER:67:3:2026-09-24T09:00',0,NULL,'2026-09-22 09:46:11'),(102,4,'MEETING_REMINDER','会议即将开始','「smoke-with-attendees」将于 2026-09-24T09:00 在 A301 开始，请按时签到。',67,'MEETING_REMINDER:67:4:2026-09-24T09:00',0,NULL,'2026-09-22 09:46:11'),(103,2,'MEETING_REMINDER','会议即将开始','「新产品内部路演」将于 2026-09-24T09:00 在 S101 开始，请按时签到。',3,'MEETING_REMINDER:3:2:2026-09-24T09:00',0,NULL,'2026-09-22 09:46:11'),(104,9,'NO_SHOW_MARKED','缺席记录','会议「跨部门协调会」已结束，你未签到，已被记录为 NO_SHOW。',28,'NO_SHOW_MARKED:28:9',0,NULL,'2026-09-22 09:46:11'),(105,5,'NO_SHOW_MARKED','缺席记录','会议「跨部门协调会」已结束，你未签到，已被记录为 NO_SHOW。',28,'NO_SHOW_MARKED:28:5',0,NULL,'2026-09-22 09:46:11'),(106,6,'NO_SHOW_MARKED','缺席记录','会议「跨部门协调会」已结束，你未签到，已被记录为 NO_SHOW。',28,'NO_SHOW_MARKED:28:6',0,NULL,'2026-09-22 09:46:11'),(107,7,'NO_SHOW_MARKED','缺席记录','会议「通宵调试保障」已结束，你未签到，已被记录为 NO_SHOW。',45,'NO_SHOW_MARKED:45:7',0,NULL,'2026-09-22 09:46:11'),(108,5,'NO_SHOW_MARKED','缺席记录','会议「通宵调试保障」已结束，你未签到，已被记录为 NO_SHOW。',45,'NO_SHOW_MARKED:45:5',0,NULL,'2026-09-22 09:46:11'),(109,6,'NO_SHOW_MARKED','缺席记录','会议「通宵调试保障」已结束，你未签到，已被记录为 NO_SHOW。',45,'NO_SHOW_MARKED:45:6',0,NULL,'2026-09-22 09:46:11'),(110,10,'NO_SHOW_MARKED','缺席记录','会议「通宵调试保障」已结束，你未签到，已被记录为 NO_SHOW。',45,'NO_SHOW_MARKED:45:10',0,NULL,'2026-09-22 09:46:11'),(111,3,'RESERVATION_REJECTED','预约被驳回','你的预约「全院月度总结会」未通过审批，原因：审批超时：预约时段已结束仍未审批，系统自动驳回。',2,'RESERVATION_REJECTED:4a0b9672-aa0b-4955-8d20-c14bffd2527a',0,NULL,'2026-09-22 09:46:26'),(150,3,'ATTENDEE_ADDED','被加入会议','你被加入会议「冒烟-界面选人」（A301 2026-09-24T11:00 开始），请按时签到。',68,'ATTENDEE_ADDED:6feb1cda-fb16-4d5e-ae0d-11afb1807696',0,NULL,'2026-09-22 09:58:36'),(152,6,'ATTENDEE_ADDED','被加入会议','你被加入会议「冒烟-界面选人」（A301 2026-09-24T11:00 开始），请按时签到。',68,'ATTENDEE_ADDED:3e8d0c0d-001f-493e-821d-0068b606bc78',0,NULL,'2026-09-22 09:58:36'),(154,2,'RESERVATION_CREATED','预约创建成功','你创建的预约「冒烟-界面选人」（A301，2026-09-24T11:00 开始）已确认，请准时参会并签到。',68,'RESERVATION_CREATED:3c573425-08b1-4ffd-9f7c-b7b52cd57292',0,NULL,'2026-09-22 09:58:36'),(161,2,'RESERVATION_CANCELLED','预约已取消','你的预约「smoke-with-attendees」已取消。',67,'RESERVATION_CANCELLED:9c9f7f07-b255-48f3-b485-0dcff503ac04',0,NULL,'2026-09-22 09:59:59'),(162,2,'RESERVATION_CANCELLED','预约已取消','你的预约「冒烟-界面选人」已取消。',68,'RESERVATION_CANCELLED:11e7334b-4d42-4890-9558-08fcb6d9f075',0,NULL,'2026-09-22 09:59:59'),(163,2,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-09-22T12:00 开始），请按时签到。',69,'ATTENDEE_ADDED:00c8f201-eb83-41e4-bef4-5e6979646bb4',0,NULL,'2026-09-22 11:19:45'),(164,3,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-09-22T12:00 开始），请按时签到。',69,'ATTENDEE_ADDED:56c285b0-3293-440f-ae94-fafd1a4d91aa',0,NULL,'2026-09-22 11:19:45'),(165,1,'RESERVATION_CREATED','预约创建成功','你创建的预约「t」（A301，2026-09-22T12:00 开始）已确认，请准时参会并签到。',69,'RESERVATION_CREATED:85bcfe62-484c-4bcf-bb56-02b7ffbfb625',0,NULL,'2026-09-22 11:19:45'),(166,2,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-09-29T12:00 开始），请按时签到。',70,'ATTENDEE_ADDED:d65e81cd-7fe7-4cb3-80a7-8aaea82486ae',0,NULL,'2026-09-22 11:19:45'),(167,3,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-09-29T12:00 开始），请按时签到。',70,'ATTENDEE_ADDED:7be98eae-0e85-489b-8d86-15458264805c',0,NULL,'2026-09-22 11:19:45'),(168,1,'RESERVATION_CREATED','预约创建成功','你创建的预约「t」（A301，2026-09-29T12:00 开始）已确认，请准时参会并签到。',70,'RESERVATION_CREATED:b20cc777-3e84-4c50-a783-b4eb88f876bf',0,NULL,'2026-09-22 11:19:45'),(169,2,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-10-06T12:00 开始），请按时签到。',71,'ATTENDEE_ADDED:304798eb-91b6-448d-8cc5-ba9b62db803a',0,NULL,'2026-09-22 11:19:45'),(170,3,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-10-06T12:00 开始），请按时签到。',71,'ATTENDEE_ADDED:cc73029d-5aef-4f27-ac38-1d93663d78d2',0,NULL,'2026-09-22 11:19:45'),(171,1,'RESERVATION_CREATED','预约创建成功','你创建的预约「t」（A301，2026-10-06T12:00 开始）已确认，请准时参会并签到。',71,'RESERVATION_CREATED:d13e5a61-7473-4294-b7ec-b51e71abd582',0,NULL,'2026-09-22 11:19:45'),(172,2,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-10-13T12:00 开始），请按时签到。',72,'ATTENDEE_ADDED:e82678a4-e646-48bf-b45e-65ad1a81f4f7',0,NULL,'2026-09-22 11:19:45'),(173,3,'ATTENDEE_ADDED','被加入会议','你被加入会议「t」（A301 2026-10-13T12:00 开始），请按时签到。',72,'ATTENDEE_ADDED:1512eead-740d-4aba-953a-4cdf01daa632',0,NULL,'2026-09-22 11:19:45'),(174,1,'RESERVATION_CREATED','预约创建成功','你创建的预约「t」（A301，2026-10-13T12:00 开始）已确认，请准时参会并签到。',72,'RESERVATION_CREATED:7e15a4f6-5e43-4b58-8d7b-ecf0390fe844',0,NULL,'2026-09-22 11:19:45'),(175,1,'MEETING_REMINDER','会议即将开始','「t」将于 2026-09-22T12:00 在 A301 开始，请按时签到。',69,'MEETING_REMINDER:69:1:2026-09-22T12:00',0,NULL,'2026-09-22 11:30:42'),(176,2,'MEETING_REMINDER','会议即将开始','「t」将于 2026-09-22T12:00 在 A301 开始，请按时签到。',69,'MEETING_REMINDER:69:2:2026-09-22T12:00',0,NULL,'2026-09-22 11:30:42'),(177,3,'MEETING_REMINDER','会议即将开始','「t」将于 2026-09-22T12:00 在 A301 开始，请按时签到。',69,'MEETING_REMINDER:69:3:2026-09-22T12:00',0,NULL,'2026-09-22 11:30:42'),(265,4,'ATTENDEE_ADDED','被加入会议','你被加入会议「1q」（A302 2026-09-22T12:30 开始），请按时签到。',73,'ATTENDEE_ADDED:33355f73-32a1-496b-8e51-9feb20addf38',0,NULL,'2026-09-22 12:12:21'),(266,3,'ATTENDEE_ADDED','被加入会议','你被加入会议「1q」（A302 2026-09-22T12:30 开始），请按时签到。',73,'ATTENDEE_ADDED:e858d738-29c2-4b08-9c31-6c0b76e76ba3',0,NULL,'2026-09-22 12:12:21'),(267,1,'RESERVATION_CREATED','预约创建成功','你创建的预约「1q」（A302，2026-09-22T12:30 开始）已确认，请准时参会并签到。',73,'RESERVATION_CREATED:2fa71edb-1578-4d2e-80d6-2a82d7167bf0',0,NULL,'2026-09-22 12:12:21'),(268,1,'MEETING_REMINDER','会议即将开始','「1q」将于 2026-09-22T12:30 在 A302 开始，请按时签到。',73,'MEETING_REMINDER:73:1:2026-09-22T12:30',0,NULL,'2026-09-22 12:12:44'),(269,4,'MEETING_REMINDER','会议即将开始','「1q」将于 2026-09-22T12:30 在 A302 开始，请按时签到。',73,'MEETING_REMINDER:73:4:2026-09-22T12:30',0,NULL,'2026-09-22 12:12:44'),(270,3,'MEETING_REMINDER','会议即将开始','「1q」将于 2026-09-22T12:30 在 A302 开始，请按时签到。',73,'MEETING_REMINDER:73:3:2026-09-22T12:30',0,NULL,'2026-09-22 12:12:44'),(289,1,'RESERVATION_CREATED','预约创建成功','你创建的预约「1」（A302，2026-09-28T14:45 开始）已确认，请准时参会并签到。',74,'RESERVATION_CREATED:d3cfd232-1d5f-4fd1-bd03-8d4d0b64cb20',0,NULL,'2026-09-22 12:18:56');
/*!40000 ALTER TABLE `notification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `operation_log`
--

DROP TABLE IF EXISTS `operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '操作人用户ID',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型：APPROVE_RESERVATION/REJECT_RESERVATION/FORCE_CANCEL_RESERVATION/CREATE_ROOM/UPDATE_ROOM/UPDATE_CATEGORY等',
  `business_type` varchar(50) NOT NULL COMMENT '业务类型：RESERVATION/MEETING_ROOM/ROOM_CATEGORY',
  `business_id` bigint NOT NULL COMMENT '关联业务ID（预约ID/会议室ID/分类ID）',
  `content` varchar(500) NOT NULL COMMENT '操作内容描述',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '操作IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_log_user` (`user_id`),
  KEY `idx_log_business` (`business_type`,`business_id`),
  KEY `idx_operation_business_created` (`business_type`,`created_at`),
  KEY `idx_operation_user_created` (`user_id`,`created_at`),
  CONSTRAINT `fk_log_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表（关键管理行为）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `operation_log`
--

LOCK TABLES `operation_log` WRITE;
/*!40000 ALTER TABLE `operation_log` DISABLE KEYS */;
INSERT INTO `operation_log` VALUES (1,1,'CREATE_ROOM','MEETING_ROOM',5,'新增会议室 B502（阶梯报告厅，大型会议室分类）','127.0.0.1','2026-09-21 20:43:16'),(2,1,'UPDATE_CATEGORY','ROOM_CATEGORY',4,'调整特殊会议室规则：需审批，单次最长8小时','127.0.0.1','2026-09-21 20:43:16'),(3,1,'APPROVE_RESERVATION','RESERVATION',3,'审批通过预约 RSV20260901003（新产品内部路演）','127.0.0.1','2026-09-21 20:43:16'),(4,1,'REJECT_RESERVATION','RESERVATION',4,'驳回预约 RSV20260901004（社团招新宣讲）','127.0.0.1','2026-09-21 20:43:16'),(5,1,'FORCE_CANCEL_RESERVATION','RESERVATION',5,'强制取消预约 RSV20260901005（小组讨论），原因：设备检修','127.0.0.1','2026-09-21 20:43:16'),(11,4,'CREATE_ROOM','MEETING_ROOM',7,'新增会议室 C101（小型团队讨论室）','127.0.0.1','2026-09-21 20:43:16'),(12,4,'CREATE_ROOM','MEETING_ROOM',8,'新增会议室 C201（标准会议室）','127.0.0.1','2026-09-21 20:43:16'),(13,4,'CREATE_ROOM','MEETING_ROOM',9,'新增会议室 D301（大型阶梯教室）','127.0.0.1','2026-09-21 20:43:16'),(14,4,'CREATE_ROOM','MEETING_ROOM',10,'新增会议室 D401（多功能报告厅）','127.0.0.1','2026-09-21 20:43:16'),(15,4,'APPROVE_RESERVATION','RESERVATION',29,'审批通过预约 RSV20260917029（Department annual review）','127.0.0.1','2026-09-21 20:43:16'),(16,4,'REJECT_RESERVATION','RESERVATION',30,'驳回预约 RSV20260917030（Product launch rehearsal）','127.0.0.1','2026-09-21 20:43:16'),(17,1,'CREATE_ROOM','MEETING_ROOM',12,'新增会议室 E202（图书馆研讨室，配视频会议设备）','127.0.0.1','2026-09-21 20:43:16'),(18,1,'CREATE_ROOM','MEETING_ROOM',13,'新增会议室 A303（小型面试与谈话间）','127.0.0.1','2026-09-21 20:43:16'),(19,1,'CREATE_ROOM','MEETING_ROOM',14,'新增会议室 B503（大型多功能厅，支持舞台灯光与扩音）','127.0.0.1','2026-09-21 20:43:16'),(20,1,'UPDATE_ROOM','MEETING_ROOM',15,'将会议室 B203 状态置为维护中：更换投影设备','127.0.0.1','2026-09-21 20:43:16'),(21,1,'UPDATE_ROOM','MEETING_ROOM',16,'将会议室 A304 状态置为停用：改造为教学储物间','127.0.0.1','2026-09-21 20:43:16'),(22,1,'UPDATE_CATEGORY','ROOM_CATEGORY',3,'调整大型会议室规则：需管理员审批，可提前14天预约','127.0.0.1','2026-09-21 20:43:16'),(23,1,'APPROVE_RESERVATION','RESERVATION',13,'审批通过预约 RSV20260901013（跨天场地布置与彩排）','127.0.0.1','2026-09-21 20:43:16'),(24,4,'APPROVE_RESERVATION','RESERVATION',18,'审批通过预约 RSV20260901018（全院教职工大会）','127.0.0.1','2026-09-21 20:43:16'),(25,1,'APPROVE_RESERVATION','RESERVATION',20,'审批通过预约 RSV20260901020（产品发布会彩排）','127.0.0.1','2026-09-21 20:43:16'),(26,4,'APPROVE_RESERVATION','RESERVATION',23,'审批通过预约 RSV20260901023（学术讲座：分布式系统实践）','127.0.0.1','2026-09-21 20:43:16'),(27,1,'APPROVE_RESERVATION','RESERVATION',24,'审批通过预约 RSV20260901024（创新项目路演）','127.0.0.1','2026-09-21 20:43:16'),(28,1,'REJECT_RESERVATION','RESERVATION',40,'驳回预约 RSV20260917040（商业宣讲活动），原因：非校内教学科研活动','127.0.0.1','2026-09-21 20:43:16'),(29,4,'REJECT_RESERVATION','RESERVATION',41,'驳回预约 RSV20260917041（外部培训占用申请），原因：时段冲突','127.0.0.1','2026-09-21 20:43:16'),(30,1,'FORCE_CANCEL_RESERVATION','RESERVATION',43,'强制取消预约 RSV20260917043（改期后的研讨），原因：设备检修','127.0.0.1','2026-09-21 20:43:16');
/*!40000 ALTER TABLE `operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reservation`
--

DROP TABLE IF EXISTS `reservation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `request_id` varchar(100) NOT NULL COMMENT '客户端逻辑请求ID（幂等键）',
  `reservation_no` varchar(32) NOT NULL COMMENT '预约业务单号（服务端按 RSV+日期+序号 生成，用于展示与追溯）',
  `room_id` bigint NOT NULL COMMENT '会议室ID',
  `user_id` bigint NOT NULL COMMENT '预约人用户ID',
  `title` varchar(100) NOT NULL COMMENT '会议主题',
  `start_time` datetime NOT NULL COMMENT '预约开始时间',
  `end_time` datetime NOT NULL COMMENT '预约结束时间（必须晚于开始时间，见chk_reservation_period）',
  `participant_count` int NOT NULL DEFAULT '1' COMMENT '参与人数（校验对象是会议室实际容量 capacity）',
  `status` varchar(20) NOT NULL COMMENT '状态：PENDING / CONFIRMED / REJECTED / CANCELLED（见chk_reservation_status）；PENDING与CONFIRMED占用时间段参与冲突检测',
  `remark` varchar(500) DEFAULT NULL COMMENT '预约备注（申请人填写）',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因（仅用户取消或管理员强制取消时填写；审批驳回理由写 approval_record.remark）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本；每次预约写操作递增',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_user_request` (`user_id`,`request_id`),
  UNIQUE KEY `uk_reservation_no` (`reservation_no`),
  KEY `idx_reservation_room_status_start` (`room_id`,`status`,`start_time`),
  KEY `idx_reservation_user_start` (`user_id`,`start_time`),
  KEY `idx_reservation_status_period` (`status`,`start_time`,`end_time`),
  CONSTRAINT `fk_reservation_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`),
  CONSTRAINT `fk_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_reservation_period` CHECK ((`end_time` > `start_time`)),
  CONSTRAINT `chk_reservation_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'CONFIRMED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约表（核心业务表）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reservation`
--

LOCK TABLES `reservation` WRITE;
/*!40000 ALTER TABLE `reservation` DISABLE KEYS */;
INSERT INTO `reservation` VALUES (1,'seed-rsv-1','RSV20260901001',3,2,'项目周会','2026-09-22 10:00:00','2026-09-22 11:30:00',12,'CONFIRMED','需要投影仪',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(2,'seed-rsv-2','RSV20260901002',5,3,'全院月度总结会','2026-09-23 14:00:00','2026-09-23 16:00:00',45,'REJECTED','需提前调试话筒',NULL,1,'2026-09-21 20:43:16','2026-09-22 09:46:26'),(3,'seed-rsv-3','RSV20260901003',6,2,'新产品内部路演','2026-09-24 09:00:00','2026-09-24 11:00:00',30,'CONFIRMED','需要灯光和音响',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(4,'seed-rsv-4','RSV20260901004',6,3,'社团招新宣讲','2026-09-24 14:00:00','2026-09-24 16:00:00',35,'REJECTED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(5,'seed-rsv-5','RSV20260901005',1,3,'小组讨论','2026-09-22 15:00:00','2026-09-22 16:00:00',5,'CANCELLED',NULL,'该时段安排设备检修，管理员强制取消',0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(6,'seed-rsv-6','RSV20260901006',4,2,'迭代评审会','2026-09-19 10:00:00','2026-09-19 11:30:00',15,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(7,'seed-rsv-7','RSV20260901007',1,2,'每周项目例会（第 1 周）','2026-09-24 14:00:00','2026-09-24 15:00:00',8,'CONFIRMED','周期性会议，连续 6 周',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(8,'seed-rsv-8','RSV20260901008',1,2,'每周项目例会（第 2 周）','2026-10-01 14:00:00','2026-10-01 15:00:00',8,'CONFIRMED','周期性会议，连续 6 周',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(9,'seed-rsv-9','RSV20260901009',1,2,'每周项目例会（第 3 周）','2026-10-08 14:00:00','2026-10-08 15:00:00',8,'CONFIRMED','周期性会议，连续 6 周',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(10,'seed-rsv-10','RSV20260901010',1,2,'每周项目例会（第 4 周）','2026-10-15 14:00:00','2026-10-15 15:00:00',8,'CONFIRMED','周期性会议，连续 6 周',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(11,'seed-rsv-11','RSV20260901011',1,2,'每周项目例会（第 5 周）','2026-10-22 14:00:00','2026-10-22 15:00:00',8,'CONFIRMED','周期性会议，连续 6 周',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(12,'seed-rsv-12','RSV20260901012',1,2,'每周项目例会（第 6 周）','2026-10-29 14:00:00','2026-10-29 15:00:00',8,'CONFIRMED','周期性会议，连续 6 周',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(13,'seed-rsv-13','RSV20260901013',14,5,'跨天场地布置与彩排','2026-09-25 22:00:00','2026-09-26 01:00:00',12,'CONFIRMED','跨天使用，结束时间落在次日',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(14,'seed-rsv-14','RSV20260901014',5,6,'跨天系统割接演练','2026-09-30 23:00:00','2026-10-01 03:00:00',30,'PENDING','跨天演练，需管理员审批后执行',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(15,'seed-rsv-15','RSV20260901015',1,3,'小组讨论：迭代任务拆分','2026-09-20 09:00:00','2026-09-20 10:00:00',6,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(16,'seed-rsv-16','RSV20260901016',3,2,'需求评审会','2026-09-20 14:00:00','2026-09-20 15:30:00',14,'CONFIRMED','评审通过后进入开发',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(17,'seed-rsv-17','RSV20260901017',4,5,'合作方视频对接','2026-09-18 09:30:00','2026-09-18 10:30:00',10,'CONFIRMED','需开启视频会议设备',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(18,'seed-rsv-18','RSV20260901018',5,6,'全院教职工大会','2026-09-17 15:00:00','2026-09-17 17:00:00',55,'CONFIRMED','需扩音与投影',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(19,'seed-rsv-19','RSV20260901019',8,7,'新员工入职培训','2026-09-16 09:00:00','2026-09-16 11:00:00',15,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(20,'seed-rsv-20','RSV20260901020',6,2,'产品发布会彩排','2026-09-15 14:00:00','2026-09-15 16:00:00',28,'CONFIRMED','需要灯光和音响保障',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(21,'seed-rsv-21','RSV20260901021',11,8,'读书分享会','2026-09-14 16:00:00','2026-09-14 17:30:00',12,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(22,'seed-rsv-22','RSV20260901022',2,9,'小组周会','2026-09-13 11:00:00','2026-09-13 12:00:00',5,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(23,'seed-rsv-23','RSV20260901023',9,3,'学术讲座：分布式系统实践','2026-09-12 14:00:00','2026-09-12 16:00:00',45,'CONFIRMED','需录播',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(24,'seed-rsv-24','RSV20260901024',10,5,'创新项目路演','2026-09-11 10:00:00','2026-09-11 12:00:00',30,'CONFIRMED','需要舞台灯光',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(25,'seed-rsv-25','RSV20260901025',7,6,'一对一沟通','2026-09-20 16:00:00','2026-09-20 17:00:00',3,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(26,'seed-rsv-26','RSV20260901026',12,5,'图书馆研讨：课程设计','2026-09-23 10:00:00','2026-09-23 12:00:00',12,'CONFIRMED','需要视频会议设备',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(27,'seed-rsv-27','RSV20260901027',13,7,'面试：后端开发实习生','2026-09-22 09:00:00','2026-09-22 10:30:00',4,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(28,'seed-rsv-28','RSV20260901028',3,9,'跨部门协调会','2026-09-23 16:00:00','2026-09-23 17:00:00',15,'CONFIRMED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(29,'seed-extra-rsv-29','RSV20260917029',9,3,'Department annual review','2026-09-24 13:00:00','2026-09-24 15:00:00',40,'CONFIRMED','Approved large-room event',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(30,'seed-extra-rsv-30','RSV20260917030',10,2,'Product launch rehearsal','2026-09-25 14:00:00','2026-09-25 16:00:00',25,'REJECTED','Waiting for a different venue',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(31,'seed-extra-rsv-31','RSV20260917031',11,3,'Study group discussion','2026-09-26 10:00:00','2026-09-26 12:00:00',10,'CONFIRMED','Regular discussion',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(32,'seed-extra-rsv-32','RSV20260917032',8,4,'Administrator coordination meeting','2026-09-27 15:00:00','2026-09-27 16:00:00',12,'CONFIRMED','Internal administration meeting',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(33,'seed-extra-rsv-33','RSV20260917033',7,2,'Team planning session','2026-09-23 09:00:00','2026-09-23 10:00:00',6,'CANCELLED',NULL,'Schedule changed by the organizer',0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(34,'seed-extra-rsv-34','RSV20260917034',8,3,'Cross-team sync','2026-09-22 14:00:00','2026-09-22 16:00:00',12,'CONFIRMED','Weekly cross-team sync',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(35,'seed-extra-rsv-35','RSV20260917035',9,4,'Public lecture preparation','2026-09-28 10:00:00','2026-09-28 12:00:00',30,'PENDING','Awaiting administrator approval',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(36,'seed-rsv-36','RSV20260917036',6,5,'社团联合汇演','2026-09-27 19:00:00','2026-09-27 21:00:00',38,'PENDING','需要舞台灯光与音响',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(37,'seed-rsv-37','RSV20260917037',9,6,'研究生开题报告会','2026-09-29 09:00:00','2026-09-29 12:00:00',40,'PENDING','需要录播与话筒',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(38,'seed-rsv-38','RSV20260917038',5,10,'年度表彰大会','2026-10-02 14:00:00','2026-10-02 17:00:00',50,'PENDING','需提前布置会场',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(39,'seed-rsv-39','RSV20260917039',14,7,'迎新晚会彩排','2026-10-03 18:00:00','2026-10-03 21:00:00',60,'PENDING','需舞台灯光与调音台',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(40,'seed-rsv-40','RSV20260917040',6,8,'商业宣讲活动','2026-09-26 17:00:00','2026-09-26 19:00:00',30,'REJECTED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(41,'seed-rsv-41','RSV20260917041',9,9,'外部培训占用申请','2026-09-27 13:00:00','2026-09-27 15:00:00',35,'REJECTED',NULL,NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(42,'seed-rsv-42','RSV20260917042',3,5,'临时取消的评审会','2026-09-25 15:00:00','2026-09-25 16:00:00',10,'CANCELLED',NULL,'主讲人行程变更，用户主动取消',0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(43,'seed-rsv-43','RSV20260917043',12,6,'改期后的研讨','2026-09-24 14:00:00','2026-09-24 15:00:00',8,'CANCELLED',NULL,'该时段安排设备检修，管理员强制取消',0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(44,'seed-rsv-44','RSV20260917044',2,9,'取消的小组会','2026-09-26 09:00:00','2026-09-26 10:00:00',4,'CANCELLED',NULL,'参会人数不足，用户主动取消',0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(45,'seed-rsv-45','RSV20260917045',4,7,'通宵调试保障','2026-09-23 20:00:00','2026-09-24 02:00:00',8,'CONFIRMED','跨天使用，结束时间落在次日',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(46,'seed-rsv-46','RSV20260917046',15,2,'维护前最后一次会议','2026-09-15 10:00:00','2026-09-15 11:00:00',12,'CONFIRMED','该会议室随后进入维护状态',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(47,'seed-rsv-47','RSV20260917047',16,3,'停用前的讨论会','2026-09-09 15:00:00','2026-09-09 16:00:00',6,'CONFIRMED','该会议室随后停用',NULL,0,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(56,'212cbebb-dc76-464f-b15b-2cbc1a468d49','RSVed076eb1a55246e6bddb',1,2,'test2','2026-09-22 11:00:00','2026-09-22 12:00:00',4,'CONFIRMED','无',NULL,0,'2026-09-22 09:12:47','2026-09-22 09:12:47'),(57,'212cbebb-dc76-464f-b15b-2cbc1a468d49~w1','RSV783cc273117d4739b282',1,2,'test2','2026-09-29 11:00:00','2026-09-29 12:00:00',4,'CONFIRMED','无',NULL,0,'2026-09-22 09:12:47','2026-09-22 09:12:47'),(66,'concurrency-1-2a83b2e0-7a6d-468f-b19b-1d03e51c5802-13','RSV05a7809dd48542aab126',1,2,'Concurrency baseline','2026-09-23 10:00:00','2026-09-23 11:00:00',1,'CONFIRMED','repeatable concurrency test',NULL,0,'2026-09-22 09:35:51','2026-09-22 09:35:51'),(67,'smoke-att-007','RSVf25ae6022b30417b8c67',1,2,'smoke-with-attendees','2026-09-24 09:00:00','2026-09-24 10:00:00',4,'CANCELLED',NULL,'smoke cleanup',1,'2026-09-22 09:45:01','2026-09-22 09:59:59'),(68,'0918fd00-e9ec-432a-8b01-1c8fd85436b8','RSV1c6f898517b148a08870',1,2,'冒烟-界面选人','2026-09-24 11:00:00','2026-09-24 12:00:00',4,'CANCELLED','','smoke cleanup',1,'2026-09-22 09:58:36','2026-09-22 09:59:59'),(69,'1c14e6a8-c62b-44bd-a2ea-9bda5e5a4ee0','RSV86454bd73adb41869ed5',1,1,'t','2026-09-22 12:00:00','2026-09-22 13:00:00',4,'CONFIRMED','1',NULL,0,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(70,'1c14e6a8-c62b-44bd-a2ea-9bda5e5a4ee0~w1','RSVcf5119de226e422d9030',1,1,'t','2026-09-29 12:00:00','2026-09-29 13:00:00',4,'CONFIRMED','1',NULL,0,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(71,'1c14e6a8-c62b-44bd-a2ea-9bda5e5a4ee0~w2','RSV475441a68e504c95a458',1,1,'t','2026-10-06 12:00:00','2026-10-06 13:00:00',4,'CONFIRMED','1',NULL,0,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(72,'1c14e6a8-c62b-44bd-a2ea-9bda5e5a4ee0~w3','RSVb3fb8d9c03934f30a8d4',1,1,'t','2026-10-13 12:00:00','2026-10-13 13:00:00',4,'CONFIRMED','1',NULL,0,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(73,'4b29f347-80d3-46dc-bf90-542eee055814','RSV08d05c99bee14a089e8a',2,1,'1q','2026-09-22 12:30:00','2026-09-22 13:30:00',4,'CONFIRMED','',NULL,0,'2026-09-22 12:12:21','2026-09-22 12:12:21'),(74,'bbece199-055d-4773-8554-2050cfc11166','RSVc7881d5da6c34ba8a162',2,1,'1','2026-09-28 14:45:00','2026-09-28 16:15:00',4,'CONFIRMED','1',NULL,0,'2026-09-22 12:18:56','2026-09-22 12:18:56');
/*!40000 ALTER TABLE `reservation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reservation_attendee`
--

DROP TABLE IF EXISTS `reservation_attendee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reservation_attendee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reservation_id` bigint NOT NULL COMMENT '所属预约ID（预约域，只读引用）',
  `user_id` bigint NOT NULL COMMENT '参与人用户ID（身份域，只读引用）',
  `attendee_role` varchar(20) NOT NULL DEFAULT 'ATTENDEE' COMMENT '参会角色：ORGANIZER-组织者(预约创建人) / ATTENDEE-参与人',
  `attendance_status` varchar(20) NOT NULL DEFAULT 'EXPECTED' COMMENT '出勤状态：EXPECTED-待签到 / CHECKED_IN-已签到 / CHECKED_OUT-已签退 / NO_SHOW-缺席',
  `check_in_at` datetime DEFAULT NULL COMMENT '签到时间（服务器时间）',
  `check_out_at` datetime DEFAULT NULL COMMENT '签退时间（服务器时间）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attendee_reservation_user` (`reservation_id`,`user_id`),
  KEY `idx_attendee_user` (`user_id`),
  CONSTRAINT `fk_attendee_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`),
  CONSTRAINT `fk_attendee_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_attendee_role` CHECK ((`attendee_role` in (_utf8mb4'ORGANIZER',_utf8mb4'ATTENDEE'))),
  CONSTRAINT `chk_attendee_status` CHECK ((`attendance_status` in (_utf8mb4'EXPECTED',_utf8mb4'CHECKED_IN',_utf8mb4'CHECKED_OUT',_utf8mb4'NO_SHOW')))
) ENGINE=InnoDB AUTO_INCREMENT=3095 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约参与人与执行出勤表（meeting 域）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reservation_attendee`
--

LOCK TABLES `reservation_attendee` WRITE;
/*!40000 ALTER TABLE `reservation_attendee` DISABLE KEYS */;
INSERT INTO `reservation_attendee` VALUES (1,7,2,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(2,7,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(3,7,5,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(4,7,6,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(5,8,2,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(6,8,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(7,8,5,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(8,15,3,'ORGANIZER','CHECKED_OUT','2026-09-20 08:55:00','2026-09-20 09:58:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(9,15,2,'ATTENDEE','CHECKED_OUT','2026-09-20 08:58:00','2026-09-20 09:58:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(10,15,9,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(11,16,2,'ORGANIZER','CHECKED_OUT','2026-09-20 14:05:00','2026-09-20 15:33:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(12,16,5,'ATTENDEE','CHECKED_OUT','2026-09-20 14:06:00','2026-09-20 15:33:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(13,16,7,'ATTENDEE','CHECKED_OUT','2026-09-20 14:12:00','2026-09-20 15:33:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(14,16,9,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(15,17,5,'ORGANIZER','CHECKED_OUT','2026-09-18 09:30:00','2026-09-18 10:18:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(16,17,6,'ATTENDEE','CHECKED_OUT','2026-09-18 09:32:00','2026-09-18 10:18:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(17,19,7,'ORGANIZER','CHECKED_OUT','2026-09-16 09:05:00','2026-09-16 10:52:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(18,19,8,'ATTENDEE','CHECKED_IN','2026-09-16 09:06:00',NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(19,19,9,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(20,20,2,'ORGANIZER','CHECKED_OUT','2026-09-15 14:00:00','2026-09-15 16:08:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(21,20,6,'ATTENDEE','CHECKED_OUT','2026-09-15 13:58:00','2026-09-15 16:08:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(22,20,8,'ATTENDEE','CHECKED_OUT','2026-09-15 14:02:00','2026-09-15 16:08:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(23,22,9,'ORGANIZER','CHECKED_OUT','2026-09-13 11:00:00','2026-09-13 11:43:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(24,22,10,'ATTENDEE','CHECKED_OUT','2026-09-13 11:01:00','2026-09-13 11:43:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(25,23,3,'ORGANIZER','CHECKED_OUT','2026-09-12 13:58:00','2026-09-12 15:48:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(26,23,5,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(27,23,6,'ATTENDEE','CHECKED_OUT','2026-09-12 14:00:00','2026-09-12 15:48:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(28,25,6,'ORGANIZER','CHECKED_OUT','2026-09-20 16:00:00','2026-09-20 16:28:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(29,25,7,'ATTENDEE','CHECKED_OUT','2026-09-20 16:00:00','2026-09-20 16:28:00','2026-09-21 20:43:16','2026-09-21 20:43:16'),(30,26,5,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:26:06'),(31,26,6,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:26:06'),(32,26,7,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:26:06'),(33,26,8,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:26:06'),(34,27,7,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:15:05'),(35,27,9,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:15:05'),(36,27,10,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:15:05'),(37,28,9,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:46:11'),(38,28,5,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:46:11'),(39,28,6,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:46:11'),(40,34,3,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:26:06'),(41,34,2,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:26:06'),(42,34,5,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:26:06'),(43,45,7,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:46:11'),(44,45,5,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:46:11'),(45,45,6,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:46:11'),(46,45,10,'ATTENDEE','NO_SHOW',NULL,NULL,'2026-09-21 20:43:16','2026-09-22 09:46:11'),(47,46,2,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:44:12','2026-09-21 20:44:12'),(50,18,6,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:44:12','2026-09-21 20:44:12'),(52,6,2,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-21 20:44:12','2026-09-21 20:44:12'),(650,56,2,'ORGANIZER','CHECKED_OUT','2026-09-22 10:45:00','2026-09-22 12:30:00','2026-09-22 09:15:05','2026-09-22 11:13:32'),(661,57,2,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 09:15:09','2026-09-22 09:15:09'),(779,1,2,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-22 09:26:06','2026-09-22 09:26:06'),(969,66,2,'ORGANIZER','NO_SHOW',NULL,NULL,'2026-09-22 09:44:11','2026-09-22 09:44:11'),(971,67,2,'ORGANIZER','CHECKED_IN','2026-09-24 08:55:00',NULL,'2026-09-22 09:45:01','2026-09-22 09:53:19'),(972,67,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 09:45:01','2026-09-22 09:45:01'),(973,67,4,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 09:45:01','2026-09-22 09:45:01'),(988,3,2,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 09:46:11','2026-09-22 09:46:11'),(1172,68,2,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 09:58:36','2026-09-22 09:58:36'),(1174,68,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 09:58:36','2026-09-22 09:58:36'),(1175,68,6,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 09:58:36','2026-09-22 09:58:36'),(2134,69,1,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2135,69,2,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2136,69,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2137,70,1,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2138,70,2,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2139,70,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2140,71,1,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2141,71,2,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2142,71,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2143,72,1,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2144,72,2,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2145,72,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 11:19:45','2026-09-22 11:19:45'),(2699,73,1,'ORGANIZER','CHECKED_OUT','2026-09-22 12:16:58','2026-09-22 12:17:01','2026-09-22 12:12:21','2026-09-22 12:17:00'),(2700,73,4,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 12:12:21','2026-09-22 12:12:21'),(2701,73,3,'ATTENDEE','EXPECTED',NULL,NULL,'2026-09-22 12:12:21','2026-09-22 12:12:21'),(2990,35,4,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 12:35:58','2026-09-22 12:35:58'),(3013,74,1,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 12:38:39','2026-09-22 12:38:39'),(3014,32,4,'ORGANIZER','EXPECTED',NULL,NULL,'2026-09-22 12:38:39','2026-09-22 12:38:39');
/*!40000 ALTER TABLE `reservation_attendee` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `room_category`
--

DROP TABLE IF EXISTS `room_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_name` varchar(50) NOT NULL COMMENT '分类名称（唯一）：小型会议室/普通会议室/大型会议室/特殊会议室',
  `min_capacity` int NOT NULL COMMENT '分类容量下限（人）',
  `max_capacity` int NOT NULL COMMENT '分类容量上限（人）',
  `approval_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要审批：1-是（提交后PENDING，管理员审批） 0-否（提交后直接CONFIRMED）',
  `max_duration_minutes` int NOT NULL DEFAULT '1440' COMMENT '单次预约最大时长（分钟），Service 校验',
  `advance_days` int NOT NULL DEFAULT '7' COMMENT '允许提前预约的最大天数（天），Service 校验',
  `description` varchar(500) DEFAULT NULL COMMENT '分类说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`category_name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室分类表（类型与预约规则）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `room_category`
--

LOCK TABLES `room_category` WRITE;
/*!40000 ALTER TABLE `room_category` DISABLE KEYS */;
INSERT INTO `room_category` VALUES (1,'小型会议室',3,8,0,1440,7,'3-8人日常讨论，免审批，单次最长24小时，可提前7天预约','2026-09-21 20:43:16','2026-09-21 20:43:16'),(2,'普通会议室',9,20,0,1440,7,'9-20人常规会议，免审批，单次最长24小时，可提前7天预约','2026-09-21 20:43:16','2026-09-21 20:43:16'),(3,'大型会议室',21,100,1,1440,14,'21人以上大型会议，需管理员审批，单次最长24小时，可提前14天预约','2026-09-21 20:43:16','2026-09-21 20:43:16'),(4,'特殊会议室',10,60,1,1440,14,'路演厅、多功能厅等受控场地，需管理员审批，单次最长24小时，可提前14天预约','2026-09-21 20:43:16','2026-09-21 20:43:16');
/*!40000 ALTER TABLE `room_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `room_facility`
--

DROP TABLE IF EXISTS `room_facility`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_facility` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `room_id` bigint NOT NULL COMMENT '所属会议室ID',
  `facility_name` varchar(50) NOT NULL COMMENT '设施名称：投影仪/白板/视频会议设备/麦克风等',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '设施数量',
  `description` varchar(200) DEFAULT NULL COMMENT '设施说明',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_facility` (`room_id`,`facility_name`),
  CONSTRAINT `fk_facility_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室设施表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `room_facility`
--

LOCK TABLES `room_facility` WRITE;
/*!40000 ALTER TABLE `room_facility` DISABLE KEYS */;
INSERT INTO `room_facility` VALUES (1,1,'投影仪',1,NULL),(2,1,'白板',1,NULL),(3,1,'无线投屏',1,NULL),(4,2,'显示屏',1,'55寸电视'),(5,2,'白板',1,NULL),(6,3,'投影仪',1,NULL),(7,3,'麦克风',2,'无线手持'),(8,3,'讲台电脑',1,NULL),(9,4,'投影仪',1,NULL),(10,4,'视频会议设备',1,NULL),(11,4,'白板',1,NULL),(12,5,'投影仪',2,NULL),(13,5,'视频会议设备',1,NULL),(14,5,'无线麦克风',4,NULL),(15,5,'音响系统',1,NULL),(16,6,'视频会议设备',1,NULL),(17,6,'音响系统',1,NULL),(18,6,'舞台灯光',1,'路演用，需管理员协助开启'),(19,6,'无线投屏',1,NULL),(20,7,'投影仪',1,'吸顶安装'),(21,7,'白板',1,NULL),(22,7,'电子白板',1,NULL),(23,8,'投影仪',1,NULL),(24,8,'视频会议设备',1,NULL),(25,8,'讲台电脑',1,NULL),(26,9,'投影仪',2,NULL),(27,9,'无线麦克风',4,NULL),(28,9,'视频摄像头',1,'录播用'),(29,10,'音响系统',1,NULL),(30,10,'舞台灯光',1,NULL),(31,10,'麦克风',2,NULL),(32,11,'显示屏',1,'65寸显示屏'),(33,11,'白板',1,NULL),(34,11,'无线投屏',1,NULL),(35,12,'显示屏',1,'75寸显示屏'),(36,12,'白板',1,NULL),(37,12,'视频会议设备',1,NULL),(38,13,'显示屏',1,'面试用投屏'),(39,13,'白板',1,NULL),(40,14,'投影仪',2,NULL),(41,14,'音响系统',1,'含调音台'),(42,14,'舞台灯光',1,NULL),(43,14,'无线麦克风',6,NULL),(44,14,'视频会议设备',1,NULL),(45,15,'投影仪',1,'待更换'),(46,15,'白板',1,NULL),(47,16,'白板',1,NULL);
/*!40000 ALTER TABLE `room_facility` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `room_maintenance`
--

DROP TABLE IF EXISTS `room_maintenance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_maintenance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `room_id` bigint NOT NULL COMMENT '会议室ID',
  `reason` varchar(200) NOT NULL COMMENT '维护原因',
  `start_time` datetime NOT NULL COMMENT '维护开始时间',
  `end_time` datetime NOT NULL COMMENT '维护结束时间',
  `status` varchar(20) NOT NULL DEFAULT 'PLANNED' COMMENT '维护计划状态：PLANNED/FINISHED',
  `created_by` bigint NOT NULL COMMENT '创建管理员用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_maintenance_room` (`room_id`),
  CONSTRAINT `fk_maintenance_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`),
  CONSTRAINT `chk_maintenance_period` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室维护计划表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `room_maintenance`
--

LOCK TABLES `room_maintenance` WRITE;
/*!40000 ALTER TABLE `room_maintenance` DISABLE KEYS */;
/*!40000 ALTER TABLE `room_maintenance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `room_open_rule`
--

DROP TABLE IF EXISTS `room_open_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_open_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `room_id` bigint NOT NULL COMMENT '会议室ID',
  `weekday` tinyint NOT NULL COMMENT '星期：1=周一 ... 7=周日',
  `open_time` time NOT NULL COMMENT '开放时间（含）',
  `close_time` time NOT NULL COMMENT '关闭时间（不含）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1-是 0-否',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_open_rule` (`room_id`,`weekday`),
  KEY `idx_open_rule_room_weekday` (`room_id`,`weekday`),
  CONSTRAINT `fk_open_rule_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`),
  CONSTRAINT `chk_open_rule_period` CHECK ((`close_time` > `open_time`)),
  CONSTRAINT `chk_open_rule_weekday` CHECK ((`weekday` between 1 and 7))
) ENGINE=InnoDB AUTO_INCREMENT=113 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室每周开放时间规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `room_open_rule`
--

LOCK TABLES `room_open_rule` WRITE;
/*!40000 ALTER TABLE `room_open_rule` DISABLE KEYS */;
INSERT INTO `room_open_rule` VALUES (1,1,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(2,1,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(3,1,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(4,1,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(5,1,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(6,1,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(7,1,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(8,2,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(9,2,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(10,2,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(11,2,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(12,2,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(13,2,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(14,2,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(15,7,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(16,7,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(17,7,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(18,7,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(19,7,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(20,7,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(21,7,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(22,13,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(23,13,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(24,13,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(25,13,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(26,13,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(27,13,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(28,13,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(29,16,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(30,16,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(31,16,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(32,16,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(33,16,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(34,16,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(35,16,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(36,3,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(37,3,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(38,3,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(39,3,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(40,3,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(41,3,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(42,3,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(43,4,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(44,4,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(45,4,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(46,4,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(47,4,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(48,4,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(49,4,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(50,8,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(51,8,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(52,8,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(53,8,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(54,8,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(55,8,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(56,8,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(57,11,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(58,11,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(59,11,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(60,11,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(61,11,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(62,11,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(63,11,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(64,12,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(65,12,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(66,12,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(67,12,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(68,12,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(69,12,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(70,12,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(71,15,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(72,15,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(73,15,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(74,15,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(75,15,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(76,15,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(77,15,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(78,5,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(79,5,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(80,5,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(81,5,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(82,5,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(83,5,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(84,5,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(85,9,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(86,9,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(87,9,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(88,9,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(89,9,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(90,9,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(91,9,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(92,14,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(93,14,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(94,14,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(95,14,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(96,14,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(97,14,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(98,14,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(99,6,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(100,6,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(101,6,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(102,6,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(103,6,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(104,6,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(105,6,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(106,10,7,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(107,10,6,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(108,10,5,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(109,10,4,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(110,10,3,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(111,10,2,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16'),(112,10,1,'08:00:00','19:00:00',1,'2026-09-21 20:43:16','2026-09-21 20:43:16');
/*!40000 ALTER TABLE `room_open_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '登录用户名（唯一）',
  `password` varchar(100) NOT NULL COMMENT 'BCrypt 密码摘要，不保存明文密码',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户 / ADMIN-管理员',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门ID（可空）',
  `credit_score` int NOT NULL DEFAULT '100' COMMENT '信用分（预约资格门槛由应用层维护）',
  `restricted_until` datetime DEFAULT NULL COMMENT '限制截止时间（非空且在未来=黑名单/限制期）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态：1-正常 0-禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `fk_user_department` (`department_id`),
  CONSTRAINT `fk_user_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','系统管理员','admin@timeslot.demo','13800000001','ADMIN',1,100,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(2,'zhangsan','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','张三','zhangsan@timeslot.demo','13800000002','USER',2,110,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(3,'lisi','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','李四','lisi@timeslot.demo','13800000003','USER',1,80,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(4,'lzx','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','LZX','lzx@timeslot.demo','13800000004','ADMIN',1,100,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(5,'wangwu','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','王五','wangwu@timeslot.demo','13800000005','USER',2,110,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(6,'zhaoliu','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','赵六','zhaoliu@timeslot.demo','13800000006','USER',3,95,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(7,'sunqi','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','孙七','sunqi@timeslot.demo','13800000007','USER',4,100,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(8,'zhouba','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','周八','zhouba@timeslot.demo','13800000008','USER',5,70,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(9,'wujiu','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','吴九','wujiu@timeslot.demo','13800000009','USER',6,100,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(10,'zhengshi','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','郑十','zhengshi@timeslot.demo','13800000010','USER',2,60,NULL,1,'2026-09-21 20:43:15','2026-09-21 20:43:15'),(11,'liushiyi','$2b$10$qAKyBKwAtTKf3sNAjJYyouDuVVJc9cx.8rL2dx0dwmIft.3dU4hPm','刘十一','liushiyi@timeslot.demo','13800000011','USER',3,45,'2026-10-01 20:43:16',1,'2026-09-21 20:43:15','2026-09-21 20:43:16');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_time_config`
--

DROP TABLE IF EXISTS `system_time_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_time_config` (
  `id` tinyint NOT NULL COMMENT 'singleton row; always 1',
  `fixed_time` datetime DEFAULT NULL COMMENT 'NULL means real-time business clock',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_system_time_config_singleton` CHECK ((`id` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='business test clock configuration';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_time_config`
--

LOCK TABLES `system_time_config` WRITE;
/*!40000 ALTER TABLE `system_time_config` DISABLE KEYS */;
INSERT INTO `system_time_config` VALUES (1,NULL,'2026-09-22 11:13:32');
/*!40000 ALTER TABLE `system_time_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_violation`
--

DROP TABLE IF EXISTS `user_violation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_violation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '被记录的用户ID',
  `violation_type` varchar(20) NOT NULL COMMENT '类型：CREDIT_DEDUCT-信用扣分 / CREDIT_REWARD-信用奖励 / BLACKLIST_SET-进入限制 / BLACKLIST_RELEASE-解除限制 / ACCOUNT_DISABLE-账号禁用 / ACCOUNT_ENABLE-账号启用',
  `credit_change` int NOT NULL DEFAULT '0' COMMENT '信用分变化量（正数加分，负数扣分，与信用无关的记录为0）',
  `reason` varchar(500) NOT NULL COMMENT '原因（人工操作必填，应用层校验）',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人用户ID（NULL=系统自动）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_violation_user` (`user_id`,`created_at`),
  CONSTRAINT `fk_violation_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户违规与信用记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_violation`
--

LOCK TABLES `user_violation` WRITE;
/*!40000 ALTER TABLE `user_violation` DISABLE KEYS */;
INSERT INTO `user_violation` VALUES (1,2,'CREDIT_REWARD',10,'协助保障多场大型会议顺利举行，信用奖励',1,'2026-09-21 20:43:16'),(2,3,'CREDIT_DEDUCT',-20,'预约后未到场且未提前取消，信用扣分',1,'2026-09-21 20:43:16'),(3,5,'CREDIT_REWARD',10,'主动承担跨天彩排的场地协调工作，信用奖励',1,'2026-09-21 20:43:16'),(4,6,'CREDIT_DEDUCT',-5,'会议结束后未及时登记实际使用记录，信用扣分',4,'2026-09-21 20:43:16'),(5,8,'CREDIT_DEDUCT',-30,'多次预约后缺席且未签到，信用扣分',1,'2026-09-21 20:43:16'),(6,10,'CREDIT_DEDUCT',-40,'违规占用受控会议室且未按流程申请，信用扣分',1,'2026-09-21 20:43:16'),(7,11,'CREDIT_DEDUCT',-55,'连续三次预约后缺席，信用扣分',1,'2026-09-21 20:43:16'),(8,11,'BLACKLIST_SET',0,'信用分低于预约门槛，自动进入限制期 10 天',NULL,'2026-09-21 20:43:16'),(9,7,'ACCOUNT_DISABLE',0,'多次提交冲突预约并占用他人时段，管理员临时禁用账号',1,'2026-09-21 20:43:16'),(10,7,'ACCOUNT_ENABLE',0,'已确认整改，恢复账号使用',1,'2026-09-21 20:43:16');
/*!40000 ALTER TABLE `user_violation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'meeting_room'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-22 12:45:18
