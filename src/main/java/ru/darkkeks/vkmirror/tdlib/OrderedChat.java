package ru.darkkeks.vkmirror.tdlib;

public class OrderedChat implements Comparable<OrderedChat> {

    private long chatId;
    private long order;

    public OrderedChat(long chatId, long order) {
        this.chatId = chatId;
        this.order = order;
    }

    public long getChatId() {
        return chatId;
    }

    public long getOrder() {
        return order;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int compareTo(OrderedChat o) {
        if(order != o.order) return (order < o.order ? 1 : -1);
        return Long.compare(chatId, o.chatId);
    }
}
