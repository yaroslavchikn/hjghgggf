package com.manager

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileManagerRepository(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("file_manager", Context.MODE_PRIVATE)

    val root: File = Environment.getExternalStorageDirectory()
    val trashDir: File = File(context.filesDir, "trash").apply { mkdirs() }

    fun favorites(): Set<String> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun addFavorite(path: String) {
        saveFavorites(favorites() + path)
    }

    fun removeFavorite(path: String) {
        saveFavorites(favorites() - path)
    }

    private fun saveFavorites(set: Set<String>) {
        val arr = JSONArray()
        set.sorted().forEach { arr.put(it) }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }

    private fun replaceFavorite(oldPath: String, newPath: String) {
        val current = favorites().toMutableSet()
        if (current.remove(oldPath)) {
            current.add(newPath)
        }
        saveFavorites(current)
    }

    fun list(dir: File, sortMode: SortMode): List<FileItem> {
        val files = dir.listFiles()?.mapNotNull { file ->
            runCatching { file.toFileItem() }.getOrNull()
        } ?: emptyList()

        return sort(files, sortMode)
    }

    private fun sort(items: List<FileItem>, mode: SortMode): List<FileItem> {
        val comparator = when (mode) {
            SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.DATE -> compareByDescending<FileItem> { it.modified }
                .thenBy { it.name.lowercase() }
            SortMode.SIZE -> compareByDescending<FileItem> { it.size }
                .thenBy { it.name.lowercase() }
        }

        return items.sortedWith(
            compareByDescending<FileItem> { it.isDirectory }.then(comparator)
        )
    }

    fun createFolder(parent: File, name: String): Result<Unit> = runCatching {
        require(name.isNotBlank()) { "Имя не может быть пустым" }

        val folder = File(parent, name.trim())
        if (!folder.mkdir() && !folder.isDirectory) {
            error("Не удалось создать папку")
        }

        syncMediaStore(folder)
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        require(newName.isNotBlank()) { "Имя не может быть пустым" }

        val target = File(file.parentFile ?: root, newName.trim())
        if (target.exists()) {
            error("Файл с таким именем уже существует")
        }

        if (!file.renameTo(target)) {
            error("Не удалось переименовать")
        }

        replaceFavorite(file.absolutePath, target.absolutePath)
        syncMediaStore(target)
        target
    }

    fun delete(paths: List<String>, toTrash: Boolean): Result<Unit> = runCatching {
        paths.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                if (toTrash) {
                    moveToTrash(file)
                } else {
                    deleteRecursive(file)
                }

                syncMediaStore(file)
                removeFavorite(path)
            }
        }
    }

    private fun moveToTrash(file: File) {
        val target = uniqueFile(trashDir, file.name)
        moveFile(file, target)

        val item = TrashItem(
            originalPath = file.absolutePath,
            trashPath = target.absolutePath,
            name = file.name,
            isDirectory = file.isDirectory,
            deletedAt = System.currentTimeMillis(),
            size = if (file.isFile) file.length() else 0L
        )

        saveTrash(trashItems() + item)
    }

    fun trashItems(): List<TrashItem> {
        val json = prefs.getString("trash", "[]") ?: "[]"

        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<TrashItem>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TrashItem(
                        originalPath = obj.getString("originalPath"),
                        trashPath = obj.getString("trashPath"),
                        name = obj.getString("name"),
                        isDirectory = obj.getBoolean("isDirectory"),
                        deletedAt = obj.getLong("deletedAt"),
                        size = obj.optLong("size", 0L)
                    )
                )
            }

            list.filter { File(it.trashPath).exists() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveTrash(items: List<TrashItem>) {
        val arr = JSONArray()

        items.forEach { item ->
            val obj = JSONObject()
            obj.put("originalPath", item.originalPath)
            obj.put("trashPath", item.trashPath)
            obj.put("name", item.name)
            obj.put("isDirectory", item.isDirectory)
            obj.put("deletedAt", item.deletedAt)
            obj.put("size", item.size)
            arr.put(obj)
        }

        prefs.edit().putString("trash", arr.toString()).apply()
    }

    fun restoreTrash(item: TrashItem): Result<Unit> = runCatching {
        val original = File(item.originalPath)
        val parent = original.parentFile ?: root

        if (!parent.exists() && !parent.mkdirs()) {
            error("Не удалось создать папку назначения")
        }

        val target = uniqueFile(parent, item.name)
        moveFile(File(item.trashPath), target)

        saveTrash(trashItems() - item)
        syncMediaStore(target)
    }

    fun permanentDeleteTrash(items: List<TrashItem>): Result<Unit> = runCatching {
        items.forEach { item ->
            deleteRecursive(File(item.trashPath))
        }

        saveTrash(trashItems() - items.toSet())
    }

    fun copy(paths: List<String>, destination: File): Result<Unit> = runCatching {
        require(destination.isDirectory) { "Назначение не является папкой" }

        paths.forEach { path ->
            val src = File(path)
            if (src.exists()) {
                val target = uniqueFile(destination, src.name)
                copyRecursive(src, target)
                syncMediaStore(target)
            }
        }
    }

    fun move(paths: List<String>, destination: File): Result<Unit> = runCatching {
        require(destination.isDirectory) { "Назначение не является папкой" }

        paths.forEach { path ->
            val src = File(path)

            if (src.exists()) {
                if (destination.absolutePath == src.parentFile?.absolutePath) {
                    return@forEach
                }

                val destPath = destination.absolutePath
                if (destPath.startsWith(src.absolutePath + File.separator) || destPath == src.absolutePath) {
                    error("Нельзя переместить папку внутрь самой себя")
                }

                val target = uniqueFile(destination, src.name)
                moveFile(src, target)
                replaceFavorite(src.absolutePath, target.absolutePath)
                syncMediaStore(target)
            }
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val baseName = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")

        var candidate = File(dir, name)
        var counter = 1

        while (candidate.exists()) {
            val newName = if (ext.isEmpty()) {
                "$baseName ($counter)"
            } else {
                "$baseName ($counter).$ext"
            }

            candidate = File(dir, newName)
            counter++
        }

        return candidate
    }

    private fun moveFile(src: File, dest: File) {
        if (!src.renameTo(dest)) {
            copyRecursive(src, dest)
            deleteRecursive(src)
        }
    }

    private fun copyRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists() && !dest.mkdirs()) {
                error("Не удалось создать папку ${dest.name}")
            }

            src.listFiles()?.forEach { child ->
                copyRecursive(child, File(dest, child.name))
            }
        } else {
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }

        return file.delete()
    }

    private fun syncMediaStore(file: File) {
        try {
            val uri = android.provider.MediaStore.Files.getContentUri("external")

            context.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                "${android.provider.MediaStore.MediaColumns.DATA}=?",
                arrayOf(file.absolutePath),
                null
            )?.use { }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (_: Exception) {
        }
    }
}
