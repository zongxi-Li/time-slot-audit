package com.timeslot.resource.mapper;

import com.timeslot.resource.mapper.ResourceMapper.RoomRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ResourceMapper {
    @Select("""
            SELECT r.id, r.category_id, r.room_name, r.location, r.capacity, r.status,
                   c.category_name,
                   GROUP_CONCAT(f.facility_name ORDER BY f.id SEPARATOR ',') AS facilities_csv
            FROM meeting_room r
            JOIN room_category c ON c.id = r.category_id
            LEFT JOIN room_facility f ON f.room_id = r.id
            GROUP BY r.id, r.category_id, r.room_name, r.location, r.capacity, r.status, c.category_name
            ORDER BY r.room_name
            """)
    List<RoomRow> listRooms();

    @Select("""
            SELECT id, category_id, room_name, location, capacity, status
            FROM meeting_room
            WHERE id = #{roomId}
            FOR UPDATE
            """)
    RoomRow findRoomForUpdate(@Param("roomId") Long roomId);

    @Select("""
            SELECT id, category_name, approval_required, max_duration_minutes, advance_days
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

    class RoomRow {
        private Long id;
        private Long categoryId;
        private String roomName;
        private String location;
        private Integer capacity;
        private Integer status;
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
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public String getFacilitiesCsv() { return facilitiesCsv; }
        public void setFacilitiesCsv(String facilitiesCsv) { this.facilitiesCsv = facilitiesCsv; }
    }

    class CategoryRow {
        private Long id;
        private String categoryName;
        private Integer approvalRequired;
        private Integer maxDurationMinutes;
        private Integer advanceDays;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public Integer getApprovalRequired() { return approvalRequired; }
        public void setApprovalRequired(Integer approvalRequired) { this.approvalRequired = approvalRequired; }
        public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
        public void setMaxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; }
        public Integer getAdvanceDays() { return advanceDays; }
        public void setAdvanceDays(Integer advanceDays) { this.advanceDays = advanceDays; }
    }

    class OpenRuleRow {
        private java.time.LocalTime openTime;
        private java.time.LocalTime closeTime;
        private Integer enabled;

        public java.time.LocalTime getOpenTime() { return openTime; }
        public void setOpenTime(java.time.LocalTime openTime) { this.openTime = openTime; }
        public java.time.LocalTime getCloseTime() { return closeTime; }
        public void setCloseTime(java.time.LocalTime closeTime) { this.closeTime = closeTime; }
        public Integer getEnabled() { return enabled; }
        public void setEnabled(Integer enabled) { this.enabled = enabled; }
    }
}
