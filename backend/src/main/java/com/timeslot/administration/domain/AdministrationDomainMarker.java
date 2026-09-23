/**
 * 文件职责：管理领域 domain 包标记，不承载预约审批业务。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无业务方法。
 */

package com.timeslot.administration.domain;

/** Boundary marker; approval and audit services remain planned for the next increment. */
public final class AdministrationDomainMarker {
    private AdministrationDomainMarker() {
    }
}
