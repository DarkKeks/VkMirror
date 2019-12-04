package ru.darkkeks.vkmirror.vk

import com.google.gson.JsonArray

data class GetUserLongPollEventsResponse(var ts: Int, var updates: List<JsonArray>)
