-- V65: 项目预算工时按级别明细（级别 × 人数 × 每人工时），总预算由明细汇总写入 project.budget_hours
CREATE TABLE IF NOT EXISTS project_budget (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT      NOT NULL,
    staff_level_id  BIGINT      NOT NULL,
    headcount       INT         NOT NULL DEFAULT 1,
    hours_per_person NUMERIC(10,1) NOT NULL DEFAULT 0,
    create_time     TIMESTAMP DEFAULT NOW(),
    update_time     TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_project_budget_project ON project_budget(project_id);
