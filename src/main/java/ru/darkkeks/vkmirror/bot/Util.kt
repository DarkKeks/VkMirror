package ru.darkkeks.vkmirror.bot


const val BOT_FATHER = "BotFather"
const val START = "/start"
const val NEW_BOT = "/newbot"
const val SET_ABOUT = "/setabouttext"
const val SET_NAME = "/setname"


val INVALID_BOT_SELECTED = "Invalid bot selected".toRegex()


val TIMEOUT_PATTERN = "Please try again in (\\d+) seconds.".toRegex()

/**
 * @param message Response from BotFather supposedly containing timeout message
 * @return timeout in seconds, or null if none is present
 */
fun getTimeout(message: String): Int? {
    val match = TIMEOUT_PATTERN.find(message) ?: return null
    return match.groups[1]?.value?.toInt()
}

