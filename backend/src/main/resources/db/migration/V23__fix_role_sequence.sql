-- V23: 修复 sys_role 自增序列（V22 手动插入 ID=4 导致冲突）
SELECT setval(pg_get_serial_sequence('sys_role', 'id'), (SELECT MAX(id) FROM sys_role));
