-- V64: 员工级别制标准工时单价
CREATE TABLE IF NOT EXISTS staff_level (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(30) NOT NULL,
    hourly_rate NUMERIC(10,2) NOT NULL DEFAULT 0,
    sort        INT NOT NULL DEFAULT 0
);

INSERT INTO staff_level (name, hourly_rate, sort)
SELECT 'A1', 0, 10 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = 'A1');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT 'A2', 0, 20 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = 'A2');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT 'A3', 0, 30 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = 'A3');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT 'S1', 0, 40 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = 'S1');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT 'S2', 0, 50 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = 'S2');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT 'S3', 0, 60 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = 'S3');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT '项目经理', 0, 70 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = '项目经理');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT '经理', 0, 80 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = '经理');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT '合伙人', 0, 90 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = '合伙人');
INSERT INTO staff_level (name, hourly_rate, sort)
SELECT '其他', 0, 100 WHERE NOT EXISTS (SELECT 1 FROM staff_level WHERE name = '其他');

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS staff_level_id BIGINT;
