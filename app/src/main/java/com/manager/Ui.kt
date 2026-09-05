package com.manager

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AppScreen(viewModel: MainViewModel) {
    val ui by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalContext.current as? Activity

    BackHandler {
        if (!viewModel.handleBack()) {
            activity?.finish()
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = ui.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { TabIcon(tab) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = ui.selectedTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1

                    (slideInHorizontally { width -> width * direction } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width * direction } + fadeOut()
                    )
                },
                label = "tabs"
            ) { tab ->
                when (tab) {
                    BottomTab.PHONE -> FileBrowserScreen(viewModel, ui)
                    BottomTab.FAVORITES -> FavoritesScreen(viewModel, ui)
                    BottomTab.TRASH -> TrashScreen(viewModel, ui)
                }
            }
        }
    }

    ui.viewer?.let { viewer ->
        ViewerHost(viewer = viewer, onClose = viewModel::closeViewer)
    }

    ui.dialog?.let { dialog ->
        DialogHost(dialog = dialog, viewModel = viewModel)
    }
}

@Composable
fun TabIcon(tab: BottomTab) {
    when (tab) {
        BottomTab.PHONE -> Icon(Icons.Default.Folder, contentDescription = null)
        BottomTab.FAVORITES -> Icon(Icons.Default.Favorite, contentDescription = null)
        BottomTab.TRASH -> Icon(Icons.Default.Delete, contentDescription = null)
    }
}

