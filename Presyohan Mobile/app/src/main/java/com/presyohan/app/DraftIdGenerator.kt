package com.presyohan.app

import java.util.UUID

object DraftIdGenerator {
    fun next(prefix: String): String {
        return "$prefix-${UUID.randomUUID()}"
    }
}
