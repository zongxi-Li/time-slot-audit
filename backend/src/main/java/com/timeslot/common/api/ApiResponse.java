/**
 * 文件职责：定义后端统一成功响应结构，供所有 Controller 返回给前端。
 * 接口：统一包装业务数据和状态信息。
 */
package com.timeslot.common.api;

public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "success", data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
