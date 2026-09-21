# com.timeslot 领域边界

com.timeslot 按业务领域组织代码。包名表达业务责任，不能仅根据 Java 类名把所有类都称为 Entity：项目同时使用 Domain、DTO、Mapper Row/Write、Projection 和 API Response。

## 包职责

~~~text
com.timeslot
├─ common
│  ├─ api           统一响应和错误码
│  ├─ exception     业务异常和全局异常处理
│  ├─ security      JWT、当前用户和安全配置
│  ├─ time          可持久化业务时钟
│  └─ bookingwindow 全局预约时间窗口
├─ identity         用户、部门、资格、信用和违规
├─ resource         会议室、分类、设施、开放规则、维护和报修
├─ reservation      预约事实、查询和生命周期
├─ meeting          参会人、签到签退、出勤和通知
└─ administration   审批、审计和运营统计
~~~

## 依赖方向

~~~mermaid
flowchart LR
    identity --> reservation
    resource --> reservation
    reservation --> meeting
    reservation --> administration
    common --> identity
    common --> resource
    common --> reservation
    common --> meeting
    common --> administration
~~~

实际调用应遵循以下约束：

- 跨域读取优先调用对方公开的 Query Service 或 Service。
- 跨域写入通过生命周期服务、Port/SPI 或明确的应用边界完成。
- 不直接访问其他领域的 Mapper、数据库表或内部 Row/Write 类。
- reservation 拥有预约状态；meeting 只写会议执行域的参会人和出勤事实。
- administration 负责管理动作，但预约状态转换仍由预约生命周期服务裁决。

## 预约状态

持久化状态：

~~~text
PENDING ──approve──> CONFIRMED
PENDING ──reject───> REJECTED
PENDING/CONFIRMED ──cancel──> CANCELLED
~~~

UPCOMING、IN_USE、COMPLETED 是根据持久化状态和业务时间推导出的展示状态，不单独写入预约状态字段。

## 源码定位

- Controller：对外 HTTP 边界和权限声明。
- Service：业务规则、事务和跨域编排。
- Domain：业务对象、状态和值对象。
- DTO：请求和响应契约。
- Mapper：MyBatis SQL 接口及必要的 Row/Write 投影。
- src/main/resources/mapper/：SQL XML 映射。
