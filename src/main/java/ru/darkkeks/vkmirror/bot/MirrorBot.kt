package ru.darkkeks.vkmirror.bot

import org.litote.kmongo.Id

const val ID_NOT_ASSIGNED = -1

data class MirrorBot(val _id: Id<MirrorBot>,
                     val username: String,
                     val token: String,
                     val vkId: Int = ID_NOT_ASSIGNED)