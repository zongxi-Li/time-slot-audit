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
            SELECT id, category_id, room_name, location, capacity, status, description
            FROM meeting_room
            WHERE id = #{roomId}
            FOR UPDATE
            """)
    RoomRow findRoomForUpdate(@Param("roomId") Long roomId);

    @Select("""
            SELECT id, category_id, room_name, location, capacity, status, description
            FROM meeting_room
            WHERE id = #{roomId}
            """)
    RoomRow findRoomById(@Param("roomId") Long roomId);

    @Select("""
            SELECT id, category_id, room_name, location, capacity, status, description
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
