package com.timeslot.common.time;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/** currentTime 固定输出到秒：ISO 默认序列化在整点时会省略秒，前端 picker 解析会出歧义。 */
public record SystemTimeResponse(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime currentTime,
        SystemTimeMode mode) {
}
