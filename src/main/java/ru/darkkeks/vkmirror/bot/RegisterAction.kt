package ru.darkkeks.vkmirror.bot

import ru.darkkeks.vkmirror.util.createLogger

val ALREADY_TAKEN = "already taken".toRegex()
val TOKEN = """\d+:\w+""".toRegex()

class RegisterAction(autoReg: BotAutoReg,
                     val username: String,
                     val name: String) : BotFatherAction<String>(autoReg) {

    override suspend fun execute() {
        val newBot = autoReg.executeCommand(NEW_BOT)

        val timeout = getTimeout(newBot)
        if (timeout != null) {
            callback.completeExceptionally(BotFatherTimeoutException(timeout))
            return
        }

        val afterName = autoReg.executeCommand(name)
        val afterUsername = autoReg.executeCommand(username)

        if (ALREADY_TAKEN.find(afterUsername) != null) {
            callback.completeExceptionally(IllegalStateException("Bot username $username is already taken"))
            return
        }

        val match = TOKEN.find(afterUsername)
        if (match != null) {
            callback.complete(match.value)
        } else {
            logger.error("No token in BotFather response!:\n$afterUsername")
            callback.completeExceptionally(IllegalStateException("No token in response"))
        }
    }

    companion object {
        val logger = createLogger<RegisterAction>()
    }
}