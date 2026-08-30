-- V44: 日程事件分组（同一批次创建的多人的日程共享 event_id，支持整场删除/单人退出）
ALTER TABLE schedule ADD COLUMN event_id VARCHAR(40);

-- 历史数据回填：按 标题|日期|起止时间|时长|类型|创建人 归组
UPDATE schedule
SET event_id = MD5(CONCAT_WS('|', COALESCE(title, ''), schedule_date::text,
        COALESCE(start_time, ''), COALESCE(end_time, ''), COALESCE(hours, 0)::text, type, COALESCE(create_by, '')));

CREATE INDEX IF NOT EXISTS idx_schedule_event_id ON schedule (event_id);
