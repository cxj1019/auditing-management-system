-- V62: 新增部门（商务行政部/代理记账部/后勤支持部）+ 日程设备（会议室/公司车辆）
INSERT INTO sys_department (id, dept_name, sort)
SELECT 9, '商务行政部', 40
WHERE NOT EXISTS (SELECT 1 FROM sys_department WHERE dept_name = '商务行政部');

INSERT INTO sys_department (id, dept_name, sort)
SELECT 10, '代理记账部', 50
WHERE NOT EXISTS (SELECT 1 FROM sys_department WHERE dept_name = '代理记账部');

INSERT INTO sys_department (id, dept_name, sort)
SELECT 11, '后勤支持部', 60
WHERE NOT EXISTS (SELECT 1 FROM sys_department WHERE dept_name = '后勤支持部');

CREATE TABLE IF NOT EXISTS schedule_resource (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(20)  NOT NULL DEFAULT '其他',
    status        SMALLINT     NOT NULL DEFAULT 1,
    create_time   TIMESTAMP    DEFAULT NOW()
);

INSERT INTO schedule_resource (name, resource_type)
SELECT '大会议室', '会议室'
WHERE NOT EXISTS (SELECT 1 FROM schedule_resource WHERE name = '大会议室');

INSERT INTO schedule_resource (name, resource_type)
SELECT '小会议室', '会议室'
WHERE NOT EXISTS (SELECT 1 FROM schedule_resource WHERE name = '小会议室');

INSERT INTO schedule_resource (name, resource_type)
SELECT '公司车辆', '车辆'
WHERE NOT EXISTS (SELECT 1 FROM schedule_resource WHERE name = '公司车辆');

ALTER TABLE schedule ADD COLUMN IF NOT EXISTS resource_id BIGINT;
