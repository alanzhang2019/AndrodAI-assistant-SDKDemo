package com.agpting.sdkdemo;

public class ConversationItem {
    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;
    public static final int TYPE_PROMPT = 2;
    public static final int TYPE_SYSTEM = 2; // 假设 0 是用户，1 是 AI，2 是系统消息

    private String message;
    private int type;

    public ConversationItem(String message, int type) {
        this.message = message;
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public int getType() {
        return type;
    }
}
