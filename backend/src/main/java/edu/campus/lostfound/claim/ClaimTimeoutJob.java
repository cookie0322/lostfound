package edu.campus.lostfound.claim;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.lostfound.data.entity.Claim;
import edu.campus.lostfound.data.mapper.ClaimMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Component
public class ClaimTimeoutJob {

    private final ClaimMapper claimMapper;
    private final ClaimAppService claimAppService;

    public ClaimTimeoutJob(ClaimMapper claimMapper, ClaimAppService claimAppService) {
        this.claimMapper = claimMapper;
        this.claimAppService = claimAppService;
    }

    // 每 60 秒执行一次
    @Scheduled(fixedDelay = 60000)
    public void closeExpired() {
        // 查出 state 为 0或1，且已超时的记录
        List<Claim> expiredList = claimMapper.selectList(new LambdaQueryWrapper<Claim>()
                .in(Claim::getState, 0, 1)
                .le(Claim::getExpireAt, LocalDateTime.now())
                .last("LIMIT 100")
        );

        for (Claim claim : expiredList) {
            try {
                claimAppService.processTimeout(claim);
                Logger.getGlobal().info("成功处理超时认领: " + claim.getClaimId());
            } catch (Exception e) {
                // 如果抛出并发异常，跳过，让别的线程处理
                Logger.getGlobal().info("超时任务处理跳过: " + e.getMessage());
            }
        }
    }
}