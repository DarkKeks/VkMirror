package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.objects.groups.Group
import com.vk.api.sdk.objects.users.User
import ru.darkkeks.vkmirror.vk.`object`.MyConversation

// TODO: Test nullable fields
class MyGetConversationsResponse(
        var count: Int,
        val items: List<MyConversation>,
        val profiles: List<User>,
        val groups: List<Group>)