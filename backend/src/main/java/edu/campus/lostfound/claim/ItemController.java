package edu.campus.lostfound.claim;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.lostfound.data.entity.Item;
import edu.campus.lostfound.data.mapper.ItemMapper;
import edu.campus.lostfound.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 招领信息台账的只读接口（属 M2 信息发布与浏览子系统，本功能不实现发布）。
 *
 * <p>架构 1.1 非目标 2 写的是"M2 仅由信息台账提供数据与只读查询，供 F2 消费"，
 * 所以这里只暴露一个查询，开发期数据来自 db/02-seed.sql。
 * 浏览不需要认证（需求 3.2 认证门槛：仅发布信息不需要认证）。
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemMapper itemMapper;

    public ItemController(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @GetMapping
    public ApiResult<List<Item>> list() {
        return ApiResult.ok(itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, 0)
                .orderByDesc(Item::getCreatedAt)));
    }
}
