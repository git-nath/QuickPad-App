package com.example.quickpad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.quickpad.data.AppDatabase
import com.example.quickpad.data.VideoEntity
import com.example.quickpad.data.VideoRepository
import com.example.quickpad.ui.theme.QuickPadTheme
import kotlinx.coroutines.launch

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
                    onSaveVideo = viewModel::addVideo
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    navController: NavHostController,
    videos: List<VideoEntity>,
    onSaveVideo: (String, String, () -> Unit) -> Unit
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
                    onAddClick = { navController.navigate("add") }
                )
            }
            composable("add") {
                AddVideoScreen(
                    onBack = { navController.popBackStack() },
                    onSave = { uri, caption ->
                        onSaveVideo(uri, caption) {
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(videos: List<VideoEntity>, onAddClick: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("QuickPad") }) },
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
            items(videos, key = { it.id }) { video ->
                VideoItem(video)
            }
        }
    }
}

@Composable
private fun VideoItem(video: VideoEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = Uri.parse(video.uri),
                contentDescription = "Video thumbnail",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = video.caption,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVideoScreen(
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onInvalid: () -> Unit
) {
    val context = LocalContext.current
    var caption by rememberSaveable { mutableStateOf("") }
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

            selectedUri?.let {
                AsyncImage(
                    model = Uri.parse(it),
                    contentDescription = "Selected video preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop
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

            TextButton(
                onClick = {
                    val uri = selectedUri
                    if (uri.isNullOrBlank() || caption.isBlank()) {
                        onInvalid()
                    } else {
                        onSave(uri, caption.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
