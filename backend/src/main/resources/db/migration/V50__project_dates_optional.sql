-- =====================================================
-- V50: 项目期间改为可选,放开 project 表日期列的 NOT NULL 约束
-- =====================================================

ALTER TABLE project ALTER COLUMN start_date DROP NOT NULL;
ALTER TABLE project ALTER COLUMN end_date DROP NOT NULL;
