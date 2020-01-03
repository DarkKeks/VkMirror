package ru.darkkeks.vkmirror.bot

import ru.darkkeks.vkmirror.util.createLogger

// TODO Extract common class with SetNameAction
class SetDescriptionAction(autoReg: BotAutoReg,
                           val username: String,
                           val description: String) : BotFatherAction<Unit>(autoReg) {

    override suspend fun execute() {
        val newBot = autoReg.executeCommand(SET_DESCRIPTION)

        val timeout = getTimeout(newBot)
        if (timeout != null) {
            callback.completeExceptionally(BotFatherTimeoutException(timeout))
            return
        }

        val afterUsername = autoReg.executeCommand(username)

        if (INVALID_BOT_SELECTED.find(afterUsername) != null) {
            callback.completeExceptionally(IllegalStateException("Invalid username $username"))
        }

        autoReg.executeCommand(description)
        callback.complete(Unit)
    }

    companion object {
        val logger = createLogger()
    }
}