@Composable
fun FileBrowserScreen(viewModel: MainViewModel, ui: UiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        FileBrowserTopBar(viewModel, ui)

        if (ui.items.isEmpty()) {
            EmptyView("Папка пуста")
        } else if (ui.viewMode == ViewMode.LIST) {
            FileList(ui, viewModel)
        } else {
            FileGrid(ui, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserTopBar(viewModel: MainViewModel, ui: UiState) {
    val title = if (ui.selectionMode) {
        "${ui.selectedPaths.size} выбрано"
    } else if (ui.currentPath == viewModel.rootPath) {
        "Мой телефон"
    } else {
        File(ui.currentPath).name
    }

    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (ui.selectionMode) {
                IconButton(onClick = viewModel::clearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Отмена")
                }
            } else {
                IconButton(
                    onClick = { viewModel.goBack() },
                    enabled = ui.currentPath != viewModel.rootPath
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            }
        },
        actions = {
            if (ui.selectionMode) {
                SelectionActions(viewModel, ui)
            } else {
                BrowserActions(viewModel, ui)
            }
        }
    )
}

@Composable
fun SelectionActions(viewModel: MainViewModel, ui: UiState) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = viewModel::selectAll) {
        Icon(Icons.Default.SelectAll, contentDescription = "Выбрать все")
    }

    IconButton(onClick = viewModel::showDeleteOptions) {
        Icon(Icons.Default.Delete, contentDescription = "Удалить")
    }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Копировать") },
                onClick = {
                    menuExpanded = false
                    viewModel.showMoveCopyDialog(MoveCopyMode.COPY)
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
            )

            DropdownMenuItem(
                text = { Text("Переместить") },
                onClick = {
                    menuExpanded = false
                    viewModel.showMoveCopyDialog(MoveCopyMode.MOVE)
                },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
            )

            DropdownMenuItem(
                text = { Text("Переименовать") },
                enabled = ui.selectedPaths.size == 1,
                onClick = {
                    menuExpanded = false
                    viewModel.showRenameDialog()
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )

            val allFavorite = ui.selectedPaths.isNotEmpty() &&
                    ui.selectedPaths.all { ui.favorites.contains(it) }

            DropdownMenuItem(
                text = {
                    Text(if (allFavorite) "Убрать из избранного" else "В избранное")
                },
                onClick = {
                    menuExpanded = false
                    viewModel.toggleFavoriteSelected()
                },
                leadingIcon = {
                    Icon(
                        if (allFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@Composable
fun BrowserActions(viewModel: MainViewModel, ui: UiState) {
    var sortExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = viewModel::toggleViewMode) {
        Icon(
            if (ui.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
            contentDescription = "Переключить вид"
        )
    }

    IconButton(onClick = viewModel::showCreateFolderDialog) {
        Icon(Icons.Default.CreateNewFolder, contentDescription = "Создать папку")
    }

    Box {
        IconButton(onClick = { sortExpanded = true }) {
            Icon(Icons.Default.Sort, contentDescription = "Сортировка")
        }

        DropdownMenu(
            expanded = sortExpanded,
            onDismissRequest = { sortExpanded = false }
        ) {
            SortMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = { Text("Сортировка по: ${mode.label.lowercase()}") },
                    onClick = {
                        viewModel.setSortMode(mode)
                        sortExpanded = false
                    },
                    leadingIcon = {
                        if (ui.sortMode == mode) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileList(ui: UiState, viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(ui.items, key = { it.path }) { item ->
            val selected = ui.selectedPaths.contains(item.path)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { viewModel.onClickFile(item) },
                        onLongClick = { viewModel.onLongClickFile(item) }
                    )
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = fileIcon(item),
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = if (item.isDirectory) {
                            item.modified.formatDateTime()
                        } else {
                            "${item.size.formatSize()} · ${item.modified.formatDateTime()}"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (ui.favorites.contains(item.path)) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileGrid(ui: UiState, viewModel: MainViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(ui.items, key = { it.path }) { item ->
            val selected = ui.selectedPaths.contains(item.path)

            Surface(
                modifier = Modifier
                    .padding(4.dp)
                    .height(140.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = { viewModel.onClickFile(item) },
                            onLongClick = { viewModel.onLongClickFile(item) }
                        )
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = fileIcon(item),
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun fileIcon(item: FileItem): ImageVector {
    val file = File(item.path)

    return when {
        item.isDirectory -> Icons.Default.Folder
        file.isImage() -> Icons.Default.Image
        file.isVideo() -> Icons.Default.Movie
        file.isAudio() -> Icons.Default.MusicNote
        else -> Icons.Default.InsertDriveFile
    }
}

@Composable
fun EmptyView(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: MainViewModel, ui: UiState) {
    val favoriteItems = remember(ui.favorites) {
        ui.favorites.map { path ->
            val file = File(path)
            if (file.exists()) {
                file.toFileItem()
            } else {
                FileItem(
                    path = path,
                    name = path.substringAfterLast('/'),
                    isDirectory = false,
                    size = 0L,
                    modified = 0L
                )
            }
        }
    }

    if (favoriteItems.isEmpty()) {
        EmptyView("Нет избранных файлов")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(favoriteItems, key = { it.path }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { viewModel.openFavorite(item.path) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = fileIcon(item),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = item.path,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    IconButton(onClick = { viewModel.removeFavorite(item.path) }) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Убрать из избранного",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun TrashScreen(viewModel: MainViewModel, ui: UiState) {
    if (ui.trash.isEmpty()) {
        EmptyView("Корзина пуста")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(ui.trash, key = { it.trashPath }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "Удалено: ${item.deletedAt.formatDateTime()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    IconButton(onClick = { viewModel.restoreTrash(item) }) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Восстановить",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = { viewModel.permanentDeleteTrash(item) }) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Удалить навсегда",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun DialogHost(dialog: DialogState, viewModel: MainViewModel) {
    when (dialog) {
        DialogState.CreateFolder -> {
            TextDialog(
                title = "Новая папка",
                label = "Имя папки",
                initial = "",
                confirmText = "Создать",
                onConfirm = viewModel::createFolder,
                onDismiss = viewModel::closeDialog
            )
        }

        is DialogState.Rename -> {
            TextDialog(
                title = "Переименовать",
                label = "Новое имя",
                initial = dialog.initialName,
                confirmText = "OK",
                onConfirm = viewModel::rename,
                onDismiss = viewModel::closeDialog
            )
        }

        DialogState.DeleteOptions -> {
            AlertDialog(
                onDismissRequest = viewModel::closeDialog,
                title = { Text("Удаление") },
                text = { Text("Что сделать с выбранными элементами?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteSelected(true) }) {
                        Text("В корзину")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.deleteSelected(false) }) {
                        Text("Удалить навсегда")
                    }
                }
            )
        }

        is DialogState.MoveCopy -> {
            MoveCopyDialog(dialog, viewModel)
        }
    }
}

@Composable
fun TextDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun MoveCopyDialog(dialog: DialogState.MoveCopy, viewModel: MainViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeDialog,
        title = { Text(dialog.mode.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                Text(
                    text = "Текущая папка: ${File(dialog.currentPath).name}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (dialog.currentPath != viewModel.rootPath) {
                    TextButton(onClick = viewModel::moveCopyUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вверх")
                    }
                }

                Divider()

                if (dialog.folders.isEmpty()) {
                    Text(
                        text = "Нет папок",
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn {
                        items(dialog.folders, key = { it.path }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = {
                                        viewModel.moveCopyNavigate(folder.path)
                                    })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = folder.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmMoveCopy) {
                Text("Выбрать текущую папку")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeDialog) {
                Text("Отмена")
            }
        }
    )
}
