/**
 * 文件职责：会议室分类管理 HTTP 接口。
 * 接口：GET /api/admin/room-categories；
 *        POST /api/admin/room-categories；
 *        PUT /api/admin/room-categories/{categoryId}。
 * 方法：listCategories 查询分类；createCategory 新建分类；updateCategory 修改指定分类。
*/

package com.timeslot.resource.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.resource.dto.CategoryResponse;
import com.timeslot.resource.dto.SaveCategoryRequest;
import com.timeslot.resource.service.CategoryAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/admin/room-categories")
public class CategoryAdminController {
    private final CategoryAdminService categoryAdminService;

    public CategoryAdminController(CategoryAdminService categoryAdminService) {
        this.categoryAdminService = categoryAdminService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.success(categoryAdminService.listCategories());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody SaveCategoryRequest request) {
        return ApiResponse.success(categoryAdminService.createCategory(request));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long categoryId,
                                                        @Valid @RequestBody SaveCategoryRequest request) {
        return ApiResponse.success(categoryAdminService.updateCategory(categoryId, request));
    }
}
