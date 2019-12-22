package ru.darkkeks.vkmirror.bot

class BotFatherTimeoutException(val timeout: Int)
    : RuntimeException("Encountered BotFather timeout: $timeout seconds")