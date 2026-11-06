package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学号占用申诉（FR-05）。
 *
 * <p>状态：0 待处理 / 1 已解除（原绑定被管理员解除）/ 2 已驳回。
 * <p>只存学号哈希，与 account.student_no_hash 同算法，便于等值查询。
 */
@Data
@TableName("appeal")
public class Appeal {

    @TableId(value = "appeal_id", type = IdType.AUTO)
    private Long appealId;

    /** 申诉人 */
    private Long userId;

    /** 申诉声称被占用的学号（SHA-256） */
    private String studentNoHash;

    private String reason;

    /** 0 待处理 / 1 已解除 / 2 已驳回 */
    private Integer state;

    private Long handledBy;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
