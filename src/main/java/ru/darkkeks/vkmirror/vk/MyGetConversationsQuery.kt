package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.client.AbstractQueryBuilder
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.objects.base.UserGroupFields

class MyGetConversationsQuery(client: VkApiClient, actor: UserActor, vararg peerIds: Int) :
        AbstractQueryBuilder<MyGetConversationsQuery, MyGetConversationsResponse>(
                client, "messages.getConversationsById", MyGetConversationsResponse::class.java) {

    init {
        accessToken(actor.accessToken)
        peerIds(*peerIds)
        extended(true)
    }

    fun extended(value: Boolean) = unsafeParameter("extended", value)

    fun groupId(value: Int) = unsafeParameter("group_id", value)

    fun peerIds(vararg value: Int) = unsafeParameter("peer_ids", *value)

    fun fields(vararg value: UserGroupFields) = unsafeParameter("fields", *value)

    override fun getThis() = this

    override fun essentialKeys() = listOf("peer_ids", "access_token")
}