-- V63: 工时管理增强（1-6）
-- 2) 人员工时单价（工时 × 单价 = 项目人工成本自动核算）
CREATE TABLE IF NOT EXISTS labor_rate (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL UNIQUE,
    hourly_rate NUMERIC(10,2) NOT NULL DEFAULT 0,
    update_by   VARCHAR(50),
    update_time TIMESTAMP DEFAULT NOW()
);

-- 3) 项目预算工时
ALTER TABLE project ADD COLUMN IF NOT EXISTS budget_hours NUMERIC(10,1);

-- 4) 月度工时锁定 + 日程确认痕迹
CREATE TABLE IF NOT EXISTS schedule_lock (
    id         BIGSERIAL PRIMARY KEY,
    lock_month VARCHAR(7) NOT NULL UNIQUE,
    locked_by  VARCHAR(50),
    create_time TIMESTAMP DEFAULT NOW()
);

ALTER TABLE schedule ADD COLUMN IF NOT EXISTS confirmed SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS confirmed_by VARCHAR(50);
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS confirmed_time TIMESTAMP;
