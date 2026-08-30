## Why

事务所需要记录每位员工每日的工作安排和工时，按项目统计人力投入。借鉴已有日程原型，融入项目维度实现工时统计。

## What Changes

- 新增日程管理模块：日程 CRUD、月历视图、按项目/成员筛选、工时汇总
- 日程挂项目（可空），工时按项目聚合可辅助成本分析

## Capabilities

### New Capabilities
- `schedule-management`: 团队日程管理与工时统计

## Impact

- V26 建表；后端新增 schedule 包；前端新增日程页面
