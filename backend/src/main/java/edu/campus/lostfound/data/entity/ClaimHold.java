package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 占用记录（对应架构 表附 1-2 的 hold）。
 *
 * <p>关键设计（架构 3.1【Original】决策 12）：本表**只存活跃占用**，
 * 申请时插入一行，认领进入终态时直接删除该行，历史由 claim 主表保留。
 * 因此 {@code item_id} 上只是一个普通唯一索引 {@code uk_hold_item}，
 * 不需要 released_at / active_flag 这类辅助列——也不存在"旧占用占着唯一索引"的问题。
 *
 * <p>并发口径：不做应用层锁，谁先插入成功谁占用，失败者收到 409（FR-07 / QR-09）。
 */
@Data
@TableName("claim_hold")
public class ClaimHold {

    @TableId(value = "hold_id", type = IdType.AUTO)
    private Long holdId;

    private Long claimId;

    private Long itemId;

    private LocalDateTime createdAt;
}
