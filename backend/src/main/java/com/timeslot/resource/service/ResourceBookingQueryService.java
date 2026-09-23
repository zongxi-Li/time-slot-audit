/**
 * 文件职责：为预约流程读取并锁定可预约会议室的资源信息。
 * 接口：被 ReservationService 调用。
 * 方法：lockBookableRoom 在预约事务中锁定会议室行并校验其可预订条件。
*/

package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.dto.BookableRoomProfile;
import com.timeslot.resource.mapper.ResourceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public resource contract for the reservation domain. The lock method joins the caller's
 * transaction (PROPAGATION MANDATORY), so the meeting_room row lock is held until the
 * reservation transaction commits — never earlier.
 */
@Service
public class ResourceBookingQueryService {
    private final ResourceMapper mapper;

    public ResourceBookingQueryService(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    /** Must be called inside the reservation transaction; the mapper performs SELECT ... FOR UPDATE. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BookableRoomProfile lockBookableRoom(Long roomId) {
        ResourceMapper.RoomRow row = mapper.findRoomForUpdate(roomId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室不存在");
        }
        ResourceMapper.CategoryRow category = mapper.findCategory(row.getCategoryId());
        if (category == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室分类不存在");
        }
        return new BookableRoomProfile(row.getId(), row.getRoomName(), row.getCapacity(),
                MeetingRoomStatus.fromDb(row.getStatus()).name(), category.getApprovalRequired() == 1,
                category.getMaxDurationMinutes(), category.getAdvanceDays());
    }
}
