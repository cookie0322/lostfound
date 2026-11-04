package edu.campus.lostfound.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 招领信息台账（属 M2 信息发布与浏览子系统，本功能只读）。
 *
 * <p>非目标：不实现发布与检索。开发期由 db/seed.sql 造数或 dev 接口写入，
 * 用于给 F2 提供 item_id 与"信息仍可认领"的判定依据（架构 1.1 表 1-1 非目标 2）。
 */
@Data
@TableName("item")
public class Item {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    private String title;

    /** 类别：钥匙 / 雨伞 / 水杯 / 教材 / 文具 / 衣物（需求 3.2 受理范围） */
    private String category;

    private String place;

    private LocalDateTime foundAt;

    /** 0 可认领 / 1 已完成 / 2 已下架 */
    private Integer status;

    /** 拾得者账号，即认领流程中的 respondent */
    private Long publisherId;

    private LocalDateTime createdAt;
}
