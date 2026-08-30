-- V31: 函证必须关联项目（存量表为空，可直接收紧约束）
ALTER TABLE confirmation ALTER COLUMN project_id SET NOT NULL;
