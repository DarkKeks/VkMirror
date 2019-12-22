package ru.darkkeks.vkmirror.bot

import kotlinx.coroutines.CompletableDeferred

abstract class BotFatherAction<T>(val autoReg: BotAutoReg) {
    val callback = CompletableDeferred<T>()

    abstract suspend fun execute()
}