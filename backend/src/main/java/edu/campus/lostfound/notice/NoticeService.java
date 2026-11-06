package edu.campus.lostfound.notice;

import edu.campus.lostfound.data.entity.Notice;
import edu.campus.lostfound.data.mapper.NoticeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内信发送。
 *
 * <p>notice.node 是 NOT NULL 列，且语义上必须记录"这是哪一类节点"，
 * 所以 node 与 claim_id 是必填参数——少传一个，插入就会失败，
 * 而发送发生在业务事务里，会把整个状态迁移一起回滚。
 */
@Service
public class NoticeService {

    private final NoticeMapper noticeMapper;

    public NoticeService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /**
     * @param node APPLIED / VERDICT / CONFIRM / COMPLETED / CLOSED（FR-14 的五类节点）
     */
    public void send(Long receiverId, Long claimId, String node, String title, String content) {
        Notice notice = new Notice();
        notice.setReceiverId(receiverId);
        notice.setClaimId(claimId);
        notice.setNode(node);
        notice.setTitle(title);
        notice.setContent(content); // 内容里不能有真实姓名和联系方式（QR-04）
        notice.setCreatedAt(LocalDateTime.now());
        noticeMapper.insert(notice);
    }
}
