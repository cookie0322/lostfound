package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信（FR-14）：五个关键节点各写一条，未在线时前端以登录横幅提示。
 *
 * <p>节点取值：APPLIED（新申请）/ VERDICT（确认、拒绝、转仲裁）/ CONFIRM（对方已确认）/
 * COMPLETED（已完成）/ CLOSED（已结束：超时、撤回、裁决驳回）。
 * <p>内容脱敏：不含对方真实姓名与联系方式（QR-04）。
 */
@Data
@TableName("notice")
public class Notice {

    @TableId(value = "notice_id", type = IdType.AUTO)
    private Long noticeId;

    private Long receiverId;

    private Long claimId;

    private String node;

    private String title;

    private String content;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
