package edu.campus.lostfound.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.campus.lostfound.data.entity.Notice;
import edu.campus.lostfound.data.mapper.NoticeMapper;
import edu.campus.lostfound.web.ApiResult;
import edu.campus.lostfound.web.BizException;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeMapper noticeMapper;

    public NoticeController(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /** 当前用户未读 + 已读的站内信，按时间倒序（未登录必须 401，不能返回空列表） */
    @GetMapping
    public ApiResult<List<Notice>> list(HttpSession session) {
        Long userId = currentUserId(session);
        List<Notice> list = noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getReceiverId, userId)
                .orderByDesc(Notice::getCreatedAt));
        return ApiResult.ok(list);
    }

    /**
     * 标记已读。
     * <p>不能用 updateById：只 set 一个 id 时，MyBatis-Plus 生成的是没有 SET 子句的
     * UPDATE 语句，MySQL 直接报语法错误。必须显式 set 字段。
     */
    @PostMapping("/{id}/read")
    public ApiResult<Void> read(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw BizException.notFound("通知不存在");
        }
        if (!notice.getReceiverId().equals(userId)) {
            throw BizException.forbidden("无权操作此通知");
        }
        noticeMapper.update(null, new LambdaUpdateWrapper<Notice>()
                .eq(Notice::getNoticeId, id)
                .set(Notice::getReadAt, LocalDateTime.now()));
        return ApiResult.ok();
    }

    private Long currentUserId(HttpSession session) {
        Long userId = (Long) session.getAttribute("CURRENT_USER_ID");
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        return userId;
    }
}
