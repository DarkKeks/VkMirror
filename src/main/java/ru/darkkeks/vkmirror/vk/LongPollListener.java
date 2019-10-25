package ru.darkkeks.vkmirror.vk;

import ru.darkkeks.vkmirror.vk.object.Message;

public interface LongPollListener {
    void newMessage(Message message);
}
