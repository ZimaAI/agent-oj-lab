package com.oj.agent.core.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.agent.core.question.converter.AlgorithmQuestionConverter;
import com.oj.agent.core.question.mapper.TagMapper;
import com.oj.agent.core.question.model.entity.Tag;
import com.oj.agent.core.question.model.query.TagPageQuery;
import com.oj.agent.core.question.model.result.TagResult;
import com.oj.agent.core.question.service.TagService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    /**
     * 查询全部标签列表。
     */
    @Override
    public List<TagResult> listTags() {
        List<Tag> tags = this.list(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getIsDelete, 0)
                .orderByAsc(Tag::getId));
        return tags.stream().map(AlgorithmQuestionConverter::toTagResult).toList();
    }

    /**
     * 分页查询标签列表。
     */
    @Override
    public Page<TagResult> pageTags(TagPageQuery query) {
        TagPageQuery safeQuery = query == null ? new TagPageQuery() : query;
        validatePageParams(safeQuery.getCurrent(), safeQuery.getPageSize());
        Page<Tag> page = new Page<>(safeQuery.getCurrent(), safeQuery.getPageSize());
        IPage<Tag> tagPage = baseMapper.selectPageByKeyword(page, safeQuery.getKeyword());
        Page<TagResult> result = new Page<>(safeQuery.getCurrent(), safeQuery.getPageSize(), tagPage.getTotal());
        result.setRecords(tagPage.getRecords().stream().map(AlgorithmQuestionConverter::toTagResult).toList());
        return result;
    }

    // 校验分页参数为正整数。
    private void validatePageParams(long current, long pageSize) {
        if (current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("分页参数非法");
        }
    }
}
