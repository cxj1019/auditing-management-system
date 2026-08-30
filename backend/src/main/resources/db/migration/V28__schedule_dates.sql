-- V28: 日程增加结束日期、起止时间
ALTER TABLE schedule ADD COLUMN end_date DATE;
ALTER TABLE schedule ADD COLUMN start_time VARCHAR(5);
ALTER TABLE schedule ADD COLUMN end_time VARCHAR(5);
