/**
 * 文件职责：统一封装后端 API 的业务码、提示消息和数据载荷。
 * 接口：统一包装业务数据和状态信息。
 * 方法：success(T) 创建成功响应；error(code, message) 创建错误响应。
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
