## ADDED Requirements

### Requirement: 日程管理
系统 SHALL 支持创建、编辑、删除日程，必填：标题、日期、类型（工作/休假/出差/其他）、工时（小时）。可选关联项目。

#### Scenario: 创建日程
- **WHEN** 用户填写标题、日期、类型、工时并保存
- **THEN** 日程出现在日历对应日期

### Requirement: 月历视图
系统 SHALL 以月历形式展示日程，按日聚合显示日程条和工时合计。

#### Scenario: 查看月历
- **WHEN** 用户打开日程页面
- **THEN** 显示当月日历，每日格子内展示日程条和工时合计

### Requirement: 工时统计
系统 SHALL 按成员和项目汇总工时。

#### Scenario: 查看工时汇总
- **WHEN** 用户切换到工时统计页签
- **THEN** 显示每位成员的月度工时合计
