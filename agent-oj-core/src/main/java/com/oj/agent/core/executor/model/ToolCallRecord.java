package com.oj.agent.core.executor.model;

public class ToolCallRecord implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    private static final String TO_STRING_TEMPLATE = "{\"order\": %s, \"tool\": \"%s\", \"repliedToUser\": %s}";

    private int order;

    private String tool;

    private boolean repliedToUser;

    public ToolCallRecord() {
    }

    public ToolCallRecord(int order, String tool) {
        this.order = order;
        this.tool = tool;
        this.repliedToUser = false;
    }

    public ToolCallRecord(int order, String tool, boolean repliedToUser) {
        this.order = order;
        this.tool = tool;
        this.repliedToUser = repliedToUser;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public boolean isRepliedToUser() {
        return repliedToUser;
    }

    public void setRepliedToUser(boolean repliedToUser) {
        this.repliedToUser = repliedToUser;
    }

    @Override
    public String toString() {
        return TO_STRING_TEMPLATE.formatted(order, tool, repliedToUser);
    }
}
