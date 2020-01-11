package ru.darkkeks.vkmirror.tdlib

import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.tdlib.internal.TdApi.CheckAuthenticationBotToken
import ru.darkkeks.vkmirror.tdlib.internal.TdApi.SetAuthenticationPhoneNumber

fun botTelegramCredentials(apiId: Int, apiHash: String, botToken: String) =
        TelegramCredentials(apiId, apiHash, true, botToken = botToken)

fun userTelegramCredentials(apiId: Int, apiHash: String, phoneNumber: String) =
        TelegramCredentials(apiId, apiHash, false, phoneNumber = phoneNumber)

class TelegramCredentials(
        val apiId: Int,
        val apiHash: String,
        private val isBot: Boolean,
        private val phoneNumber: String? = null,
        private val botToken: String? = null) {

    val credentialsFunction: TdApi.Function get() = when {
        isBot -> CheckAuthenticationBotToken(botToken)
        else -> SetAuthenticationPhoneNumber(phoneNumber, TdApi.PhoneNumberAuthenticationSettings(false, true, false))
    }

    val dataDirectory: String get() = when {
        isBot -> "tdlib-$strippedToken"
        else -> "tdlib$phoneNumber"
    }

    private val strippedToken: String
        get() = botToken?.substring(0, botToken.indexOf(":")) ?: ""
}