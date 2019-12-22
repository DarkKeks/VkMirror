package ru.darkkeks.vkmirror.bot

import ru.darkkeks.vkmirror.util.logger

class SetNameAction(autoReg: BotAutoReg,
                    val username: String,
                    val name: String) : BotFatherAction<Unit>(autoReg) {

    override suspend fun execute() {
        val newBot = autoReg.executeCommand(SET_NAME)

        val timeout = getTimeout(newBot)
        if (timeout != null) {
            callback.completeExceptionally(BotFatherTimeoutException(timeout))
            return
        }

        val afterUsername = autoReg.executeCommand(username)

        if (INVALID_BOT_SELECTED.find(afterUsername) != null) {
            callback.completeExceptionally(IllegalStateException("Invalid username $username"))
        }

        autoReg.executeCommand(name)
        callback.complete(Unit)
    }

    companion object {
        val logger = logger()
    }
}