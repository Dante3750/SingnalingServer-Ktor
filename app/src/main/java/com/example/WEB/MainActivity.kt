package com.example.WEB

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.WEB.ui.WebRtcViewModel
import com.example.WEB.ui.WebRtcUiState
import com.example.WEB.ui.theme.WebRTCInterviewTheme
import org.webrtc.SurfaceViewRenderer

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))

        setContent {
            WebRTCInterviewTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WebRtcScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebRtcScreen(viewModel: WebRtcViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isSignalingConnected.collectAsState()
    val currentIp by viewModel.currentIp.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val emoji by viewModel.receivedEmoji.collectAsState()
    
    var showConfigDialog by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf(currentIp) }
    var roomInput by remember { mutableStateOf("test-room") }
    var userIdInput by remember { mutableStateOf(currentUserId) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("WebRTC Call", style = MaterialTheme.typography.titleMedium)
                        Text("ID: $currentUserId | Room: $roomInput", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) { Icon(Icons.Default.Edit, "Config") }
                    Text(
                        text = if (isConnected) "Online" else "Offline",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp),
                        color = if (isConnected) Color.Unspecified else Color.Red
                    )
                    Box(modifier = Modifier.padding(end = 16.dp).size(12.dp).background(if (isConnected) Color.Green else Color.Red, RoundedCornerShape(50)))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                if (uiState !is WebRtcUiState.Idle) {
                    val localVideoView = remember { SurfaceViewRenderer(context) }
                    val remoteVideoView = remember { SurfaceViewRenderer(context) }

                    LaunchedEffect(uiState) {
                        viewModel.prepareSessionViews(localVideoView, remoteVideoView)
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            // Views are released by sessionManager.release() in ViewModel
                        }
                    }

                    AndroidView(factory = { remoteVideoView }, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(120.dp, 160.dp).background(Color.DarkGray, RoundedCornerShape(8.dp))) {
                        AndroidView(factory = { localVideoView }, modifier = Modifier.fillMaxSize())
                    }
                }
                
                emoji?.let { Text(it, fontSize = 80.sp, modifier = Modifier.align(Alignment.Center)) }
            }

            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (uiState !is WebRtcUiState.Idle) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("❤️", "👍", "🔥", "😂", "😮").forEach { e ->
                                IconButton(onClick = { viewModel.sendEmoji(e) }) { Text(e, fontSize = 24.sp) }
                            }
                        }
                        Button(onClick = { viewModel.onHangup() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.fillMaxWidth()) { Text("Hangup") }
                    } else {
                        Button(onClick = { viewModel.onStartCall() }, enabled = isConnected, modifier = Modifier.fillMaxWidth()) { Text("Start Call") }
                    }
                }
            }
        }

        if (showConfigDialog) {
            AlertDialog(
                onDismissRequest = { showConfigDialog = false },
                title = { Text("Server Configuration") },
                text = {
                    Column {
                        TextField(value = ipInput, onValueChange = { ipInput = it }, label = { Text("Server IP") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        TextField(value = roomInput, onValueChange = { roomInput = it }, label = { Text("Room ID") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        TextField(value = userIdInput, onValueChange = { userIdInput = it }, label = { Text("User ID") }, singleLine = true)
                    }
                },
                confirmButton = { 
                    Button(onClick = { 
                        viewModel.updateConfig(ipInput, roomInput, userIdInput)
                        showConfigDialog = false 
                    }) { Text("Connect") } 
                }
            )
        }
        
        if (uiState is WebRtcUiState.IncomingCall) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Incoming Call") },
                confirmButton = { Button(onClick = { viewModel.onAcceptCall() }) { Text("Accept") } },
                dismissButton = { TextButton(onClick = { viewModel.onHangup() }) { Text("Decline") } }
            )
        }
    }
}
