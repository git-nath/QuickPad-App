package com.example.quickpad

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.quickpad.data.AppDatabase
import com.example.quickpad.data.VideoEntity
import com.example.quickpad.data.VideoRepository
import com.example.quickpad.ui.theme.QuickPadTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ALL_FOLDERS = "All"
private const val DEFAULT_FOLDER = "General"

class MainActivity : ComponentActivity() {
    private val viewModel: VideoViewModel by viewModels {
        VideoViewModelFactory(
            VideoRepository(
                AppDatabase.getInstance(applicationContext).videoDao()
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickPadTheme {
                val navController = rememberNavController()
                val videos by viewModel.videos.collectAsStateWithLifecycle()

                AppNavigation(
                    navController = navController,
                    videos = videos,
                    onSaveVideo = viewModel::addVideo,
                    onUpdateVideo = viewModel::updateVideo
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    navController: NavHostController,
    videos: List<VideoEntity>,
    onSaveVideo: (String, String, String, () -> Unit) -> Unit,
    onUpdateVideo: (Long, String, String, () -> Unit) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    videos = videos,
                    onAddClick = { navController.navigate("add") },
                    onVideoClick = { videoId -> navController.navigate("player/$videoId") },
                    onSaveEdit = { id, caption, folder ->
                        onUpdateVideo(id, caption, folder) {
                            scope.launch { snackbarHostState.showSnackbar("Video updated") }
                        }
                    }
                )
            }
            composable("add") {
                AddVideoScreen(
                    onBack = { navController.popBackStack() },
                    onSave = { uri, caption, folder ->
                        onSaveVideo(uri, caption, folder) {
                            navController.popBackStack()
                        }
                    },
                    onInvalid = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Pick a video and add a caption")
                        }
                    }
                )
            }
            composable("player/{videoId}") { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId")?.toLongOrNull()
                val video = videos.firstOrNull { it.id == videoId }

                if (video == null) {
                    scope.launch { snackbarHostState.showSnackbar("Video not found") }
                    navController.popBackStack()
                } else {
                    VideoPlayerScreen(
                        video = video,
                        onBack = { navController.popBackStack() },
                        onPlayFailed = {
                            scope.launch { snackbarHostState.showSnackbar("Unable to play this video") }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    videos: List<VideoEntity>,
    onAddClick: () -> Unit,
    onVideoClick: (Long) -> Unit,
    onSaveEdit: (Long, String, String) -> Unit
) {
    var showFolderDialog by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf(ALL_FOLDERS) }
    var editingVideo by remember { mutableStateOf<VideoEntity?>(null) }

    val knownFolders = remember(videos) {
        (videos.map { it.folder }.filter { it.isNotBlank() } + DEFAULT_FOLDER).distinct().sorted()
    }
    val filteredVideos = remember(videos, selectedFolder) {
        if (selectedFolder == ALL_FOLDERS) videos else videos.filter { it.folder == selectedFolder }
    }

    if (showFolderDialog) {
        FolderManagerDialog(
            folders = knownFolders,
            selectedFolder = selectedFolder,
            onSelectFolder = { selectedFolder = it },
            onDismiss = { showFolderDialog = false }
        )
    }

    editingVideo?.let { video ->
        EditVideoDialog(
            video = video,
            knownFolders = knownFolders,
            onDismiss = { editingVideo = null },
            onSave = { caption, folder ->
                onSaveEdit(video.id, caption, folder)
                editingVideo = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QuickPad (${filteredVideos.size})") },
                actions = {
                    TextButton(onClick = { showFolderDialog = true }) {
                        Text(selectedFolder)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add video")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredVideos, key = { it.id }) { video ->
                VideoItem(
                    video = video,
                    onVideoClick = onVideoClick,
                    onEditClick = { editingVideo = video }
                )
            }
        }
    }
}

@Composable
private fun VideoItem(
    video: VideoEntity,
    onVideoClick: (Long) -> Unit,
    onEditClick: () -> Unit
) {
    val videoUri = remember(video.uri) { video.uri.toUri() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onVideoClick(video.id) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            VideoThumbnail(
                uri = videoUri,
                description = "Video thumbnail",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = video.caption,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Folder: ${video.folder}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onEditClick) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(uri: Uri, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnail = produceState<Bitmap?>(initialValue = null, uri) {
        value = loadVideoThumbnail(context = context, uri = uri)
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (thumbnail.value != null) {
            Image(
                bitmap = thumbnail.value!!.asImageBitmap(),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .crossfade(true)
                    .build(),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private suspend fun loadVideoThumbnail(context: android.content.Context, uri: Uri): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            frame
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayerScreen(
    video: VideoEntity,
    onBack: () -> Unit,
    onPlayFailed: () -> Unit
) {
    val videoUri = remember(video.uri) { video.uri.toUri() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(video.caption) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    VideoView(it).apply {
                        val mediaController = MediaController(it)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)
                        setVideoURI(videoUri)
                        setOnPreparedListener { start() }
                        setOnErrorListener { _, _, _ ->
                            onPlayFailed()
                            true
                        }
                    }
                }
            )

            Text(
                text = video.caption,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVideoScreen(
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onInvalid: () -> Unit
) {
    val context = LocalContext.current
    var caption by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf(DEFAULT_FOLDER) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedUri = it.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Video") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = { pickerLauncher.launch(arrayOf("video/*")) }) {
                Text(if (selectedUri == null) "Pick video" else "Change video")
            }

            selectedUri?.toUri()?.let {
                VideoThumbnail(
                    uri = it,
                    description = "Selected video preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption") },
                placeholder = { Text("Add a short note") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            OutlinedTextField(
                value = folder,
                onValueChange = { folder = it },
                label = { Text("Folder") },
                placeholder = { Text("e.g. Memes") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            TextButton(
                onClick = {
                    val uri = selectedUri
                    if (uri.isNullOrBlank() || caption.isBlank()) {
                        onInvalid()
                    } else {
                        onSave(uri, caption.trim(), folder.trim().ifBlank { DEFAULT_FOLDER })
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun FolderManagerDialog(
    folders: List<String>,
    selectedFolder: String,
    onSelectFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder management") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onSelectFolder(ALL_FOLDERS)
                    onDismiss()
                }) {
                    Text(if (selectedFolder == ALL_FOLDERS) "✓ All" else "All")
                }
                folders.forEach { folder ->
                    TextButton(onClick = {
                        onSelectFolder(folder)
                        onDismiss()
                    }) {
                        val label = if (selectedFolder == folder) "✓ $folder" else folder
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun EditVideoDialog(
    video: VideoEntity,
    knownFolders: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var caption by remember(video.id) { mutableStateOf(video.caption) }
    var folder by remember(video.id) { mutableStateOf(video.folder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Folder") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (knownFolders.isNotEmpty()) {
                    Text(
                        text = "Existing folders: ${knownFolders.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleanCaption = caption.trim()
                if (cleanCaption.isNotEmpty()) {
                    onSave(cleanCaption, folder.trim().ifBlank { DEFAULT_FOLDER })
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
