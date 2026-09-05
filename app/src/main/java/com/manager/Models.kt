package com.manager

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BottomTab(val label: String) {
    PHONE("Мой телефон"),
    FAVORITES("Избранное"),
    TRASH("Корзина")
}

enum class SortMode(val label: String) {
    NAME("Имя"),
    DATE("Дата"),
    SIZE("Размер")
}

enum class ViewMode {
    LIST,
    GRID
}

enum class MoveCopyMode(val title: String) {
    COPY("Копировать"),
    MOVE("Переместить")
}

data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long
)

data class TrashItem(
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val isDirectory: Boolean,
    val deletedAt: Long,
    val size: Long
)

sealed interface DialogState {
    object CreateFolder : DialogState
    object DeleteOptions : DialogState

    data class Rename(
        val path: String,
        val initialName: String
    ) : DialogState

    data class MoveCopy(
        val mode: MoveCopyMode,
        val currentPath: String,
        val folders: List<FileItem>
    ) : DialogState
}

sealed interface ViewerState {
    data class Image(val path: String) : ViewerState
    data class Media(val path: String, val isVideo: Boolean) : ViewerState
}

fun File.toFileItem(): FileItem {
    return FileItem(
        path = absolutePath,
        name = name,
        isDirectory = isDirectory,
        size = if (isFile) length() else 0L,
        modified = lastModified()
    )
}

fun Long.formatSize(): String {
    if (this <= 0) return "0 Б"

    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = this.toDouble()
    var index = 0

    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }

    return if (index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

fun Long.formatDateTime(): String {
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(this))
}

fun File.isImage(): Boolean {
    return extension.lowercase() in listOf("jpg", "jpeg", "png")
}

fun File.isVideo(): Boolean {
    return extension.lowercase() == "mp4"
}

fun File.isAudio(): Boolean {
    return extension.lowercase() == "mp3"
}
