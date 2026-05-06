package com.oj.agent.core.rag.config;

import com.oj.agent.core.rag.enums.DocumentSplitType;
import com.oj.agent.core.rag.model.dto.DocumentSplitParam;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag.document-split")
public class KnowledgeDocumentSplitProperties {

    /**
     * 分段类型，默认 SMART。
     */
    private String splitType = DocumentSplitType.SMART.name();

    /**
     * 分段大小，默认 1000。
     */
    private Integer chunkSize = 1500;

    /**
     * 分段重叠字符数，默认 100。
     */
    private Integer overlap = 150;

    /**
     * 标题层级，默认 3。
     */
    private Integer titleLevel = 3;

    /**
     * 分隔符，默认按空行。
     */
    private String separator = "\\n\\n";

    /**
     * 正则配置，默认匹配 markdown 标题。
     */
    private String regex = "(?m)^#{1,6}\\s";

    /**
     * 兼容配置项 chunck-size。
     */
    public void setChunckSize(Integer chunckSize) {
        if (chunckSize != null) {
            this.chunkSize = chunckSize;
        }
    }

    /**
     * 兼容配置项 seperator。
     */
    public void setSeperator(String seperator) {
        if (StringUtils.hasText(seperator)) {
            this.separator = seperator;
        }
    }

    /**
     * 转换为分段参数对象。
     */
    public DocumentSplitParam toSplitParam() {
        return new DocumentSplitParam(
                splitType,
                chunkSize,
                overlap,
                titleLevel,
                separator,
                regex
        );
    }
}
