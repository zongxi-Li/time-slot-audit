/**
 * 文件职责：实现部门查询和管理员部门维护。
 * 接口：由 DepartmentController 调用。
 */
package com.timeslot.identity.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.identity.domain.Department;
import com.timeslot.identity.dto.DepartmentRequest;
import com.timeslot.identity.dto.DepartmentResponse;
import com.timeslot.identity.mapper.DepartmentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {
    private final DepartmentMapper departmentMapper;

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    public List<DepartmentResponse> list() {
        return departmentMapper.findAll().stream().map(DepartmentResponse::from).toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String deptName = request.deptName().trim();
        ensureNameAvailable(deptName, null);
        departmentMapper.insert(deptName, request.description());
        return DepartmentResponse.from(departmentMapper.findByDeptName(deptName));
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        if (departmentMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "部门不存在");
        }
        String deptName = request.deptName().trim();
        ensureNameAvailable(deptName, id);
        departmentMapper.update(id, deptName, request.description());
        return DepartmentResponse.from(departmentMapper.findById(id));
    }

    private void ensureNameAvailable(String deptName, Long excludeId) {
        if (departmentMapper.countByNameExcluding(deptName, excludeId == null ? -1L : excludeId) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "部门名称已存在");
        }
    }
}
