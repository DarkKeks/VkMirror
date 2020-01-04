package ru.darkkeks.vkmirror.tdlib

class TelegramException(val error_code: Int, val error: String) : RuntimeException("Telegram error #$error_code occurred: $error")