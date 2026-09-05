package com.manager

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val items: List<FileItem> = emptyList(),
    val selectedTab: BottomTab = BottomTab.PHONE,
    val viewMode: ViewMode = ViewMode.LIST,
    val sortMode: SortMode = SortMode.NAME,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val trash: List<TrashItem> = emptyList(),
    val viewer: ViewerState? = null,
    val dialog: DialogState? = null,
    val message: String? = null
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = FileManagerRepository(application)

    val rootPath: String = repository.root.absolutePath

    private val _state = MutableStateFlow(UiState(currentPath = rootPath))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val current = File(_state.value.currentPath)
        val safeCurrent = if (current.exists() && current.canRead()) current else repository.root

        val items = repository.list(safeCurrent, _state.value.sortMode)

        _state.update {
            it.copy(
                currentPath = safeCurrent.absolutePath,
                items = items,
                favorites = repository.favorites(),
                trash = repository.trashItems()
            )
        }
    }

    fun selectTab(tab: BottomTab) {
        _state.update {
            it.copy(
                selectedTab = tab,
                selectionMode = false,
                selectedPaths = emptySet()
            )
        }
    }

    fun openFolder(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            _state.update {
                it.copy(
                    currentPath = dir.absolutePath,
                    selectionMode = false,
                    selectedPaths = emptySet()
                )
            }
            refresh()
        }
    }

    fun goBack(): Boolean {
        val current = File(_state.value.currentPath)
        val parent = current.parentFile

        if (
            parent != null &&
            current.absolutePath != rootPath &&
            (parent.absolutePath.startsWith(rootPath) || parent.absolutePath == rootPath)
        ) {
            openFolder(parent.absolutePath)
            return true
        }

        return false
    }

    fun toggleViewMode() {
        _state.update {
            it.copy(
                viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
            )
        }
    }

    fun setSortMode(mode: SortMode) {
        _state.update { it.copy(sortMode = mode) }
        refresh()
    }

    fun onClickFile(item: FileItem) {
        if (_state.value.selectionMode) {
            toggleSelection(item.path)
        } else if (item.isDirectory) {
            openFolder(item.path)
        } else {
            openFile(item.path)
        }
    }

    fun onLongClickFile(item: FileItem) {
        if (_state.value.selectionMode) {
            toggleSelection(item.path)
        } else {
            _state.update {
                it.copy(
                    selectionMode = true,
                    selectedPaths = setOf(item.path)
                )
            }
        }
    }

    fun toggleSelection(path: String) {
        _state.update { state ->
            val set = state.selectedPaths.toMutableSet()
            if (!set.add(path)) {
                set.remove(path)
            }

            state.copy(
                selectedPaths = set,
                selectionMode = set.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _state.update { state ->
            state.copy(selectedPaths = state.items.map { it.path }.toSet())
        }
    }

    fun clearSelection() {
        _state.update {
            it.copy(
                selectionMode = false,
                selectedPaths = emptySet()
            )
        }
    }

    fun closeDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun closeViewer() {
        _state.update { it.copy(viewer = null) }
    }

    fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun openFile(path: String) {
        val file = File(path)

        if (!file.exists()) {
            showMessage("Файл не найден")
            return
        }

        when {
            file.isImage() -> {
                _state.update { it.copy(viewer = ViewerState.Image(path)) }
            }

            file.isVideo() -> {
                _state.update { it.copy(viewer = ViewerState.Media(path, true)) }
            }

            file.isAudio() -> {
                _state.update { it.copy(viewer = ViewerState.Media(path, false)) }
            }

            else -> openWithSystem(file)
        }
    }

    private fun openWithSystem(file: File) {
        try {
            val context = getApplication<Application>()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(intent, "Открыть файл")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            showMessage("Нет приложения для открытия")
        }
    }

    fun showCreateFolderDialog() {
        _state.update { it.copy(dialog = DialogState.CreateFolder) }
    }

    fun createFolder(name: String) {
        val parent = File(_state.value.currentPath)

        viewModelScope.launch(Dispatchers.IO) {
            repository.createFolder(parent, name)
                .onSuccess {
                    showMessage("Папка создана")
                    _state.update { it.copy(dialog = null) }
                }
                .onFailure {
                    showMessage(it.message ?: "Ошибка создания папки")
                }

            refresh()
        }
    }

    fun showRenameDialog() {
        val path = _state.value.selectedPaths.firstOrNull() ?: return
        val file = File(path)

        _state.update {
            it.copy(dialog = DialogState.Rename(path = path, initialName = file.name))
        }
    }

    fun rename(newName: String) {
        val dialog = _state.value.dialog as? DialogState.Rename ?: return

        viewModelScope.launch(Dispatchers.IO) {
            repository.rename(File(dialog.path), newName)
                .onSuccess {
                    showMessage("Переименовано")
                    _state.update {
                        it.copy(
                            dialog = null,
                            selectionMode = false,
                            selectedPaths = emptySet()
                        )
                    }
                }
                .onFailure {
                    showMessage(it.message ?: "Ошибка переименования")
                }

            refresh()
        }
    }

    fun showDeleteOptions() {
        _state.update { it.copy(dialog = DialogState.DeleteOptions) }
    }

    fun deleteSelected(toTrash: Boolean) {
        val paths = _state.value.selectedPaths.toList()

        _state.update { it.copy(dialog = null) }

        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(paths, toTrash)
                .onSuccess {
                    showMessage(if (toTrash) "Перемещено в корзину" else "Удалено")
                }
                .onFailure {
                    showMessage(it.message ?: "Ошибка удаления")
                }

            clearSelection()
            refresh()
        }
    }

    fun toggleFavoriteSelected() {
        val selected = _state.value.selectedPaths
        val favorites = _state.value.favorites
        val allFavorite = selected.isNotEmpty() && selected.all { favorites.contains(it) }

        viewModelScope.launch(Dispatchers.IO) {
            selected.forEach { path ->
                if (allFavorite) {
                    repository.removeFavorite(path)
                } else {
                    repository.addFavorite(path)
                }
            }

            refresh()
        }
    }

    fun removeFavorite(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeFavorite(path)
            refresh()
        }
    }

    fun openFavorite(path: String) {
        val file = File(path)

        if (!file.exists()) {
            showMessage("Файл недоступен")
            removeFavorite(path)
            return
        }

        if (file.isDirectory) {
            selectTab(BottomTab.PHONE)
            openFolder(path)
        } else {
            openFile(path)
        }
    }

    fun showMoveCopyDialog(mode: MoveCopyMode) {
        val start = _state.value.currentPath
        val folders = repository.list(File(start), _state.value.sortMode)
            .filter { it.isDirectory }

        _state.update {
            it.copy(dialog = DialogState.MoveCopy(mode, start, folders))
        }
    }

    fun moveCopyNavigate(path: String) {
        val dialog = _state.value.dialog as? DialogState.MoveCopy ?: return
        val dir = File(path)

        if (!dir.isDirectory) return

        val folders = repository.list(dir, _state.value.sortMode)
            .filter { it.isDirectory }

        _state.update {
            it.copy(dialog = dialog.copy(currentPath = path, folders = folders))
        }
    }

    fun moveCopyUp() {
        val dialog = _state.value.dialog as? DialogState.MoveCopy ?: return
        val parent = File(dialog.currentPath).parentFile

        if (parent != null && dialog.currentPath != rootPath) {
            moveCopyNavigate(parent.absolutePath)
        }
    }

    fun confirmMoveCopy() {
        val dialog = _state.value.dialog as? DialogState.MoveCopy ?: return

        val paths = _state.value.selectedPaths.toList()
        val dest = File(dialog.currentPath)
        val mode = dialog.mode

        _state.update { it.copy(dialog = null) }

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (mode == MoveCopyMode.COPY) {
                repository.copy(paths, dest)
            } else {
                repository.move(paths, dest)
            }

            result
                .onSuccess {
                    showMessage(if (mode == MoveCopyMode.COPY) "Скопировано" else "Перемещено")
                }
                .onFailure {
                    showMessage(it.message ?: "Ошибка операции")
                }

            clearSelection()
            refresh()
        }
    }

    fun restoreTrash(item: TrashItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreTrash(item)
                .onSuccess { showMessage("Восстановлено") }
                .onFailure { showMessage(it.message ?: "Ошибка восстановления") }

            refresh()
        }
    }

    fun permanentDeleteTrash(item: TrashItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.permanentDeleteTrash(listOf(item))
                .onSuccess { showMessage("Удалено из корзины") }
                .onFailure { showMessage(it.message ?: "Ошибка удаления") }

            refresh()
        }
    }

    fun handleBack(): Boolean {
        val st = _state.value

        if (st.viewer != null) {
            closeViewer()
            return true
        }

        if (st.dialog != null) {
            closeDialog()
            return true
        }

        if (st.selectionMode) {
            clearSelection()
            return true
        }

        if (st.selectedTab == BottomTab.PHONE) {
            if (goBack()) return true
        }

        if (st.selectedTab != BottomTab.PHONE) {
            selectTab(BottomTab.PHONE)
            return true
        }

        return false
    }
}
