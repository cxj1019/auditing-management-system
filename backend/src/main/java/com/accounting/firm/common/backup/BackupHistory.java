package com.accounting.firm.common.backup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 备份历史 */
@Data
@TableName("backup_history")
public class BackupHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String objectPath;

    private Long sizeBytes;

    private Integer fileCount;

    private LocalDateTime createTime;
}
