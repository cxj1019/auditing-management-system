## Context

合同模块提供 contract 表可作为项目关联载体。Flyway 当前 v8，新迁移 V9/V10。编号生成、状态机、权限种子模式均已在合同/报销模块验证过，直接复用同一套模式。

## Goals / Non-Goals

**Goals:**
- 函证台账 CRUD + 三段式状态机 + 逾期自动标记
- 逾期阈值做成 application.yml 配置项（confirmation.overdue-days，默认 30），无需改代码即可调整
- 前端列表对逾期行做醒目标记

**Non-Goals:**
- 不做函证模板生成与打印
- 不做与银行/被函证单位的电子直连
- 不做催函提醒消息推送（列表标记已满足当前需求）
- 不做多轮发函跟踪（一次发出-回函闭环）

## Decisions

**1. 单表 `confirmation`，状态机四态**
NOT_SENT(0) → SENT(1) → CONFIRMED(2)；NOT_SENT/SENT → VOIDED(3)。VOIDED 为终态。Service 层显式校验。
替代方案：增加「催收中」中间态——当前无此流程诉求，YAGNI。

**2. 逾期为计算属性而非存储字段**
列表查询时用 `sent_date < today - N` 动态计算 overdue 布尔值（SQL CASE WHEN），避免定时任务刷库。阈值读 Spring 配置 `@Value("${confirmation.overdue-days:30}")`。
替代方案：冗余 overdue 字段+定时刷新——引入任务调度复杂度，收益低。

**3. 流转接口按动作语义设计 PUT /{id}/status?action=send|confirm|void + 日期参数**
send 必填 sentDate，confirm 必填 confirmedDate，void 无需日期。与合同模块的 target-status 风格略有差异但更贴合业务动词。
替代方案：统一 target status——日期校验分散，易漏。

**4. 迁移 V9（建表）/ V10（菜单种子），菜单 ID 130 段**
130=函证管理菜单，131–134=按钮（add/edit/delete/status）；admin/manager 全量、employee 仅 list。

**5. 合同关联可选**
contract_id 可空，索引支撑后续按项目聚合。

## Risks / Trade-offs

- 逾期计算依赖服务器时钟 → 单体内网部署时钟一致，风险低
- 已作废函证保留在台账 → 审计留痕需要，不做物理删除
- employee 可见全部函证 → 与既有模块口径一致

## Migration Plan

V9/V10 随启动自动执行；回滚即回退代码。无存量数据迁移。

## Open Questions

- 是否需要区分银行函证与往来款函证的不同模板字段——本期统一摘要字段，后续按需扩展