package com.artraw.files.model

data class StorageStats(
    val rootPath: String,
    val name: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isExternalSd: Boolean = false
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercent: Int get() = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
    val freePercent: Int get() = 100 - usedPercent
}

enum class SortField {
    NAME,
    DATE,
    SIZE,
    TYPE
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class SortOption(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
) {
    fun compare(a: FileItem, b: FileItem): Int {
        // Directories always stay on top
        if (a.isDirectory && !b.isDirectory) return -1
        if (!a.isDirectory && b.isDirectory) return 1

        val result = when (field) {
            SortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
            SortField.DATE -> a.lastModified.compareTo(b.lastModified)
            SortField.SIZE -> a.size.compareTo(b.size)
            SortField.TYPE -> {
                val catCompare = a.category.compareTo(b.category)
                if (catCompare != 0) catCompare else a.extension.compareTo(b.extension, ignoreCase = true)
            }
        }
        return if (direction == SortDirection.ASCENDING) result else -result
    }
}
