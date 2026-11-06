package edu.campus.lostfound.claim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.lostfound.data.entity.Claim;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public record ClaimView(
        Long claimId,
        Long itemId,
        Long applicantId,       // 申请人ID
        Long respondentId,      // 拾得者ID
        Integer state,          // 认领状态（0-4）
        String stateDesc,       // 状态的中文描述（前端直接展示）
        Integer endReason,      // 结束原因
        List<String> features,  // 将 evidenceJson 转换回 List
        String voucherUrl,      // 凭证路径（必须做权限控制！）
        LocalDateTime confirmAAt, // 申请人确认时间
        LocalDateTime confirmRAt, // 拾得者确认时间
        LocalDateTime expireAt,   // 超时时间
        LocalDateTime createdAt   // 申请创建时间
) {
    // 全局共用一个 ObjectMapper，避免每次 new 消耗性能
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ClaimView of(Claim claim, Long currentUserId, String currentRole) {
        if (claim == null) return null;

        // 1. 解析 JSON 特征（防止解析报错导致接口 500）
        List<String> features = Collections.emptyList();
        if (claim.getEvidenceJson() != null) {
            try {
                features = MAPPER.readValue(claim.getEvidenceJson(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                // 如果 JSON 坏了，忽略异常，不阻断流程
            }
        }

        // 2. 凭证可见性（FR-06 仅拾得者与管理员可见 + KV-03 可见期）
        String safeVoucherUrl = null;
        boolean isRespondent = currentUserId != null && currentUserId.equals(claim.getRespondentId());
        boolean isAdmin = "ADMIN".equals(currentRole);

        if (claim.getVoucherUrl() != null
                && (isRespondent || isAdmin)
                && ClaimAppService.voucherVisible(claim)) {
            safeVoucherUrl = claim.getVoucherUrl();
        }

        // 3. 状态转中文
        String desc = switch (claim.getState()) {
            case 0 -> "待处理";
            case 1 -> "待交接";
            case 2 -> "仲裁中";
            case 3 -> "已完成";
            case 4 -> "已结束";
            default -> "未知状态";
        };

        return new ClaimView(
                claim.getClaimId(),
                claim.getItemId(),
                claim.getApplicantId(),
                claim.getRespondentId(),
                claim.getState(),
                desc,
                claim.getEndReason(),
                features,         // 变成 List 返回
                safeVoucherUrl,   // 权限控制后的 URL
                claim.getConfirmAAt(),
                claim.getConfirmRAt(),
                claim.getExpireAt(),
                claim.getCreatedAt()
        );
    }
}