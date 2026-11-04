package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认领申请（对应架构 表附 1-3 的 claim）。
 *
 * <p>五态取值：0 待处理 / 1 待交接 / 2 仲裁中 / 3 已完成 / 4 已结束。
 * 结束原因：1 被拒绝 / 2 超时 / 3 撤回 / 4 裁决驳回。
 * <p>字段 {@code state} 只能由认领状态机改写，业务代码不得直接赋值。
 */
@Data
@TableName("claim")
public class Claim {

    @TableId(value = "claim_id", type = IdType.AUTO)
    private Long claimId;

    private Long itemId;

    /** 认领人（失主） */
    private Long applicantId;

    /** 应答人（拾得者，即信息发布者） */
    private Long respondentId;

    /** 0 待处理 / 1 待交接 / 2 仲裁中 / 3 已完成 / 4 已结束 */
    private Integer state;

    /** 1 被拒绝 / 2 超时 / 3 撤回 / 4 裁决驳回；仅 state=4 时有值 */
    private Integer endReason;

    /** 不少于 3 项特征答案的 JSON（FR-06） */
    private String evidenceJson;

    /** 凭证文件相对路径，仅拾得者与管理员可见 */
    private String voucherUrl;

    /** 凭证可见截止时间（KV-03、QR-04） */
    private LocalDateTime voucherVisibleUntil;

    /** 申请人确认时间戳（FR-11） */
    private LocalDateTime confirmAAt;

    /** 拾得者确认时间戳（FR-11） */
    private LocalDateTime confirmRAt;

    /** 超时时间：创建 +24h，进入待交接后改为 +72h（FR-10） */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
