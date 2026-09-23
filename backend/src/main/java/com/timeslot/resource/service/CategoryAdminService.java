/**
 * 文件职责：维护会议室分类及其容量范围、审批要求。
 * 接口：由 CategoryAdminController 调用。
 * 方法：listCategories/createCategory/updateCategory 查询或维护分类；applyRequest 映射请求字段；validateRange 校验容量区间；requireCategory/requireCategoryNameFree 检查存在性和名称唯一。
*/

package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.dto.CategoryResponse;
import com.timeslot.resource.dto.SaveCategoryRequest;
import com.timeslot.resource.mapper.ResourceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryAdminService {
    private final ResourceMapper mapper;

    public CategoryAdminService(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    public List<CategoryResponse> listCategories() {
        return mapper.listCategories().stream().map(row -> CategoryResponse.from(ResourceQueryService.toCategory(row)))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(SaveCategoryRequest request) {
        validateRange(request);
        requireCategoryNameFree(request.name(), null);
        ResourceMapper.CategoryWrite category = new ResourceMapper.CategoryWrite();
        applyRequest(category, request);
        try {
            mapper.insertCategory(category);
        } catch (DuplicateKeyException exception) {
            throw duplicateCategoryName();
        }
        return getCategoryResponse(category.getId());
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, SaveCategoryRequest request) {
        requireCategory(categoryId);
        validateRange(request);
        requireCategoryNameFree(request.name(), categoryId);
        ResourceMapper.CategoryWrite category = new ResourceMapper.CategoryWrite();
        category.setId(categoryId);
        applyRequest(category, request);
        try {
            mapper.updateCategory(category);
        } catch (DuplicateKeyException exception) {
            throw duplicateCategoryName();
        }
        return getCategoryResponse(categoryId);
    }

    public CategoryResponse getCategoryResponse(Long categoryId) {
        ResourceMapper.CategoryRow row = requireCategory(categoryId);
        return CategoryResponse.from(ResourceQueryService.toCategory(row));
    }

    private void applyRequest(ResourceMapper.CategoryWrite category, SaveCategoryRequest request) {
        category.setCategoryName(request.name().trim());
        category.setMinCapacity(request.minCapacity());
        category.setMaxCapacity(request.maxCapacity());
        category.setApprovalRequired(request.approvalRequired() ? 1 : 0);
        category.setMaxDurationMinutes(request.maxDurationMinutes());
        category.setAdvanceDays(request.advanceDays());
        category.setDescription(request.description());
    }

    private void validateRange(SaveCategoryRequest request) {
        if (request.minCapacity() > request.maxCapacity()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "分类容量下限不能大于上限");
        }
    }

    private ResourceMapper.CategoryRow requireCategory(Long categoryId) {
        ResourceMapper.CategoryRow row = mapper.findCategory(categoryId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室分类不存在");
        }
        return row;
    }

    private void requireCategoryNameFree(String categoryName, Long selfId) {
        List<ResourceMapper.CategoryRow> all = mapper.listCategories();
        boolean taken = all.stream().anyMatch(row -> row.getCategoryName().equals(categoryName.trim())
                && !row.getId().equals(selfId));
        if (taken) {
            throw duplicateCategoryName();
        }
    }

    private BusinessException duplicateCategoryName() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "分类名称已存在");
    }
}
