## Decisions

1. 客户编号 KH+yyyyMMdd+流水，复用编号生成器模式
2. 工商信息抓取用 Playwright 爱企查（免费），抓取字段：信用代码/注册资本/注册地/法定代表人
3. 客户挂部门（dept_id），数据按部门隔离
4. 境外客户无工商信息抓取

## Migration Plan

V23 建表。
