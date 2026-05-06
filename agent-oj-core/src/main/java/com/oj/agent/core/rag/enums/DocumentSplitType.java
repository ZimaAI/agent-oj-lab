package com.oj.agent.core.rag.enums;

public enum DocumentSplitType {

    /**
     * 按长度切分
     */
    LENGTH,

    /**
     * 按标题切分
     */
    TITLE,

    /**
     * 按正则切分
     */
    REGEX,

    /**
     * 智能切分
     */
    SMART,

    /**
     * 按分隔符切分
     */
    SEPARATOR;
}
