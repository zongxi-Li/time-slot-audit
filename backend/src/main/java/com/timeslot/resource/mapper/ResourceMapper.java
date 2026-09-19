/**
 * 文件职责：定义 会议室资源 的 MyBatis 数据访问接口，执行查询、插入和更新 SQL。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 */
package com.timeslot.resource.mapper;

import com.timeslot.resource.mapper.ResourceMapper.RoomRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ResourceMapper {
    String ROOM_COLUMNS = """
            id, category_id, room_name, location, capacity, status, description
            """;

    @Select("""
            SELECT r.id, r.category_id, r.room_name, r.location, r.capacity, r.status, r.description,
                   c.category_name,
                   GROUP_CONCAT(f.facility_name ORDER BY f.id SEPARATOR ',') AS facilities_csv
            FROM meeting_room r
            JOIN room_category c ON c.id = r.category_id
            LEFT JOIN room_facility f ON f.room_id = r.id
            GROUP BY r.id, r.category_id, r.room_name, r.location, r.capacity, r.status, r.description, c.category_name
            ORDER BY r.room_name
            """)
    List<RoomRow> listRooms();

    @Select("""
            SELECT
            """ + ROOM_COLUMNS + """
            FROM meeting_room
            WHERE id = #{roomId}
            FOR UPDATE
            """)
    RoomRow findRoomForUpdate(@Param("roomId") Long roomId);

    @Select("""
            SELECT
            """ + ROOM_COLUMNS + """
            FROM meeting_room
            WHERE id = #{roomId}
            """)
    RoomRow findRoomById(@Param("roomId") Long roomId);

    @Select("""
            SELECT
            """ + ROOM_COLUMNS + """
            FROM meeting_room
            WHERE room_name = #{roomName}
            """)
    RoomRow findRoomByName(@Param("roomName") String roomName);

    @Insert("""
            INSERT INTO meeting_room (category_id, room_name, location, capacity, status, description)
            VALUES (#{categoryId}, #{roomName}, #{location}, #{capacity}, #{status}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRoom(RoomWrite room);

    @Update("""
            UPDATE meeting_room
            SET category_id = #{categoryId}, room_name = #{roomName}, location = #{location},
                capacity = #{capacity}, description = #{description}
            WHERE id = #{id}
            """)
    int updateRoom(RoomWrite room);

    @Update("""
            UPDATE meeting_room
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateRoomStatus(@Param("id") Long id, @Param("status") int status);

    @Select("""
            SELECT id, category_name, min_capacity, max_capacity, approval_required,
                   max_duration_minutes, advance_days, description
            FROM room_category
            WHERE id = #{categoryId}
            """)
    CategoryRow findCategory(@Param("categoryId") Long categoryId);

    @Select("""
            SELECT open_time, close_time, enabled
            FROM room_open_rule
            WHERE room_id = #{roomId} AND weekday = #{weekday}
            """)
    OpenRuleRow findOpenRule(@Param("roomId") Long roomId, @Param("weekday") Integer weekday);

    @Select("""
            SELECT id, room_id, weekday, open_time, close_time, enabled
            FROM room_open_rule
            WHERE room_id = #{roomId}
            ORDER BY weekday
            """)
    List<OpenRuleRow> listOpenRulesByRoom(@Param("roomId") Long roomId);

    @Delete("""
            DELETE FROM room_open_rule
            WHERE room_id = #{roomId}
            """)
    int deleteOpenRulesByRoom(@Param("roomId") Long roomId);

    @Insert("""
            <script>
            INSERT INTO room_open_rule (room_id, weekday, open_time, close_time, enabled) VALUES
            <foreach collection="rules" item="rule" separator=",">
                (#{roomId}, #{rule.weekday}, #{rule.openTime}, #{rule.closeTime}, #{rule.enabled})
            </foreach>
            </script>
            """)
    int insertOpenRules(@Param("roomId") Long roomId, @Param("rules") List<OpenRuleWrite> rules);

    @Delete("""
            DELETE FROM room_facility
            WHERE room_id = #{roomId}
            """)
    int deleteFacilitiesByRoom(@Param("roomId") Long roomId);

    @Insert("""
            <script>
            INSERT INTO room_facility (room_id, facility_name, quantity, description) VALUES
            <foreach collection="facilities" item="facility" separator=",">
                (#{roomId}, #{facility.facilityName}, #{facility.quantity}, #{facility.description})
            </foreach>
            </script>
            """)
    int insertFacilities(@Param("roomId") Long roomId, @Param("facilities") List<FacilityWrite> facilities);

    @Select("""
            SELECT id, room_id, facility_name, quantity, description
            FROM room_facility
            WHERE id = #{facilityId}
            """)
    FacilityRow findFacilityById(@Param("facilityId") Long facilityId);

    @Insert("""
            INSERT INTO room_maintenance (room_id, reason, start_time, end_time, status, created_by)
            VALUES (#{roomId}, #{reason}, #{startTime}, #{endTime}, #{status}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMaintenance(MaintenanceWrite maintenance);

    @Select("""
            SELECT id, room_id, reason, start_time, end_time, status, created_by, created_at
            FROM room_maintenance
            WHERE room_id = #{roomId}
            ORDER BY start_time DESC
            """)
    List<MaintenanceRow> listMaintenanceByRoom(@Param("roomId") Long roomId);

    @Select("""
            SELECT id, room_id, reason, start_time, end_time, status, created_by, created_at
            FROM room_maintenance
            WHERE id = #{planId}
            """)
    MaintenanceRow findMaintenanceById(@Param("planId") Long planId);

    @Update("""
            UPDATE room_maintenance
            SET status = 'FINISHED'
            WHERE id = #{planId}
            """)
    int finishMaintenance(@Param("planId") Long planId);

    @Insert("""
            INSERT INTO facility_repair_ticket (room_id, facility_id, facility_name, issue, status,
                                                reporter_id, reporter_name)
            VALUES (#{roomId}, #{facilityId}, #{facilityName}, #{issue}, #{status}, #{reporterId}, #{reporterName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRepairTicket(RepairWrite ticket);

    @Select("""
            SELECT t.id, t.room_id, r.room_name, t.facility_id, t.facility_name, t.issue, t.status,
                   t.reporter_id, t.reporter_name, t.created_at, t.resolved_at, t.resolve_remark
            FROM facility_repair_ticket t
            JOIN meeting_room r ON r.id = t.room_id
            WHERE (#{roomId} IS NULL OR t.room_id = #{roomId})
            ORDER BY (t.status = 'OPEN') DESC, t.created_at DESC
            """)
    List<RepairTicketRow> listRepairTickets(@Param("roomId") Long roomId);

    @Select("""
            SELECT t.id, t.room_id, r.room_name, t.facility_id, t.facility_name, t.issue, t.status,
                   t.reporter_id, t.reporter_name, t.created_at, t.resolved_at, t.resolve_remark
            FROM facility_repair_ticket t
            JOIN meeting_room r ON r.id = t.room_id
            WHERE t.id = #{ticketId}
            """)
    RepairTicketRow findRepairTicketById(@Param("ticketId") Long ticketId);

    @Update("""
            UPDATE facility_repair_ticket
            SET status = 'RESOLVED', resolved_at = NOW(), resolve_remark = #{remark}
            WHERE id = #{ticketId} AND status = 'OPEN'
            """)
    int resolveRepairTicket(@Param("ticketId") Long ticketId, @Param("remark") String remark);

    @Select("""
            SELECT id, category_name, min_capacity, max_capacity, approval_required,
                   max_duration_minutes, advance_days, description
            FROM room_category
            ORDER BY id
            """)
    List<CategoryRow> listCategories();

    @Insert("""
            INSERT INTO room_category (category_name, min_capacity, max_capacity, approval_required,
                                       max_duration_minutes, advance_days, description)
            VALUES (#{categoryName}, #{minCapacity}, #{maxCapacity}, #{approvalRequired},
                    #{maxDurationMinutes}, #{advanceDays}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCategory(CategoryWrite category);

    @Update("""
            UPDATE room_category
            SET category_name = #{categoryName}, min_capacity = #{minCapacity}, max_capacity = #{maxCapacity},
                approval_required = #{approvalRequired}, max_duration_minutes = #{maxDurationMinutes},
                advance_days = #{advanceDays}, description = #{description}
            WHERE id = #{id}
            """)
    int updateCategory(CategoryWrite category);

    class RoomRow {
        private Long id;
        private Long categoryId;
        private String roomName;
        private String location;
        private Integer capacity;
        private Integer status;
        private String description;
        private String categoryName;
        private String facilitiesCsv;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getRoomName() { return roomName; }
        public void setRoomName(String roomName) { this.roomName = roomName; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public String getFacilitiesCsv() { return facilitiesCsv; }
        public void setFacilitiesCsv(String facilitiesCsv) { this.facilitiesCsv = facilitiesCsv; }
    }

    /** Mutable write model for insert/update of meeting_room; status is the DB integer. */
    class RoomWrite {
        private Long id;
        private Long categoryId;
        private String roomName;
        private String location;
        private Integer capacity;
        private Integer status;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getRoomName() { return roomName; }
        public void setRoomName(String roomName) { this.roomName = roomName; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    @Select("""
            SELECT id, room_id, facility_name, quantity, description
            FROM room_facility
            WHERE room_id = #{roomId}
            ORDER BY id
            """)
    List<FacilityRow> listFacilitiesByRoom(@Param("roomId") Long roomId);

    class MaintenanceRow {
        private Long id;
        private Long roomId;
        private String reason;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;
        private String status;
        private Long createdBy;
        private java.time.LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public java.time.LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(java.time.LocalDateTime startTime) { this.startTime = startTime; }
        public java.time.LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(java.time.LocalDateTime endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    /** Mutable write model for insert of room_maintenance; status is the DB string. */
    class MaintenanceWrite {
        private Long id;
        private Long roomId;
        private String reason;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;
        private String status;
        private Long createdBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public java.time.LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(java.time.LocalDateTime startTime) { this.startTime = startTime; }
        public java.time.LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(java.time.LocalDateTime endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    }

    class RepairTicketRow {
        private Long id;
        private Long roomId;
        private String roomName;
        private Long facilityId;
        private String facilityName;
        private String issue;
        private String status;
        private Long reporterId;
        private String reporterName;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime resolvedAt;
        private String resolveRemark;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public String getRoomName() { return roomName; }
        public void setRoomName(String roomName) { this.roomName = roomName; }
        public Long getFacilityId() { return facilityId; }
        public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
        public String getFacilityName() { return facilityName; }
        public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
        public String getIssue() { return issue; }
        public void setIssue(String issue) { this.issue = issue; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getReporterId() { return reporterId; }
        public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
        public String getReporterName() { return reporterName; }
        public void setReporterName(String reporterName) { this.reporterName = reporterName; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(java.time.LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
        public String getResolveRemark() { return resolveRemark; }
        public void setResolveRemark(String resolveRemark) { this.resolveRemark = resolveRemark; }
    }

    /** Mutable write model for insert of facility_repair_ticket; status is the DB string. */
    class RepairWrite {
        private Long id;
        private Long roomId;
        private Long facilityId;
        private String facilityName;
        private String issue;
        private String status;
        private Long reporterId;
        private String reporterName;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public Long getFacilityId() { return facilityId; }
        public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
        public String getFacilityName() { return facilityName; }
        public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
        public String getIssue() { return issue; }
        public void setIssue(String issue) { this.issue = issue; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getReporterId() { return reporterId; }
        public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
        public String getReporterName() { return reporterName; }
        public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    }

    class FacilityRow {
        private Long id;
        private Long roomId;
        private String facilityName;
        private Integer quantity;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public String getFacilityName() { return facilityName; }
        public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    class OpenRuleRow {
        private Long id;
        private Long roomId;
        private Integer weekday;
        private java.time.LocalTime openTime;
        private java.time.LocalTime closeTime;
        private Integer enabled;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public Integer getWeekday() { return weekday; }
        public void setWeekday(Integer weekday) { this.weekday = weekday; }
        public java.time.LocalTime getOpenTime() { return openTime; }
        public void setOpenTime(java.time.LocalTime openTime) { this.openTime = openTime; }
        public java.time.LocalTime getCloseTime() { return closeTime; }
        public void setCloseTime(java.time.LocalTime closeTime) { this.closeTime = closeTime; }
        public Integer getEnabled() { return enabled; }
        public void setEnabled(Integer enabled) { this.enabled = enabled; }
    }

    /** Mutable write model for batch insert of room_open_rule; enabled is the DB integer. */
    class OpenRuleWrite {
        private Integer weekday;
        private java.time.LocalTime openTime;
        private java.time.LocalTime closeTime;
        private Integer enabled;

        public Integer getWeekday() { return weekday; }
        public void setWeekday(Integer weekday) { this.weekday = weekday; }
        public java.time.LocalTime getOpenTime() { return openTime; }
        public void setOpenTime(java.time.LocalTime openTime) { this.openTime = openTime; }
        public java.time.LocalTime getCloseTime() { return closeTime; }
        public void setCloseTime(java.time.LocalTime closeTime) { this.closeTime = closeTime; }
        public Integer getEnabled() { return enabled; }
        public void setEnabled(Integer enabled) { this.enabled = enabled; }
    }

    /** Mutable write model for batch insert of room_facility. */
    class FacilityWrite {
        private String facilityName;
        private Integer quantity;
        private String description;

        public String getFacilityName() { return facilityName; }
        public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    class CategoryRow {
        private Long id;
        private String categoryName;
        private Integer minCapacity;
        private Integer maxCapacity;
        private Integer approvalRequired;
        private Integer maxDurationMinutes;
        private Integer advanceDays;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public Integer getMinCapacity() { return minCapacity; }
        public void setMinCapacity(Integer minCapacity) { this.minCapacity = minCapacity; }
        public Integer getMaxCapacity() { return maxCapacity; }
        public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }
        public Integer getApprovalRequired() { return approvalRequired; }
        public void setApprovalRequired(Integer approvalRequired) { this.approvalRequired = approvalRequired; }
        public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
        public void setMaxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; }
        public Integer getAdvanceDays() { return advanceDays; }
        public void setAdvanceDays(Integer advanceDays) { this.advanceDays = advanceDays; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /** Mutable write model for insert/update of room_category; approvalRequired is the DB integer. */
    class CategoryWrite {
        private Long id;
        private String categoryName;
        private Integer minCapacity;
        private Integer maxCapacity;
        private Integer approvalRequired;
        private Integer maxDurationMinutes;
        private Integer advanceDays;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public Integer getMinCapacity() { return minCapacity; }
        public void setMinCapacity(Integer minCapacity) { this.minCapacity = minCapacity; }
        public Integer getMaxCapacity() { return maxCapacity; }
        public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }
        public Integer getApprovalRequired() { return approvalRequired; }
        public void setApprovalRequired(Integer approvalRequired) { this.approvalRequired = approvalRequired; }
        public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
        public void setMaxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; }
        public Integer getAdvanceDays() { return advanceDays; }
        public void setAdvanceDays(Integer advanceDays) { this.advanceDays = advanceDays; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
