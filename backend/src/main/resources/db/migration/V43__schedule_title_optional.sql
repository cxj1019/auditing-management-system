-- V43: 日程标题改为可选（未填时界面显示为空白条目）
ALTER TABLE schedule ALTER COLUMN title DROP NOT NULL;
