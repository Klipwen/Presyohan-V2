package com.presyohan.app

enum class ItemStatus {
    NEW,
    UPDATE,
    DUPLICATE,
    ERROR_NO_PRICE,
    ERROR_INVALID_FORMAT,
    ERROR_NO_CATEGORY
}

data class ParsedItem(
    val name: String,
    val description: String?,
    val unit: String,
    val price: Double?,
    var status: ItemStatus
)

data class ParsedCategory(
    val name: String,
    val items: MutableList<ParsedItem> = mutableListOf()
)

