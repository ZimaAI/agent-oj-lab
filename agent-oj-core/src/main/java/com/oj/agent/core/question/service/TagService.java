package com.oj.agent.core.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.oj.agent.core.question.model.entity.Tag;
import com.oj.agent.core.question.model.query.TagPageQuery;
import com.oj.agent.core.question.model.result.TagResult;

import java.util.List;

public interface TagService extends IService<Tag> {

    /**
     * 查询全部标签列表。
     */
    List<TagResult> listTags();

    /**
     * 分页查询标签列表。
     */
    Page<TagResult> pageTags(TagPageQuery query);
}
