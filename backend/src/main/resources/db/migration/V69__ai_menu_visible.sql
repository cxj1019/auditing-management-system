-- V69: AI 设置菜单改为可见（visible=0 在本系统表示隐藏，V68 误填 0）
UPDATE sys_menu SET visible = 1 WHERE id = 205 AND visible = 0;
