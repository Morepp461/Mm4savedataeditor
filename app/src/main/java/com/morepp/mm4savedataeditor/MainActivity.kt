package com.morepp.mm4savedataeditor

import android.net.Uri
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF080B10)
private val Panel = Color(0xB51A2029)
private val PanelAlt = Color(0xE0202731)
private val Red = Color(0xFFE10600)
private val Gold = Color(0xFFD6B56D)
private val Blue = Color(0xFF8C9AA8)
private val Muted = Color(0xFF9AA4B0)
private val GlassEdge = Color(0x38FFFFFF)
private val Carbon = Color(0xFF11161D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditorApp() }
    }
}

private data class EditTarget(
    val root: String,
    val path: List<String>,
    val value: JsonElement,
    val minValue: String? = null,
    val maxValue: String? = null
)

private data class EditOperation(
    val root: String,
    val path: List<String>,
    val before: JsonElement,
    val after: JsonElement
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<Mm4SaveCodec.Document?>(null) }
    var selectedTab by remember { mutableStateOf("Overview") }
    var selectedDriver by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf("Belum ada save data") }
    var sourceUri by remember { mutableStateOf<String?>(null) }
    var selectedModule by remember { mutableStateOf<String?>(null) }
    var hasChanges by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var confirmDiscardOpen by remember { mutableStateOf(false) }
    var undoStack by remember { mutableStateOf(emptyList<EditOperation>()) }
    var redoStack by remember { mutableStateOf(emptyList<EditOperation>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showIntro by remember { mutableStateOf(true) }
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2600)
        showIntro = false
        if (!context.getSharedPreferences("mm4_editor", android.content.Context.MODE_PRIVATE).getBoolean("tutorial_seen", false)) showTutorial = true
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            errorMessage = null
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Tidak bisa membaca file.")
                    val bytes = input.use { readSaveBytes(it) }
                    Mm4SaveCodec.decode(bytes, uri.lastPathSegment ?: "career.msv")
                }
                document = loaded
                sourceName = uri.lastPathSegment ?: "MM4 career"
                sourceUri = uri.toString()
                selectedTab = "Overview"
                selectedModule = null
                search = ""
                hasChanges = false
                undoStack = emptyList()
                redoStack = emptyList()
            } catch (failure: Exception) {
                errorMessage = failure.message ?: "Gagal membuka save."
            } finally {
                busy = false
            }
        }
    }

    val templateFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri:Uri? ->
        val latest=document
        if(uri!=null && latest!=null) scope.launch {
            busy=true
            try {
                val templateDocument=withContext(Dispatchers.IO) {
                    val input=context.contentResolver.openInputStream(uri) ?: error("Tidak bisa membaca file template.")
                    val bytes=input.use { readSaveBytes(it) }
                    Mm4SaveCodec.decode(bytes,uri.lastPathSegment ?: "F1-F4-template.msv")
                }
                val changed=withContext(Dispatchers.Default) {
                    applyGodModeAction(latest,"official_f1_2026",templateDocument.data)
                }
                if(changed!=null) {
                    document=changed
                    hasChanges=true
                    undoStack=emptyList()
                    redoStack=emptyList()
                    snackbarHostState.showSnackbar("Template diterapkan: 80 profil pembalap dan atributnya. Nama tim, kontrak, dan riwayat career tetap dipertahankan.")
                } else {
                    snackbarHostState.showSnackbar("Template tidak cocok. Dibutuhkan 4 divisi, masing-masing 10 tim dan 2 pembalap per tim. Tidak ada perubahan diterapkan.")
                }
            } catch(failure:Exception) {
                snackbarHostState.showSnackbar("Gagal membaca atau menerapkan template: ${failure.message ?: "file tidak valid"}")
            } finally {
                busy=false
            }
        }
    }

    val requestOpen = {
        if (hasChanges) confirmDiscardOpen = true
        else openFile.launch(arrayOf("*/*"))
    }

    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val current = document
        if (uri != null && current != null) {
            if (uri.toString() == sourceUri) {
                errorMessage = "Pilih file tujuan yang berbeda agar save asli tidak tertimpa."
            } else scope.launch {
                busy = true
                errorMessage = null
                try {
                    withContext(Dispatchers.IO) {
                        val expected = Mm4SaveCodec.prepareForExport(current)
                        val bytes = Mm4SaveCodec.encode(current)
                        val verified = Mm4SaveCodec.decode(bytes, current.sourceName)
                        val mismatch = Mm4SaveCodec.firstDifference(verified, expected)
                        check(mismatch == null) {
                            "Validasi round-trip gagal pada $mismatch. File belum ditulis agar save tidak rusak."
                        }
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Penyedia file tidak bisa membuka tujuan export untuk ditulis.")
                        output.use { stream ->
                            stream.write(bytes)
                            stream.flush()
                        }
                        // Re-open the exact SAF URI and validate persisted bytes before reporting success.
                        val persisted = context.contentResolver.openInputStream(uri)
                            ?: error("File hasil export tidak dapat dibaca kembali.")
                        val persistedBytes = persisted.use { readSaveBytes(it) }
                        check(persistedBytes.isNotEmpty()) { "Export menghasilkan file kosong (0 byte)." }
                        val persistedDocument = Mm4SaveCodec.decode(persistedBytes, current.sourceName)
                        val persistedMismatch = Mm4SaveCodec.firstDifference(persistedDocument, expected)
                        check(persistedMismatch == null) {
                            "Verifikasi file setelah ditulis gagal pada $persistedMismatch. Jangan gunakan file ini."
                        }
                    }
                    hasChanges = false
                } catch (failure: Exception) {
                    errorMessage = failure.message ?: "Export gagal."
                } finally {
                    busy = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Panel,
            surfaceVariant = PanelAlt,
            primary = Red,
            onPrimary = Color.White,
            secondary = Gold,
            onSecondary = Ink,
            tertiary = Blue,
            onTertiary = Color.White,
            surfaceTint = Red,
            outline = GlassEdge,
            onSurface = Color.White,
            onBackground = Color.White,
            onSurfaceVariant = Muted
        )
    ) {
        Scaffold(
            containerColor = Ink,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(Modifier.width(3.dp).height(25.dp).clip(RoundedCornerShape(3.dp)).background(Red))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("MM4 SAVE DATA EDITOR", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                                    Text("PADDOCK CONTROL  •  CAREER ENGINEERING", color = Gold, fontSize = 8.sp, letterSpacing = 1.05.sp)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val operation = undoStack.lastOrNull() ?: return@IconButton
                                val current = document ?: return@IconButton
                                val updated = mutateDocument(current, operation.root) {
                                    setPath(it, operation.path, operation.before)
                                }
                                if (updated != null) {
                                    document = updated
                                    undoStack = undoStack.dropLast(1)
                                    redoStack = (redoStack + operation).takeLast(50)
                                    hasChanges = true
                                }
                            },
                            enabled = undoStack.isNotEmpty() && !busy
                        ) { Icon(Icons.Default.Undo, contentDescription = "Undo edit") }
                        IconButton(
                            onClick = {
                                val operation = redoStack.lastOrNull() ?: return@IconButton
                                val current = document ?: return@IconButton
                                val updated = mutateDocument(current, operation.root) {
                                    setPath(it, operation.path, operation.after)
                                }
                                if (updated != null) {
                                    document = updated
                                    redoStack = redoStack.dropLast(1)
                                    undoStack = (undoStack + operation).takeLast(50)
                                    hasChanges = true
                                }
                            },
                            enabled = redoStack.isNotEmpty() && !busy
                        ) { Icon(Icons.Default.Redo, contentDescription = "Redo edit") }
                        IconButton(onClick = requestOpen) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Buka save")
                        }
                        IconButton(
                            onClick = { exportFile.launch("MM4_Edited_Save.msv") },
                            enabled = document != null
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Export save")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF090D12),
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = { WatermarkFooter() }
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B1017), Ink, Color(0xFF10151C))))
                    .padding(padding)
            ) {
                val current = document
                if (current == null) {
                    WelcomePanel(busy, requestOpen)
                } else {
                    CareerHeader(
                        document = current,
                        fileName = sourceName,
                        hasChanges = hasChanges,
                        onExport = { exportFile.launch("MM4_Edited_Save.msv") }
                    )
                    CareerWorkspace(
                        document = current,
                        section = selectedTab,
                        onSection = { selectedTab = it; selectedDriver = null },
                        selectedDriver = selectedDriver,
                        onDriver = { selectedDriver = it },
                        onEdit = { root, path, value ->
                            if (value.isJsonPrimitive) {
                                val bounds = editorBounds(document, root, path)
                                editTarget = EditTarget(root, path, value.deepCopy(), bounds.first, bounds.second)
                            }
                        },
                        onGodAction = { action ->
                            if (busy) return@CareerWorkspace
                            val latest = document ?: return@CareerWorkspace
                            scope.launch {
                                busy = true
                                try {
                                    if(action=="official_f1_2026") {
                                        templateFile.launch(arrayOf("*/*"))
                                        busy=false
                                        return@launch
                                    }
                                    val changed = withContext(Dispatchers.Default) {
                                        applyGodModeAction(latest, action)
                                    }
                                    if (changed != null) {
                                        document = changed
                                        hasChanges = true
                                        undoStack = emptyList()
                                        redoStack = emptyList()
                                        val successText = when {
                                            action == "official_f1_2026" -> "Template tim dan 80 profil pembalap berhasil diterapkan."
                                            action == "rename_divisions" -> "Label divisi berhasil diperbarui."
                                            action.startsWith("swap_teams|") -> "Tim berhasil ditukar antar divisi."
                                            action.startsWith("move_driver|") -> "Pembalap berhasil dipindahkan."
                                            action == "max_balance" -> "Saldo berhasil diatur ke £2 miliar."
                                            action == "unlock_hq" -> "Upgrade HQ berhasil dibuka."
                                            action == "max_research" -> "Riset berhasil dimaksimalkan."
                                            action == "debug_unlock" -> "Debug unlock berhasil diaktifkan."
                                            action == "unlock_suppliers" -> "Supplier berhasil dibuka."
                                            else -> "Perubahan berhasil diterapkan."
                                        }
                                        snackbarHostState.showSnackbar(successText)
                                    } else {
                                        val failureText = when {
                                            action == "official_f1_2026" -> "Template tidak cocok: dibutuhkan 4 divisi, 10 tim dan 2 pembalap per tim."
                                            action == "rename_divisions" -> "Nama divisi tidak tersimpan sebagai teks di format save ini."
                                            action == "unlock_suppliers" -> "Supplier tidak diubah: struktur node tidak ditemukan atau sudah terbuka."
                                            else -> "Operasi gagal. Data save tidak diubah."
                                        }
                                        snackbarHostState.showSnackbar(failureText)
                                    }
                                } catch (failure: Exception) {
                                    snackbarHostState.showSnackbar("God Mode gagal: ${failure.message ?: "kesalahan tidak diketahui"}")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onApplyTeamColor = { colorPath, preset ->
                            val latest = document ?: return@CareerWorkspace
                            val changed = mutateDocument(latest, "Game Data") { root ->
                                val target = resolvePath(root, colorPath)?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mutateDocument false
                                var applied = false
                                preset.entrySet().forEach { (channel, channelValue) ->
                                    if (target.has(channel)) { target.add(channel, channelValue.deepCopy()); applied = true }
                                }
                                applied
                            }
                            if (changed != null) {
                                document = changed
                                hasChanges = true
                                undoStack = emptyList()
                                redoStack = emptyList()
                                scope.launch { snackbarHostState.showSnackbar("Warna tim berhasil diterapkan.") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Gagal menerapkan warna tim.") }
                            }
                        },
                        onApplyExperience = { experience ->
                            val latest = document ?: return@CareerWorkspace
                            val updatedData = applyManagerExperience(latest.data, experience)
                            if (updatedData == null) {
                                scope.launch { snackbarHostState.showSnackbar("Experience di luar rentang level yang tersimpan. Save tidak diubah.") }
                            } else {
                                document = latest.copy(data = updatedData)
                                hasChanges = true
                                undoStack = emptyList()
                                redoStack = emptyList()
                                scope.launch { snackbarHostState.showSnackbar("Experience manager diperbarui mengikuti tabel level career.") }
                            }
                        },
                        onResetInfluenceCooldown = { position ->
                            val latest = document ?: return@CareerWorkspace
                            val path = listOf("influenceManager", "m_Influences", "[$position]", "m_CurrentCooldown")
                            val before = resolvePath(latest.data, path)?.deepCopy()
                            val updatedData = resetInfluenceCooldown(latest.data, position)
                            if (before != null && updatedData != null) {
                                val changed = latest.copy(data = updatedData)
                                document = changed
                                val after = resolvePath(updatedData, path)?.deepCopy()
                                if (after != null) undoStack = (undoStack + EditOperation("Game Data", path, before, after)).takeLast(50)
                                redoStack = emptyList()
                                hasChanges = true
                                scope.launch { snackbarHostState.showSnackbar("Cooldown aksi berhasil diatur menjadi siap digunakan.") }
                            }
                        }
                    )
                }
            }
        }

        if (busy) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Memproses save…") },
                text = { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red) }
            )
        }

        if (confirmDiscardOpen) {
            AlertDialog(
                onDismissRequest = { confirmDiscardOpen = false },
                title = { Text("Buang perubahan?") },
                text = { Text("Perubahan yang belum diekspor akan hilang jika membuka save lain.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscardOpen = false
                        openFile.launch(arrayOf("*/*"))
                    }) { Text("LANJUTKAN") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscardOpen = false }) { Text("BATAL") }
                }
            )
        }

        errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Operasi gagal") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) { Text("Tutup") }
                }
            )
        }

        editTarget?.takeIf { it.value.isJsonPrimitive }?.let { target ->
            EditValueDialog(
                target = target,
                onClose = { editTarget = null },
                onSave = { value ->
                    val current = document ?: return@EditValueDialog
                    val rootBefore = if (target.root == "Save Info") current.info else current.data
                    val before = resolvePath(rootBefore, target.path)?.deepCopy()
                    val updated = mutateDocument(current, target.root) { setPath(it, target.path, value) }
                    if (updated != null) {
                        val rootAfter = if (target.root == "Save Info") updated.info else updated.data
                        val after = resolvePath(rootAfter, target.path)?.deepCopy()
                        if (before != null && after != null) {
                            undoStack = (undoStack + EditOperation(target.root, target.path, before, after)).takeLast(50)
                            redoStack = emptyList()
                        }
                        document = updated
                        hasChanges = true
                        editTarget = null
                    }
                }
            )
        }
        if (showIntro) IntroSplash()
        if (showTutorial) {
            var tutorialPage by remember { mutableIntStateOf(0) }
            val pages = listOf(
                Triple("01", "BUKA CAREER SAVE", "Pilih file career .msv dari penyimpanan perangkat. File asli tidak akan ditimpa."),
                Triple("02", "EDIT DATA CAREER", "Pilih kategori Drivers, Staff, Teams, Cars, Finance, HQ, atau God Mode. Periksa perubahan sebelum melanjutkan."),
                Triple("03", "UNDO / REDO", "Gunakan tombol kembali dan ulangi untuk membatalkan atau memulihkan perubahan selama sesi edit."),
                Triple("04", "EXPORT SAVE BARU", "Simpan hasil sebagai file baru. Editor akan memeriksa hasil encode dan decode sebelum file ditulis.")
            )
            val page = pages[tutorialPage]
            Dialog(
                onDismissRequest = {
                    showTutorial = false
                    context.getSharedPreferences("mm4_editor", android.content.Context.MODE_PRIVATE).edit().putBoolean("tutorial_seen", true).apply()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xF0252B33), Color(0xF0131921), Color(0xF02A1517))))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("MM4 • CAREER WORKSHOP", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                            Text("QUICK START GUIDE", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        }
                        TextButton(onClick = {
                            showTutorial = false
                            context.getSharedPreferences("mm4_editor", android.content.Context.MODE_PRIVATE).edit().putBoolean("tutorial_seen", true).apply()
                        }) { Text("LEWATI", color = Muted, fontSize = 10.sp) }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF171D25), Color(0xFF32191B)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(page.first, color = Color.White.copy(alpha = 0.10f), fontSize = 84.sp, fontWeight = FontWeight.Black)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = Red, modifier = Modifier.size(30.dp))
                            Text("RACE CONTROL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(pages.size) { index ->
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(if (index <= tutorialPage) Red else Color.White.copy(alpha = 0.16f)))
                        }
                    }
                    AnimatedContent(
                        targetState = tutorialPage,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(160))
                        },
                        label = "tutorial-step-motion"
                    ) { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pages[step].second, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                            Text(pages[step].third, color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("LANGKAH ${tutorialPage + 1} DARI ${pages.size}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                if (tutorialPage < pages.lastIndex) tutorialPage++
                                else {
                                    showTutorial = false
                                    context.getSharedPreferences("mm4_editor", android.content.Context.MODE_PRIVATE).edit().putBoolean("tutorial_seen", true).apply()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF10131D))
                        ) { Text(if (tutorialPage == pages.lastIndex) "MULAI EDIT" else "LANJUT", fontWeight = FontWeight.Black) }
                    }
                }
            }
        }

    }
}

@Composable
private fun IntroSplash() {
    val transition = rememberInfiniteTransition(label = "intro-motion")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo-pulse"
    )
    var showEndingMark by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1900); showEndingMark = true }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF080B10), Color(0xFF171D25), Color(0xFF080B10)))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.mm4_app_logo),
                    contentDescription = "MM4 Save Data Editor",
                    modifier = Modifier.size(132.dp).graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        rotationY = (1f - pulse) * 180f
                    }
                )
                Text("MM4 SAVE DATA EDITOR", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                Text("F1 MOTORSPORT  •  CAREER ENGINEERING", color = Gold, fontSize = 9.sp, letterSpacing = 1.8.sp)
                LinearProgressIndicator(
                    Modifier.width(176.dp).padding(top = 8.dp),
                    color = Red,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
                AnimatedVisibility(visible = showEndingMark, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 }) {
                    Text("CREATED BY MOREPP", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

@Composable
private fun WelcomePanel(busy: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xB84A3A58), Color(0x8C252E3B), Color(0xA83C2736))))
                .border(1.dp, GlassEdge, RoundedCornerShape(26.dp))
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painter = painterResource(id = R.drawable.mm4_app_logo), contentDescription = "MM4 Save Data Editor", modifier = Modifier.size(58.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("CREATED BY MOREPP", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("MM4 SAVE DATA EDITOR", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onOpen,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("BUKA FILE SAVE (.MSV)")
            }

        }
    }
}

@Composable
private fun CareerHeader(
    document: Mm4SaveCodec.Document,
    fileName: String,
    hasChanges: Boolean,
    onExport: () -> Unit
) {
    val label = document.info.get("teamName")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.takeIf { it.isNotBlank() }
        ?: "CAREER LOADED"
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xE0252C35), Color(0xD9182029), Color(0xE02A1718))))
            .border(1.dp, GlassEdge, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(4.dp)).background(Red))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("CAREER CONTROL  /  ${label.uppercase()}", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fileName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (hasChanges) Gold else Color(0xFF66C58A)))
                Text(if (hasChanges) "CHANGES PENDING EXPORT" else "CAREER LOADED • ORIGINAL PRESERVED", color = if (hasChanges) Gold else Color(0xFF82D7A2), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.45.sp)
            }
        }
        Button(
            onClick = onExport,
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("EXPORT", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
        }
    }
}


private val Sections = listOf("Overview", "Drivers", "Staff", "Teams", "Cars", "Finance", "Career Finance Analytics", "HQ", "Influence Actions", "Manager Progression", "Diagnostics", "Save Info", "God Mode")

@Composable
private fun CareerWorkspace(
    document: Mm4SaveCodec.Document, section: String, onSection: (String) -> Unit,
    selectedDriver: String?, onDriver: (String?) -> Unit,
    onEdit: (String, List<String>, JsonElement) -> Unit,
    onGodAction: (String) -> Unit,
    onApplyTeamColor: (List<String>, JsonObject) -> Unit,
    onApplyExperience: (Int) -> Unit,
    onResetInfluenceCooldown: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(Sections) { s ->
                FilterChip(
                    selected = section == s,
                    onClick = { onSection(s) },
                    label = { Text(s.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.45.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Red,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0x661C242E),
                        labelColor = Muted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = section == s,
                        borderColor = GlassEdge,
                        selectedBorderColor = Red
                    )
                )
            }
        }
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                (fadeIn(animationSpec = tween(190)) +
                    slideInVertically(animationSpec = tween(220)) { height -> height / 18 }) togetherWith
                    (fadeOut(animationSpec = tween(130)) +
                        slideOutVertically(animationSpec = tween(170)) { height -> -height / 24 })
            },
            label = "career-workspace-transition"
        ) { targetSection ->
            when (targetSection) {
                "Overview" -> OverviewPage(document, onSection)
                "Drivers" -> DriverPage(document, selectedDriver, onDriver, onEdit)
                "Influence Actions" -> InfluenceActionsPage(document, onResetInfluenceCooldown, onEdit)
                "Career Finance Analytics" -> CareerFinanceAnalyticsPage(document)
                "Manager Progression" -> ManagerProgressionPage(document, onApplyExperience)
                "Staff" -> EntityCatalogPage(document, "staff", onEdit, onApplyTeamColor)
                "Teams" -> EntityCatalogPage(document, "team", onEdit, onApplyTeamColor)
                "Cars" -> EntityCatalogPage(document, "car", onEdit, onApplyTeamColor)
                "Finance" -> FinancePage(document, onEdit)
                "HQ" -> HqPage(document, onEdit)
                "Diagnostics" -> SaveDiagnosticsPage(document)
                "Save Info" -> InfoPage(document, onEdit)
                "God Mode" -> GodModePage(document, onGodAction, onEdit)
                else -> SaveDiagnosticsPage(document)
            }
        }
    }
}

@Composable
private fun OverviewPage(doc: Mm4SaveCodec.Document, go: (String) -> Unit) {
    val info = doc.info
    val data = doc.data
    val name = info.get("teamName")?.asString ?: "Career"
    val balance = data.getAsJsonObject("financeManager")?.get("m_Balance")?.asLong ?: 0L
    val driverCount = data.getAsJsonObject("driverManager")?.getAsJsonArray("m_Drivers")?.size() ?: 0
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("CAREER CONTROL CENTER", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(name, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Career date: " + (info.get("gameDate")?.asString?.take(10) ?: "—"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { go("Drivers") }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("DRIVER LINEUP") }
                    OutlinedButton(onClick = { go("Finance") }) { Text("FINANCE") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCard(Modifier.weight(1f)) {
                    Icon(Icons.Default.AccountBalance, null, tint = Gold)
                    Text("TEAM BALANCE", color = Muted, fontSize = 9.sp)
                    Text(formatMoney(balance), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                GlassCard(Modifier.weight(1f)) {
                    Icon(Icons.Default.Groups, null, tint = Gold)
                    Text("DRIVER DATABASE", color = Muted, fontSize = 9.sp)
                    Text(driverCount.toString() + " records", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
        item { QuickLink("Drivers", "Profil pembalap, kontrak, dan atribut", Icons.Default.Person) { go("Drivers") } }
        item { QuickLink("Finance", "Saldo dan histori transaksi tim", Icons.Default.Payments) { go("Finance") } }
        item { QuickLink("Headquarters", "Progres fasilitas dan upgrade", Icons.Default.Business) { go("HQ") } }
        item { QuickLink("Save information", "Identitas karier dan pengaturan", Icons.Default.Info) { go("Save Info") } }
    }
}

@Composable
private fun DriverPage(doc: Mm4SaveCodec.Document, selected: String?, onSelect: (String?) -> Unit, onEdit: (String,List<String>,JsonElement)->Unit) {
    data class DriverRecord(val path:List<String>,val data:JsonObject,val id:String,val name:String,val team:String)
    val drivers=remember(doc.data) {
        val all=collectObjectsWithId(doc.data).associateBy { it.second.get("\$id")?.asString }
        val teamNames=doc.data.getAsJsonObject("teamManager")?.getAsJsonArray("m_Teams")
            ?.mapNotNull { teamRef ->
                val teamId=teamRef.asJsonObject.get("\$ref")?.asString ?: return@mapNotNull null
                val team=all[teamId]?.second ?: return@mapNotNull null
                teamId to (team.get("name")?.asString?.takeIf { it.isNotBlank() } ?: "Free Agent")
            }?.toMap().orEmpty()
        val roster=doc.data.getAsJsonObject("driverManager")?.getAsJsonArray("m_Drivers")
        roster?.mapNotNull { ref ->
            val id=ref.asJsonObject.get("\$ref")?.asString ?: return@mapNotNull null
            val pair=all[id] ?: return@mapNotNull null
            val (path,obj)=pair
            val details=obj.getAsJsonObject("m_PersonDetails") ?: return@mapNotNull null
            val name=details.get("name")?.asString?.trim().orEmpty().ifBlank { "Unnamed driver • " + id }
            val teamId=obj.getAsJsonObject("contract")?.getAsJsonObject("m_Team")?.get("\$ref")?.asString
            val team=teamId?.let { teamNames[it] } ?: "Free Agent"
            DriverRecord(path,obj,id,name,team)
        }?.distinctBy { it.id }?.sortedWith(compareBy({ it.team.lowercase() },{ it.name.lowercase() })).orEmpty()
    }
    val analyticsById = remember(doc.data, doc.info) {
        driverAnalyticsRecords(
            doc.data,
            doc.info.get("gameDate")?.takeIf { it.isJsonPrimitive }?.asString
        ).associateBy { it.recordKey }
    }
    val referenced=doc.data.getAsJsonObject("driverManager")?.getAsJsonArray("m_Drivers")?.size() ?: 0
    val chosen=drivers.firstOrNull { it.id==selected }
    if(chosen==null) {
        var search by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize()) {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=6.dp)) {
                Text("DRIVER DATABASE",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                Text("${drivers.size} driver profiles available",fontSize=22.sp,fontWeight=FontWeight.Black)
                Text("${referenced} driver references in career roster. Profiles are resolved by ID, including records without a \$type marker.",color=Muted,fontSize=10.sp)
            }
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=6.dp),placeholder={Text("Search driver name")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)
            LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(drivers.filter { it.name.contains(search,true) },key={it.id}) { driver ->
                    GlassCard(Modifier.fillMaxWidth().clickable { onSelect(driver.id) }) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.Person,null,tint=Gold,modifier=Modifier.size(30.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(driver.name,fontWeight=FontWeight.Bold)
                                val details=driver.data.getAsJsonObject("m_PersonDetails")
                                Text("${driver.team} • Age ${ageFromDate(details?.get("dateOfBirth")?.asString, doc.info.get("gameDate")?.takeIf { it.isJsonPrimitive }?.asString)} • Driver ID ${driver.data.get("m_DriverID")?.asString ?: "—"}",color=Muted,fontSize=10.sp)
                            }
                            Icon(Icons.Default.ChevronRight,null,tint=Muted)
                        }
                    }
                }
            }
        }
    } else {
        val d=chosen.data
        val careerAnalytics=analyticsById[chosen.id]
        val details=d.getAsJsonObject("m_PersonDetails")
        val contract=d.getAsJsonObject("contract")
        val stats=d.getAsJsonObject("m_Stats")?.getAsJsonArray("m_Stats")
        val profilePath=chosen.path
        LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={onSelect(null)}) { Icon(Icons.Default.ArrowBack,"Back") }
                    Column(Modifier.weight(1f)) {
                        Text(chosen.name,fontSize=19.sp,fontWeight=FontWeight.Bold)
                        Text("Driver profile • ${ageFromDate(details?.get("dateOfBirth")?.asString)} years",color=Muted,fontSize=10.sp)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("PERSONAL PROFILE",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                    listOf("firstName","lastName","name","shortName","threeLetterName","tweetHandle","dateOfBirth","gender","peakAge","peakDuration").forEach { key ->
                        val value=details?.get(key)
                        if(value!=null && value.isJsonPrimitive) Row(Modifier.fillMaxWidth().clickable { onEdit("Game Data",profilePath+listOf("m_PersonDetails",key),value) }.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(humanizeField(key),Modifier.weight(1f),color=Muted,fontSize=11.sp)
                            Text(driverProfileDisplayValue(key,value),color=Gold,fontWeight=FontWeight.SemiBold,fontSize=11.sp)
                            Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(14.dp))
                        }
                    }
                    val nationality=details?.getAsJsonObject("nationality")?.get("mNationalityID")
                    if(nationality!=null) Row(Modifier.fillMaxWidth().clickable { onEdit("Game Data",profilePath+listOf("m_PersonDetails","nationality","mNationalityID"),nationality) }.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("Nationality",Modifier.weight(1f),color=Muted,fontSize=11.sp)
                        Text(nationalityLabel(nationality),color=Gold,fontWeight=FontWeight.SemiBold,fontSize=10.sp)
                        Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(14.dp))
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("DRIVER CONTRACT",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                    listOf("m_PerRaceCost" to "Wage per race","m_SigningFee" to "Signing fee","m_EndYear" to "Contract end year","m_RacesLeft" to "Races remaining","m_Status" to "Contract status","m_Patience" to "Negotiation patience").forEach { (key,label) ->
                        val value=contract?.get(key)
                        if(value!=null) Row(Modifier.fillMaxWidth().clickable { onEdit("Game Data",profilePath+listOf("contract",key),value) }.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(label,Modifier.weight(1f),color=Muted,fontSize=11.sp)
                            Text(preview(value),color=Gold,fontWeight=FontWeight.SemiBold,fontSize=11.sp)
                            Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(14.dp))
                        }
                    }
                }
            }
            item { Text("DRIVER ATTRIBUTES",color=Gold,fontWeight=FontWeight.Bold,fontSize=12.sp) }
            items(stats?.size() ?: 0) { n ->
                val stat=stats!![n].asJsonObject
                val value=stat.get("m_Stat")
                val potential=stat.get("m_Potential")
                val label=statAttributeLabel("driver","Driver",n)
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                            Text("Potential ${displayIntegerStat(potential)} / 20",color=Muted,fontSize=9.sp)
                        }
                        Text("${displayIntegerStat(value)} / 20",color=Gold,fontWeight=FontWeight.Bold)
                        IconButton(onClick={if(value!=null) onEdit("Game Data",profilePath+listOf("m_Stats","m_Stats","[$n]","m_Stat"),value)}) { Icon(Icons.Default.Edit,null,tint=Muted) }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(potential!=null) TextButton(onClick={onEdit("Game Data",profilePath+listOf("m_Stats","m_Stats","[$n]","m_Potential"),potential)}) { Text("Edit potential",fontSize=9.sp) }
                        val modifier=stat.get("m_StatModifier")
                        if(modifier!=null) TextButton(onClick={onEdit("Game Data",profilePath+listOf("m_Stats","m_Stats","[$n]","m_StatModifier"),modifier)}) { Text("Modifier ${preview(modifier)}",fontSize=9.sp) }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("RIWAYAT KARIER", color=Gold, fontWeight=FontWeight.Bold, fontSize=11.sp)
                    val history = careerAnalytics?.careerHistory.orEmpty()
                    if (history.isEmpty()) {
                        Text("Belum ada riwayat musim yang tersimpan untuk pembalap ini.", color=Muted, fontSize=10.sp)
                    } else {
                        val totalWins = history.sumOf { it.wins }
                        val totalPodiums = history.sumOf { it.podiums }
                        val totalRaces = history.sumOf { it.races }
                        val totalPoints = history.sumOf { it.points }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("MENANG" to totalWins, "PODIUM" to totalPodiums, "BALAPAN" to totalRaces, "POIN" to totalPoints).forEach { (label,value) ->
                                Column(Modifier.weight(1f)) {
                                    Text(label, color=Muted, fontSize=8.sp)
                                    Text(value.toString(), color=Gold, fontWeight=FontWeight.Black, fontSize=14.sp)
                                }
                            }
                        }
                        Divider(color=Color.White.copy(alpha=0.12f))
                        history.forEach { season ->
                            Column(Modifier.fillMaxWidth().padding(vertical=5.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text("${season.year} • ${season.teamName}", fontWeight=FontWeight.SemiBold, fontSize=11.sp)
                                    Text("${season.points} poin", color=Gold, fontWeight=FontWeight.Bold, fontSize=10.sp)
                                }
                                Text("Menang ${season.wins} • Podium ${season.podiums} • Balapan ${season.races} • DNF ${season.dnfs}", color=Muted, fontSize=9.sp)
                            }
                        }
                    }
                }
            }

        }
    }
}

internal fun ageFromDate(date:String?, careerDate:String? = null):String {
    if(date.isNullOrBlank()) return "—"
    return try {
        val birth=java.time.LocalDate.parse(date.take(10))
        val reference=careerDate?.takeIf { it.length >= 10 }
            ?.let { java.time.LocalDate.parse(it.take(10)) }
            ?: java.time.LocalDate.now()
        java.time.Period.between(birth,reference).years.coerceAtLeast(0).toString()
    } catch(_:Exception) { "—" }
}

private fun collectTypedObjects(root:JsonElement,type:String):List<Pair<List<String>,JsonObject>> {
    val found=mutableListOf<Pair<List<String>,JsonObject>>()
    fun visit(node:JsonElement,path:List<String>) {
        when {
            node.isJsonObject -> {
                val obj=node.asJsonObject
                if(obj.get("\$type")?.takeIf { it.isJsonPrimitive }?.asString==type) found.add(path to obj)
                obj.entrySet().forEach { (key,value) -> visit(value,path+key) }
            }
            node.isJsonArray -> node.asJsonArray.forEachIndexed { index,value -> visit(value,path+"[$index]") }
        }
    }
    visit(root,emptyList())
    return found
}

@Composable
private fun TeamPage(doc: Mm4SaveCodec.Document, onEdit: (String,List<String>,JsonElement)->Unit) {
    val details = doc.data.getAsJsonObject("player")?.getAsJsonObject("m_TeamDetails")
    val colors = details?.getAsJsonObject("m_TeamColors")
    val teamName = doc.info.get("teamName")?.asString ?: "Career team"
    val colorNames = listOf("primaryColor", "secondaryColor", "tertiaryColor", "trimColor", "carColor", "staffPrimaryColor", "staffSecondaryColor", "sponsorsColor", "sponsorsDarkColour", "sponsorsLightColour", "helmetColour")
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("TEAM PROFILE", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(teamName, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Team identity and visual configuration", color = Muted, fontSize = 11.sp)
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("TEAM COLORS", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("RGB values stored in the career save. Tap a channel to edit it.", color = Muted, fontSize = 10.sp)
        } }
        items(colorNames.filter { colors?.get(it)?.isJsonObject == true }) { key ->
            val color = colors!!.getAsJsonObject(key)
            GlassCard(Modifier.fillMaxWidth()) {
                Text(humanizeField(key), fontWeight = FontWeight.Bold)
                listOf("r","g","b","a").forEach { channel ->
                    val value = color.get(channel)
                    if (value != null) Row(
                        Modifier.fillMaxWidth().clickable { onEdit("Game Data", listOf("player","m_TeamDetails","m_TeamColors",key,channel), value) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(channel.uppercase(), Modifier.weight(1f), color = Muted, fontSize = 10.sp)
                        Text(preview(value), color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Icon(Icons.Default.Edit, null, tint = Muted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}


@Composable
private fun EntityCatalogPage(doc: Mm4SaveCodec.Document, category: String, onEdit: (String,List<String>,JsonElement)->Unit, onApplyTeamColor: (List<String>,JsonObject)->Unit) {
    data class CatalogRecord(val path:List<String>,val data:JsonObject,val name:String,val type:String,val group:String="")
    var selected by remember(category) { mutableStateOf<String?>(null) }
    var query by remember(category) { mutableStateOf("") }
    val records=remember(doc.data,category) {
        when(category) {
            "team" -> {
                val all=collectObjectsWithId(doc.data).associateBy { it.second.get("\$id")?.asString }
                doc.data.getAsJsonObject("teamManager")?.getAsJsonArray("m_Teams")?.mapNotNull { ref ->
                    val id=ref.asJsonObject.get("\$ref")?.asString ?: return@mapNotNull null
                    val pair=all[id] ?: return@mapNotNull null
                    val (path,obj)=pair
                    val name=obj.get("name")?.asString?.ifBlank { null } ?: "Team $id"
                    CatalogRecord(path,obj,name,"Team")
                }?.distinctBy { it.path.joinToString("/") }.orEmpty()
            }
            "car" -> {
                val all=collectObjectsWithId(doc.data).associateBy { it.second.get("\$id")?.asString }
                doc.data.getAsJsonObject("teamManager")?.getAsJsonArray("m_Teams")?.mapNotNull { ref ->
                    val id=ref.asJsonObject.get("\$ref")?.asString ?: return@mapNotNull null
                    val pair=all[id] ?: return@mapNotNull null
                    val (path,obj)=pair
                    val car=obj.getAsJsonObject("car") ?: return@mapNotNull null
                    val name=(obj.get("name")?.asString ?: "Team $id")+" • Car"
                    CatalogRecord(path+listOf("car"),car,name,"Team Car")
                }?.distinctBy { it.path.joinToString("/") }.orEmpty()
            }
            else -> {
                val driverIds=doc.data.getAsJsonObject("driverManager")?.getAsJsonArray("m_Drivers")
                    ?.mapNotNull { it.asJsonObject.get("\$ref")?.asString }?.toSet().orEmpty()
                val all=collectObjectsWithId(doc.data)
                val teamNames=all.mapNotNull { (_,team) ->
                    val id=team.get("\$id")?.asString
                    val name=team.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                    if(id!=null && !name.isNullOrBlank() && team.has("m_TeamID")) id to name else null
                }.toMap()
                all.mapNotNull { (path,obj) ->
                    val id=obj.get("\$id")?.asString ?: return@mapNotNull null
                    if(id in driverIds) return@mapNotNull null
                    val stats=safeObject(obj,"stats") ?: return@mapNotNull null
                    val role=when {
                        stats.has("m_Engineer") -> "Designer"
                        stats.has("m_Mechanic") -> "Mechanic"
                        stats.has("m_Strategist") -> "Strategist"
                        else -> return@mapNotNull null
                    }
                    val person=safeObject(obj,"m_PersonDetails")
                    val personName=person?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    val regen=safeObject(obj,"m_PersonRegenDetails")
                    val displayName=personName.ifBlank {
                        val seed=regen?.get("regenSeed")?.asString ?: id
                        "Prospek " + role + " • " + seed.takeLast(5)
                    }
                    val teamRef=safeObject(obj,"contract")?.let { safeObject(it,"m_Team") }?.get("\$ref")?.asString
                    val group=teamRef?.let { teamNames[it] } ?: "Free agent / belum ditempatkan"
                    val isApplicant=path.any { it=="m_Applicants" }
                    CatalogRecord(path,obj,displayName,if(isApplicant) role + " • Kandidat" else role,group)
                }.distinctBy { it.data.get("\$id")?.asString }.sortedWith(compareBy({it.group},{it.type},{it.name}))
            }
        }
    }
    val chosen=records.firstOrNull { it.path.joinToString("/") == selected }
    val colorPath=if(category=="team") findObjectPath(records.firstOrNull { it.path.joinToString("/") == selected }?.data ?: JsonObject(),"m_TeamColors") ?: emptyList() else emptyList()
    val colorObject=if(colorPath.isNotEmpty()) records.firstOrNull { it.path.joinToString("/") == selected }?.data?.let { root -> resolvePath(root,colorPath)?.takeIf { node -> node.isJsonObject }?.asJsonObject } else null
    val colorPresets=remember(doc.data) { collectTeamColorPresets(doc.data) }
    if(chosen==null) {
        LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(when(category) { "staff" -> "STAFF MANAGEMENT"; "team" -> "TEAM DATABASE"; else -> "CAR PERFORMANCE" },color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                    Text("${records.size} records available",fontSize=23.sp,fontWeight=FontWeight.Black)
                    Text(when(category) {
                        "team" -> "Seluruh tim yang direferensikan dalam career save ditampilkan. Buka profil untuk mengedit identitas, atribut, kontrak, dan pengaturan tim."
                        "car" -> "Mobil setiap tim ditampilkan dengan statistik performa dan konfigurasi yang tersimpan."
                        else -> "Staff yang benar-benar tersimpan pada career save ditampilkan di sini. Profil dapat diedit dengan kontrol bertipe."
                    },color=Muted,fontSize=11.sp)
                }
            }
            item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Cari nama atau tipe")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true) }
            items(records.filter { it.name.contains(query,true) || it.type.contains(query,true) },key={it.path.joinToString("/")}) { record ->
                GlassCard(Modifier.fillMaxWidth().clickable { selected=record.path.joinToString("/") }) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(when(category) { "staff" -> Icons.Default.Badge; "team" -> Icons.Default.Groups; else -> Icons.Default.DirectionsCar },null,tint=Gold,modifier=Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.name,fontWeight=FontWeight.Bold)
                            Text(if(record.group.isBlank()) record.type else "${record.type} • ${record.group}",color=Muted,fontSize=10.sp)
                        }
                        Icon(Icons.Default.ChevronRight,null,tint=Muted)
                    }
                }
            }
        }
    } else {
        val fields=flatten(chosen.data).filter { row ->
            row.second.isJsonPrimitive &&
                row.first.lastOrNull() != "\$version" &&
                isCatalogPrimitiveEditable(category, row.first)
        }
        val isCar=category=="car"
        val stats=if(category=="staff") safeObject(chosen.data,"stats")?.let { safeArray(it,"m_Stats") } else safeArray(chosen.data,"m_Stats")
        val labels=when {
            isCar -> listOf("Top Speed","Acceleration","Braking","Low Speed Cornering","High Speed Cornering","Tyre Wear","Fuel Efficiency")
            category=="staff" && chosen.type.contains("Designer",true) -> listOf("Performance","Reliability","Leadership","Bonus","Success Rate")
            category=="staff" && chosen.type.contains("Strategist",true) -> listOf("Race Starts","Overtaking","Defending","Consistency","Focus")
            category=="staff" && chosen.type.contains("Mechanic",true) -> listOf("Setup Skill","Car Knowledge")
            else -> emptyList()
        }
        LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            if(category=="team" && colorObject!=null && colorPresets.isNotEmpty()) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("TEAM COLORS",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                    Text("Pilih warna dari kombinasi tim yang sudah tersimpan di career ini. Tidak perlu mengetik RGB atau kode warna.",color=Muted,fontSize=10.sp)
                    colorObject.entrySet().filter { it.value.isJsonObject }.forEach { (colorName,_) ->
                        Text(humanizeField(colorName),fontWeight=FontWeight.SemiBold,fontSize=11.sp)
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            items(colorPresets) { preset ->
                                Column(
                                    Modifier.width(64.dp).clip(RoundedCornerShape(10.dp))
                                        .clickable { onApplyTeamColor(chosen.path+colorPath+listOf(colorName),preset.channels) }
                                        .padding(4.dp),
                                    horizontalAlignment=Alignment.CenterHorizontally
                                ) {
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(teamColorChipColor(preset.channels)).border(1.dp,Color.White.copy(alpha=.3f),RoundedCornerShape(9.dp)))
                                    Text(preset.label,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=8.sp,color=Muted)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={selected=null}) { Icon(Icons.Default.ArrowBack,"Kembali") }
                    Column(Modifier.weight(1f)) {
                        Text(chosen.name,fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Text(chosen.type,color=Muted,fontSize=10.sp)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(if(category=="team") "TEAM PROFILE" else if(isCar) "CAR PERFORMANCE" else "STAFF PROFILE",color=Gold,fontWeight=FontWeight.Bold,fontSize=10.sp)
                    Text("${fields.size} editable values",fontSize=21.sp,fontWeight=FontWeight.Black)
                    Text("Edit nama, identitas, atribut, kontrak, dan pengaturan tanpa memasukkan JSON.",color=Muted,fontSize=10.sp)
                }
            }
            if((isCar || category=="staff") && stats!=null) {
                item { Text(if (isCar) "ATRIBUT MOBIL" else "ATRIBUT STAFF",color=Gold,fontWeight=FontWeight.Bold,fontSize=12.sp) }
                items(stats.size()) { index ->
                    val value=stats[index]
                    val statValue=if(category=="staff" && value.isJsonObject) value.asJsonObject.get("m_Stat") ?: value else value
                    GlassCard(Modifier.fillMaxWidth().clickable { onEdit("Game Data",chosen.path+(if(category=="staff") listOf("stats","m_Stats","[$index]","m_Stat") else listOf("m_Stats","[$index]")),statValue) }) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(labels.getOrNull(index) ?: ("Attribute " + (index+1)),Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                            Text(displayIntegerStat(statValue),color=Gold,fontWeight=FontWeight.Bold)
                            Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(15.dp))
                        }
                    }
                }
            }
            items(fields,key={it.first.joinToString("/")}) { (path,value) ->
                val statIndex=path.firstOrNull { it.startsWith("[") }?.removePrefix("[")?.removeSuffix("]")?.toIntOrNull() ?: 0
                val label=if(path.any { it=="m_Stats" }) statAttributeLabel(category,chosen.type,statIndex) else humanizeField(path.last())
                GlassCard(Modifier.fillMaxWidth().clickable { onEdit("Game Data",chosen.path+path,value) }) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                            Text(path.dropLast(1).joinToString(" / ") { humanizeField(it) },color=Muted,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Text(preview(value),color=Gold,fontWeight=FontWeight.Bold,fontSize=12.sp)
                        Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

internal fun isCatalogPrimitiveEditable(category: String, path: List<String>): Boolean = when (category) {
    "team" -> isTeamEditablePath(path)
    "staff" -> isStaffEditablePath(path)
    "car" -> false // Car attributes are rendered exclusively from the team's m_Stats array.
    else -> true
}

internal fun isTeamEditablePath(path: List<String>): Boolean {
    if (path.isEmpty()) return false
    val root = path.first()
    if (path.size == 1 && root in setOf("name", "shortName", "m_Reputation", "reputation")) return true
    val financialTerms = listOf(
        "financial", "finance", "budget", "balance", "cash", "debt", "loan",
        "wage", "salary", "sponsor", "income", "revenue", "expense", "cost",
        "profit", "loss", "bank", "payroll"
    )
    // Match only the top-level financial section. Nested car/driver fields can also
    // contain words such as "cost" or "salary" and must not leak into Team editing.
    return financialTerms.any { term -> root.contains(term, ignoreCase = true) }
}

internal fun isStaffEditablePath(path: List<String>): Boolean {
    if (path.isEmpty()) return false
    val root = path.first()
    if (root in setOf("m_PersonDetails", "contract")) return true
    val compensationTerms = listOf("salary", "wage", "pay", "perracecost", "signingfee")
    return compensationTerms.any { term -> root.contains(term, ignoreCase = true) }
}


private data class TeamColorPreset(val label:String,val channels:JsonObject)

private fun collectTeamColorPresets(root:JsonElement):List<TeamColorPreset> {
    val found=mutableListOf<TeamColorPreset>()
    fun visit(node:JsonElement) {
        if(node.isJsonObject) {
            node.asJsonObject.entrySet().forEach { (key,value) ->
                if(key=="m_TeamColors" && value.isJsonObject) {
                    value.asJsonObject.entrySet().forEach { (name,color) ->
                        if(color.isJsonObject) {
                            val channels=color.asJsonObject
                            if(channels.has("r") && channels.has("g") && channels.has("b")) found.add(TeamColorPreset(humanizeField(name),channels.deepCopy()))
                        }
                    }
                }
                visit(value)
            }
        } else if(node.isJsonArray) node.asJsonArray.forEach { visit(it) }
    }
    visit(root)
    return found.distinctBy { preset -> listOf("r","g","b","a").joinToString(":") { preset.channels.get(it)?.toString() ?: "" } }
}

private fun findObjectPath(root:JsonElement,targetKey:String):List<String>? {
    fun visit(node:JsonElement,path:List<String>):List<String>? {
        if(node.isJsonObject) {
            node.asJsonObject.entrySet().forEach { (key,value) ->
                if(key==targetKey && value.isJsonObject) return path+key
                visit(value,path+key)?.let { return it }
            }
        } else if(node.isJsonArray) node.asJsonArray.forEachIndexed { index,value -> visit(value,path+"[$index]")?.let { return it } }
        return null
    }
    return visit(root,emptyList())
}

private fun teamColorChipColor(channels:JsonObject):Color {
    fun channel(key:String):Float {
        val raw=channels.get(key)?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
        return if(raw<=1f) raw.coerceIn(0f,1f) else (raw/255f).coerceIn(0f,1f)
    }
    return Color(channel("r"),channel("g"),channel("b"),1f)
}

private fun nationalityLabel(value: JsonElement): String = nationalityNameFromCareerId(value.asString)

private fun nationalityNameFromCareerId(id:String):String = when(id) {
    else -> "ID $id • belum ada di katalog save"
}

private fun safeObject(parent:JsonObject,key:String):JsonObject? = parent.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
private fun safeArray(parent:JsonObject,key:String):JsonArray? = parent.get(key)?.takeIf { it.isJsonArray }?.asJsonArray

internal fun driverProfileDisplayValue(key:String,value:JsonElement):String = when(key) {
    "tweetHandle" -> value.asString.takeUnless { it.startsWith("MMM_TweetHandles_",true) }.orEmpty().ifBlank { "Belum diatur" }
    "dateOfBirth" -> runCatching { java.time.LocalDate.parse(value.asString.take(10)).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy",java.util.Locale("id","ID"))) }.getOrDefault("Tanggal tidak valid")
    else -> preview(value)
}

internal fun displayIntegerStat(value:JsonElement?):String = value?.let { v ->
    if(v.isJsonPrimitive && v.asJsonPrimitive.isNumber) runCatching { java.math.BigDecimal(v.asString).setScale(0,java.math.RoundingMode.HALF_UP).toPlainString() }.getOrDefault(preview(v)) else preview(v)
} ?: "—"

internal fun collectObjectsWithId(root:JsonElement):List<Pair<List<String>,JsonObject>> {
    val found=mutableListOf<Pair<List<String>,JsonObject>>()
    fun visit(node:JsonElement,path:List<String>) {
        when {
            node.isJsonObject -> {
                val obj=node.asJsonObject
                if(obj.has("\$id")) found.add(path to obj)
                obj.entrySet().forEach { (key,value) -> visit(value,path+key) }
            }
            node.isJsonArray -> node.asJsonArray.forEachIndexed { index,value -> visit(value,path+"[$index]") }
        }
    }
    visit(root,emptyList())
    return found
}

private fun statAttributeLabel(category:String,type:String,index:Int):String {
    val labels=when {
        category=="staff" && type.contains("designer",true) -> listOf("Performance","Reliability","Leadership","Bonus","Success Rate")
        category=="staff" && type.contains("strategist",true) -> listOf("Race Starts","Overtaking","Defending","Consistency","Focus")
        category=="staff" && type.contains("mechanic",true) -> listOf("Setup Skill","Car Knowledge")
        category=="car" -> listOf("Top Speed","Acceleration","Braking","Low Speed Cornering","High Speed Cornering","Tyre Wear","Fuel Efficiency","Car Reliability")
        else -> listOf("Braking","Smoothness","Control","Consistency","Race Starts","Awareness","Cornering","Defending","Instinct","Focus","Reactions","Decisions","Overtaking","Bravery","Composure")
    }
    return labels.getOrNull(index) ?: "Attribute "+(index+1)
}






@Composable
private fun InfluenceActionsPage(doc: Mm4SaveCodec.Document, onResetCooldown: (Int) -> Unit, onEdit: (String, List<String>, JsonElement) -> Unit) {
    val actions = remember(doc.data) { influenceActionRecords(doc.data) }
    val influence = doc.data.getAsJsonObject("influenceManager")?.get("m_Influence")
    val readyCount = actions.count { it.cooldown <= 0 }

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("INFLUENCE BALANCE", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text(influence?.let { preview(it) } ?: "—", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
                if (influence?.isJsonPrimitive == true && influence.asJsonPrimitive.isNumber) {
                    Button(onClick = { onEdit("Game Data", listOf("influenceManager", "m_Influence"), influence) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("EDIT SALDO INFLUENCE") }
                } else Text("Saldo influence tidak tersedia pada save ini.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("INFLUENCE ACTIONS", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("Aksi siap", color = Muted, fontSize = 9.sp)
                Text("$readyCount / " + actions.size, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        item { Text("DAFTAR AKSI", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
        items(actions, key = { it.index }) { action ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(action.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Aksi ${action.index + 1}", color = Muted, fontSize = 9.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            action.status,
                            color = if (action.cooldown <= 0) Color(0xFF9BE36A) else Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        if (action.cooldown > 0) {
                            TextButton(
                                onClick = { onResetCooldown(action.position) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text("Siapkan aksi", color = Gold, fontSize = 9.sp) }
                        }
                    }
                }
            }
        }
        if (actions.isEmpty()) item { Text("Data aksi influence tidak ditemukan pada save ini.", color = Muted, fontSize = 11.sp) }
        item { Text("Aksi dengan cooldown aktif dapat disiapkan kembali. Data lain pada setiap aksi tetap dipertahankan.", color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun ChampionshipManagerPage(doc: Mm4SaveCodec.Document, onEdit: (String, List<String>, JsonElement) -> Unit) {
    val championships = remember(doc.data) { championshipSummaries(doc.data) }
    var selectedKey by remember { mutableStateOf("") }
    var showChampionshipDetails by remember { mutableStateOf(false) }
    LaunchedEffect(doc.data) {
        if (championships.none { it.recordKey == selectedKey }) selectedKey = championships.firstOrNull()?.recordKey.orEmpty()
    }
    val selected = championships.firstOrNull { it.recordKey == selectedKey }

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("CHAMPIONSHIP MANAGER", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("Kejuaraan dan klasemen tim", fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("Jelajahi struktur kejuaraan, jumlah peserta, event berjalan, dan klasemen yang tersimpan.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("KEJUARAAN", color = Muted, fontSize = 9.sp)
                        AnimatedContent(
                            targetState = championships.size,
                            transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(180))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(120))) },
                            label = "championship-count"
                        ) { count -> Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("TOTAL TIM", color = Muted, fontSize = 9.sp)
                        Text(championships.sumOf { it.teamCount }.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("TOTAL EVENT", color = Muted, fontSize = 9.sp)
                        Text(championships.sumOf { it.raceCount }.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item { Text("DAFTAR KEJUARAAN", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
        items(championships, key = { it.recordKey }) { championship ->
            GlassCard(
                Modifier.fillMaxWidth().clickable { selectedKey = championship.recordKey; showChampionshipDetails = true }
                    .then(if (selectedKey == championship.recordKey) Modifier.border(1.dp, Gold, RoundedCornerShape(16.dp)) else Modifier)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(championship.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Tier ${championship.tier + 1} • Series ${championship.series + 1}", color = Muted, fontSize = 10.sp)
                        Text("${championship.teamCount} tim • ${championship.driverCount} pembalap • ${championship.raceCount} event", color = Muted, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Lihat klasemen", tint = Gold)
                }
            }
        }
        selected?.let { championship ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("DETAIL KEJUARAAN", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text(championship.displayName, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    val editableName = listOf("name", "m_Name", "displayName", "title").firstOrNull { key ->
                        championship.path.isNotEmpty() && resolvePath(doc.data, championship.path + key)?.isJsonPrimitive == true
                    }
                    if (editableName != null) {
                        OutlinedButton(
                            onClick = { resolvePath(doc.data, championship.path + editableName)?.let { onEdit("Game Data", championship.path + editableName, it) } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("EDIT NAMA KEJUARAAN", fontSize = 10.sp) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("tier" to "TIER", "series" to "SERIES", "category" to "CATEGORY", "m_EventNumber" to "EVENT").forEach { (field, label) ->
                            val fieldPath = championship.path + field
                            val fieldValue = resolvePath(doc.data, fieldPath)
                            OutlinedButton(
                                onClick = { fieldValue?.let { onEdit("Game Data", fieldPath, it) } },
                                modifier = Modifier.weight(1f),
                                enabled = fieldValue?.isJsonPrimitive == true
                            ) { Text(label, fontSize = 8.sp) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("TIER", color = Muted, fontSize = 9.sp)
                            Text((championship.tier + 1).toString(), fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("SERIES", color = Muted, fontSize = 9.sp)
                            Text((championship.series + 1).toString(), fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("EVENT BERJALAN", color = Muted, fontSize = 9.sp)
                            Text(championship.eventNumber.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { Text("KLASEMEN TIM", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
            items(championship.standings, key = { "${championship.recordKey}-${it.position}" }) { standing ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("P${standing.position}", color = Gold, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.width(38.dp))
                        Column(Modifier.weight(1f)) {
                            Text(standing.teamName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${standing.wins} menang • ${standing.podiums} podium • ${standing.races} balapan • ${standing.dnfs} DNF", color = Muted, fontSize = 9.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            AnimatedContent(
                                targetState = standing.points,
                                transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(180))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(120))) },
                                label = "standing-points"
                            ) { points -> Text(points.toString(), color = Gold, fontWeight = FontWeight.Black, fontSize = 17.sp) }
                            Text("POIN", color = Muted, fontSize = 8.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("m_CurrentPosition" to "POS", "wins" to "WINS", "podiums" to "PODIUM", "races" to "RACES", "DNFs" to "DNF").forEach { (field, label) ->
                            val fieldPath = standing.path + field
                            val fieldValue = resolvePath(doc.data, fieldPath)
                            TextButton(onClick = { fieldValue?.let { onEdit("Game Data", fieldPath, it) } }, enabled = fieldValue?.isJsonPrimitive == true, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                                Text(label, fontSize = 8.sp, color = if (fieldValue?.isJsonPrimitive == true) Gold else Muted)
                            }
                        }
                    }
                    if (standing.pointPaths.isNotEmpty()) {
                        Text("POIN PER EVENT", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(standing.pointPaths.size) { scoreIndex ->
                                val scorePath = standing.pointPaths[scoreIndex]
                                val scoreValue = resolvePath(doc.data, scorePath)
                                TextButton(
                                    onClick = { scoreValue?.let { onEdit("Game Data", scorePath, it) } },
                                    enabled = scoreValue?.isJsonPrimitive == true,
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                                ) {
                                    Text("R${scoreIndex + 1} • ${scoreValue?.takeIf { it.isJsonPrimitive }?.asInt ?: 0}", fontSize = 9.sp, color = Gold)
                                }
                            }
                        }
                    }
                }
            }
            if (championship.standings.isEmpty()) {
                item { Text("Klasemen tim belum tersedia pada kejuaraan ini.", color = Muted, fontSize = 11.sp) }
            }
        }
        item { Text("Perubahan metadata dan statistik hanya tersedia untuk field yang benar-benar ada di save.", color = Muted, fontSize = 10.sp) }
    }
    if (showChampionshipDetails && selected != null) {
        AlertDialog(
            onDismissRequest = { showChampionshipDetails = false },
            title = { Text(selected.displayName, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    InfoLine("Tier", (selected.tier + 1).toString())
                    InfoLine("Series", (selected.series + 1).toString())
                    InfoLine("Event berjalan", selected.eventNumber.toString())
                    InfoLine("Tim", selected.teamCount.toString())
                    InfoLine("Pembalap", selected.driverCount.toString())
                    InfoLine("Event tersimpan", selected.raceCount.toString())
                    Divider(color = Color.White.copy(alpha = 0.15f))
                    Text("KLASEMEN TIM", color = Gold, fontWeight = FontWeight.Bold)
                    selected.standings.take(8).forEach { row ->
                        InfoLine("P" + row.position + "  " + row.teamName, row.points.toString() + " poin")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChampionshipDetails = false }) { Text("TUTUP") } }
        )
    }
}

@Composable
private fun DriverAnalyticsPage(doc: Mm4SaveCodec.Document) {
    val records = remember(doc.data, doc.info) {
        driverAnalyticsRecords(doc.data, doc.info.get("gameDate")?.takeIf { it.isJsonPrimitive }?.asString)
    }
    var query by remember { mutableStateOf("") }
    var selectedDriverKey by remember { mutableStateOf("") }
    var showDriverDetails by remember { mutableStateOf(false) }
    LaunchedEffect(doc.data) {
        if (records.none { it.recordKey == selectedDriverKey }) selectedDriverKey = records.firstOrNull()?.recordKey.orEmpty()
    }
    val filtered = records.filter {
        query.isBlank() || it.name.contains(query, true) || it.teamName.contains(query, true)
    }
    val selected = records.firstOrNull { it.recordKey == selectedDriverKey }

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("DRIVER ANALYTICS", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("Statistik dan perjalanan pembalap", fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("Ringkasan profil, performa, potensi, pengalaman, dan trait berdasarkan data career.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("TOTAL PEMBALAP", color = Muted, fontSize = 9.sp)
                        Text(records.size.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("PUNYA RIWAYAT", color = Muted, fontSize = 9.sp)
                        Text(records.count { it.careerSeasons > 0 }.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Cari nama pembalap atau tim…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        item { Text("DATABASE PEMBALAP", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
        items(filtered, key = { it.recordKey }) { driver ->
            GlassCard(
                Modifier.fillMaxWidth().clickable { selectedDriverKey = driver.recordKey; showDriverDetails = true }
                    .then(if (selectedDriverKey == driver.recordKey) Modifier.border(1.dp, Gold, RoundedCornerShape(16.dp)) else Modifier)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(driver.teamName, color = Muted, fontSize = 10.sp)
                        Text(
                            (driver.age?.let { "Usia $it tahun" } ?: "Usia tidak tersedia") +
                                " • ${driver.careerSeasons} musim karier",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Lihat statistik", tint = Gold)
                }
            }
        }
        if (filtered.isEmpty()) {
            item { Text("Tidak ada pembalap yang cocok dengan pencarian.", color = Muted, fontSize = 11.sp) }
        }
        selected?.let { driver ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("PROFIL ANALITIK", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text(driver.name, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(driver.teamName, color = Muted, fontSize = 11.sp)
                    Divider(color = Color.White.copy(alpha = 0.12f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("USIA", color = Muted, fontSize = 9.sp)
                            Text(driver.age?.let { "$it tahun" } ?: "—", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("RIWAYAT KARIER", color = Muted, fontSize = 9.sp)
                            Text("${driver.careerSeasons} musim", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("TRAIT", color = Muted, fontSize = 9.sp)
                            Text(driver.traitCount.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Text("ID pembalap ${driver.driverId}", color = Muted, fontSize = 9.sp)
                }
            }
            if (driver.attributes.isNotEmpty() || driver.potentials.isNotEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("ATRIBUT DAN POTENSI", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        val count = maxOf(driver.attributes.size, driver.potentials.size)
                        (0 until count).forEach { index ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(statAttributeLabel("driver", "driver", index), Modifier.weight(1f), fontSize = 11.sp)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${driver.attributes.getOrNull(index) ?: "—"} / 20", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Potensi ${driver.potentials.getOrNull(index) ?: "—"}", color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("Halaman ini hanya membaca data pembalap. Tidak ada statistik atau riwayat yang ditulis ulang ke save.", color = Muted, fontSize = 10.sp)
        }
    }
    if (showDriverDetails && selected != null) {
        AlertDialog(
            onDismissRequest = { showDriverDetails = false },
            title = { Text(selected.name, fontWeight = FontWeight.Black) },
            text = {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(selected.teamName, color = Gold, fontWeight = FontWeight.Bold)
                    InfoLine("Usia", selected.age?.let { it.toString() + " tahun" } ?: "Tidak tersedia")
                    InfoLine("Riwayat karier", selected.careerSeasons.toString() + " musim")
                    selected.careerHistory.take(10).forEach { season ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(season.year.toString() + " • " + season.teamName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                Text(season.points.toString() + " poin", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text("Menang ${season.wins}  •  Podium ${season.podiums}  •  Balapan ${season.races}  •  DNF ${season.dnfs}", color = Muted, fontSize = 9.sp)
                        }
                    }
                    InfoLine("Trait", selected.traitCount.toString())
                    Divider(color = Color.White.copy(alpha = 0.15f))
                    Text("ATRIBUT & POTENSI", color = Gold, fontWeight = FontWeight.Bold)
                    val count = maxOf(selected.attributes.size, selected.potentials.size)
                    (0 until count).forEach { index ->
                        InfoLine(statAttributeLabel("driver", "driver", index),
                            (selected.attributes.getOrNull(index)?.toString() ?: "—") + " / 20  •  Potensi " + (selected.potentials.getOrNull(index)?.toString() ?: "—"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDriverDetails = false }) { Text("TUTUP") } }
        )
    }
}

@Composable
private fun WorldSettingsPage(doc:Mm4SaveCodec.Document,onEdit:(String,List<String>,JsonElement)->Unit) {
    val keywords=listOf("store","shop","unlock","rule","regulation","gameplay","league","championship","race","calendar","difficulty","option","setting")
    val groups=doc.data.entrySet().filter { e -> keywords.any { e.key.contains(it,true) } }
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("DUNIA KARIER & ATURAN",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
            Text("Pengaturan musim, kejuaraan, toko komponen, supplier, riset, cuaca, dan aturan yang benar-benar ditemukan di file save.",color=Muted,fontSize=11.sp)
            Text(groups.size.toString()+" data groups detected",fontSize=20.sp,fontWeight=FontWeight.Black)
        } }
        items(groups) { group ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(worldGroupLabel(group.key),fontWeight=FontWeight.Bold)
                Text(categoryDescription(group.value,flatten(group.value).size),color=Muted,fontSize=10.sp)
                flatten(group.value).filter { (path,value) -> path.none { it.startsWith("\$") } && value.isJsonPrimitive }.forEach { (path,value) ->
                    Row(Modifier.fillMaxWidth().clickable { onEdit("Game Data",listOf(group.key)+path,value) }.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(humanLabel(path.last()),Modifier.weight(1f),fontSize=11.sp)
                        Text(preview(value),color=Gold,fontSize=11.sp)
                        Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(14.dp))
                    }
                }
            }
        }
        if(groups.isEmpty()) item { Text("Belum ditemukan grup Store/Rules/Unlock yang bisa dikenali di save ini. Data lain tetap dipertahankan di Advanced.",color=Muted) }
    }
}

@Composable
private fun CareerFinanceAnalyticsPage(doc: Mm4SaveCodec.Document) {
    val summary = remember(doc.data) { careerFinanceSummary(doc.data) }
    val transactions = remember(doc.data) {
        doc.data.getAsJsonObject("financeManager")?.get("m_TransactionHistory")
            ?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { value ->
                value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
            }.orEmpty()
    }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("CAREER FINANCE ANALYTICS", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("Ringkasan keuangan career", fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("Analisis memakai saldo, riwayat transaksi, dan biaya per balapan yang tersimpan.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text("SALDO SAAT INI", color = Muted, fontSize = 9.sp)
                Text(summary?.let { formatMoney(it.balance) } ?: "Tidak tersedia", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }
        if (summary != null) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("RINGKASAN TRANSAKSI", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    InfoLine("Total catatan", summary.transactionCount.toString())
                    InfoLine("Nilai positif", formatMoney(summary.incomingTotal))
                    InfoLine("Nilai negatif", formatMoney(summary.outgoingTotal))
                    InfoLine("Tidak mengubah saldo", summary.unchangedCount.toString())
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("BIAYA PER BALAPAN", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    InfoLine("Catatan biaya", summary.perRaceCostCount.toString())
                    InfoLine("Total nilai biaya", formatMoney(summary.perRaceCostTotal))
                }
            }
        }
        item { Text("RIWAYAT TRANSAKSI", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
        items(transactions.size) { index ->
            val amount = transactions[index]
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Transaksi " + (index + 1), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            when {
                                amount > 0 -> "Nilai positif"
                                amount < 0 -> "Nilai negatif"
                                else -> "Tidak ada perubahan nilai"
                            },
                            color = Muted,
                            fontSize = 9.sp
                        )
                    }
                    Text(formatMoney(amount), color = if (amount >= 0) Color(0xFF9BE36A) else Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (transactions.isEmpty()) item { Text("Belum ada catatan transaksi numerik pada save ini.", color = Muted, fontSize = 11.sp) }
        item { Text("Jenis transaksi tidak ditebak karena riwayat pada save hanya menyimpan nilai. Semua angka ditampilkan tanpa pecahan.", color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun ManagerProgressionPage(doc: Mm4SaveCodec.Document, onApplyExperience: (Int) -> Unit) {
    val summary = remember(doc.data) { managerProgressionSummary(doc.data) }
    var experienceInput by remember(summary) { mutableStateOf(summary?.experience?.toString().orEmpty()) }
    val parsedExperience = experienceInput.toIntOrNull()
    val canApply = summary != null && parsedExperience != null && parsedExperience >= 0
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("MANAGER PROGRESSION", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("Perkembangan manager", fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("Level dan experience dibaca dari data career serta tabel level yang tersimpan di save.", color = Muted, fontSize = 11.sp)
            }
        }
        if (summary != null) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("LEVEL SAAT INI", color = Muted, fontSize = 9.sp)
                            Text(summary.level.toString() + " / " + summary.maximumLevel, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("EXPERIENCE", color = Muted, fontSize = 9.sp)
                            Text(summary.experience.toString(), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.12f))
                    InfoLine("Awal rentang level", summary.baseExperience.toString())
                    InfoLine("Batas level berikutnya", summary.maxExperience.toString())
                    Text("Experience baru otomatis menentukan level dan batas level dari tabel career. Nilai di luar rentang tidak akan diterapkan.", color = Muted, fontSize = 10.sp)
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("UBAH EXPERIENCE", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    OutlinedTextField(
                        value = experienceInput,
                        onValueChange = { value -> experienceInput = value.filter(Char::isDigit).take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Total experience") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Button(
                        onClick = { parsedExperience?.let(onApplyExperience) },
                        enabled = canApply && parsedExperience != summary.experience,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red)
                    ) { Text("TERAPKAN EXPERIENCE") }
                }
            }
        } else {
            item { Text("Data progresi manager tidak lengkap pada save ini. Tidak ada perubahan yang bisa diterapkan.", color = Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun FinancePage(doc: Mm4SaveCodec.Document, onEdit: (String,List<String>,JsonElement)->Unit) {
    val finance = doc.data.getAsJsonObject("financeManager") ?: JsonObject()
    val balance = finance.get("m_Balance")
    val history = finance.getAsJsonArray("m_TransactionHistory")
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("CURRENT TEAM BALANCE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(formatMoney(balance?.asLong ?: 0L), fontSize = 27.sp, fontWeight = FontWeight.Black)
                Button(onClick = { if (balance != null) onEdit("Game Data", listOf("financeManager","m_Balance"), balance) }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("EDIT BALANCE") }
            }
        }
        item { Text("TRANSACTION HISTORY", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        items(history?.size() ?: 0) { i -> GlassCard(Modifier.fillMaxWidth()) { InfoLine("Transaction " + (i + 1), financeHistoryLabel(history!![i])) } }
    }
}

internal fun financeHistoryLabel(value: JsonElement): String {
    if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        return runCatching { formatMoney(value.asLong) }.getOrDefault(preview(value))
    }
    if (value.isJsonObject) {
        val obj = value.asJsonObject
        val amount = listOf("amount", "m_Amount", "value", "m_Value", "balance")
            .firstNotNullOfOrNull { key -> obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } }
        if (amount != null) return runCatching { formatMoney(amount.asLong) }.getOrDefault(preview(value))
    }
    return "Detail transaksi tersimpan"
}

@Composable
private fun SaveDiagnosticsPage(doc: Mm4SaveCodec.Document) {
    val entities = remember(doc.data) { collectObjectsWithId(doc.data) }
    val ids = remember(entities) { entities.mapNotNull { it.second.get("\$id")?.takeIf { v -> v.isJsonPrimitive }?.asString } }
    val idCounts = remember(ids) { ids.groupingBy { it }.eachCount() }
    val duplicateIds = remember(idCounts) { idCounts.filterValues { it > 1 } }
    val entityIdSet = remember(ids) { ids.toSet() }
    val driverRefs = doc.data.getAsJsonObject("driverManager")?.getAsJsonArray("m_Drivers")
        ?.mapNotNull { ref -> ref.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString }
        .orEmpty()
    val teamRefs = doc.data.getAsJsonObject("teamManager")?.getAsJsonArray("m_Teams")
        ?.mapNotNull { ref -> ref.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString }
        .orEmpty()
    val unresolvedDrivers = driverRefs.count { it !in entityIdSet }
    val unresolvedTeams = teamRefs.count { it !in entityIdSet }
    val explicitNulls = remember(doc.data) { flatten(doc.data).count { it.second.isJsonNull } }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("SAVE HEALTH CHECK", color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("Pemeriksaan hanya-baca terhadap struktur dan referensi career save. Tidak ada data yang diubah.", color = Muted, fontSize = 11.sp)
                Text("${doc.data.size()} top-level data groups", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("ENTITY & ROSTER REFERENCES", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                InfoLine("Objects with entity IDs", entities.size.toString())
                InfoLine("Unique entity IDs", entityIdSet.size.toString())
                InfoLine("Driver roster references", driverRefs.size.toString())
                InfoLine("Unresolved driver references", unresolvedDrivers.toString())
                InfoLine("Team references", teamRefs.size.toString())
                InfoLine("Unresolved team references", unresolvedTeams.toString())
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("STRUCTURE WARNINGS", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                InfoLine("Duplicate entity IDs", duplicateIds.size.toString())
                InfoLine("Explicit null values", explicitNulls.toString())
                InfoLine("Save container version", "40 (supported profile)")
                Text(
                    if (unresolvedDrivers == 0 && unresolvedTeams == 0 && duplicateIds.isEmpty())
                        "Tidak ditemukan masalah referensi pada pemeriksaan dasar ini."
                    else "Ada referensi atau ID yang perlu diperiksa. Angka ini merupakan indikator, bukan bukti save rusak.",
                    color = if (unresolvedDrivers == 0 && unresolvedTeams == 0 && duplicateIds.isEmpty()) Color(0xFF82D7A2) else Gold,
                    fontSize = 10.sp
                )
            }
        }
        if (duplicateIds.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("DUPLICATE ID SAMPLE", color = Gold, fontWeight = FontWeight.Bold)
                    duplicateIds.entries.take(20).forEach { (id, count) -> InfoLine(id, "${count} occurrences") }
                }
            }
        }
    }
}

private data class HqFacilityInfo(val name: String, val group: Int, val effect: String)
private val HqFacilityCatalog = listOf(
 HqFacilityInfo("Fabrication Pipeline",0,"+2 Part Build Limit"),
 HqFacilityInfo("Factory",0,"+2 Mechanical Bonus to Both Cars"),
 HqFacilityInfo("Telemetry Department",0,"New Setup Card available in Qualifying"),
 HqFacilityInfo("Simulator",0,"+2 Part Build Limit"),
 HqFacilityInfo("Staff Gym",1,"+1 Driver Improvement Point Every 4 Races"),
 HqFacilityInfo("Publicity",1,"Drivers receive +1 Sponsor Appeal"),
 HqFacilityInfo("Education",1,"+1 Driver Improvement Point Every 4 Races"),
 HqFacilityInfo("Sporting Psychology Center",1,"Can Give Focus to 2 Young Drivers"),
 HqFacilityInfo("Forecasting Center",2,"Forecasting Center Unlocked"),
 HqFacilityInfo("Outsourced Logistics",2,"Can Target 1 Additional Node in the Supplier Network"),
 HqFacilityInfo("Promotions",2,"+10% chance of a Discounted Item appearing in each store"),
 HqFacilityInfo("Satellite Array",2,"Can Target 1 Additional Node in the Supplier Network"),
 HqFacilityInfo("Public Relations Team",3,"+1 Sponsor Appeal"),
 HqFacilityInfo("Income",3,"+$200,000 Income Per Race"),
 HqFacilityInfo("Hospitality Tents",3,"Unlock 6th Sponsor Slot"),
 HqFacilityInfo("Companion App Development",4,"+5 Influence Per Race; +$200,000 per Race"),
 HqFacilityInfo("Social Media Centre",4,"+25 Influence"),
 HqFacilityInfo("Part Development Contracts",4,"+5 Influence Per Race"),
 HqFacilityInfo("Rapport",4,"Negotiations are 5% Easier")
)
private val HqGroupNames = listOf("Garage","Academy","Logistics","Event Space","Communications")

@Composable
private fun HqPage(doc: Mm4SaveCodec.Document, onEdit: (String,List<String>,JsonElement)->Unit) {
 val hq=doc.data.getAsJsonObject("headquarters") ?: JsonObject()
 val upgrades=hq.get("m_Upgrades")?.takeIf { it.isJsonArray }?.asJsonArray
 val completed=hq.get("m_CompletedUpgrades")?.takeIf { it.isJsonArray }?.asJsonArray
 // In real MM4 saves these two fields are JSON objects (often empty), not arrays.
 val tierRows=hq.get("m_CurrentTierInformation")?.takeIf { it.isJsonArray }?.asJsonArray
 val grouped=upgrades?.mapIndexedNotNull { index,element ->
  if(!element.isJsonObject) return@mapIndexedNotNull null
  val row=element.asJsonObject
  val key=row.get("Key")?.asString ?: index.toString()
  val value=row.getAsJsonObject("Value") ?: return@mapIndexedNotNull null
  Triple(index,key,value)
 }?.groupBy { it.third.get("pathType")?.asInt ?: -1 }.orEmpty()
 LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Text("HEADQUARTERS",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
    Text(HqFacilityCatalog.size.toString()+" named facilities",fontSize=23.sp,fontWeight=FontWeight.Black)
    Text((completed?.size() ?: 0).toString()+" completed upgrades • "+(upgrades?.size() ?: 0).toString()+" upgrade nodes",color=Muted,fontSize=10.sp)
    Text("Nama dan efek fasilitas diambil dari video gameplay kamu. Upgrade node ditampilkan terpisah agar ID game tidak dipasangkan ke fasilitas secara asal.",color=Muted,fontSize=10.sp)
   }
  }
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Text("HQ CATEGORY LEVELS",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
    tierRows?.forEachIndexed { index,row ->
     if(row.isJsonObject) {
      val key=row.asJsonObject.get("Key")?.asInt ?: index
      val value=row.asJsonObject.get("Value")
      val group=HqGroupNames.getOrNull(key) ?: "HQ Category #"+key
      Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
       Text(group,Modifier.weight(1f),color=Color.White,fontSize=12.sp)
       Text(value?.let { preview(it) } ?: "—",color=Gold,fontWeight=FontWeight.Bold)
       if(value!=null) IconButton(onClick={onEdit("Game Data",listOf("headquarters","m_CurrentTierInformation","["+index+"]","Value"),value)}) {
        Icon(Icons.Default.Edit,contentDescription="Edit "+group+" level",tint=Muted)
       }
      }
     }
    }
   }
  }
  HqGroupNames.forEachIndexed { groupIndex,groupName ->
   val facilities=HqFacilityCatalog.filter { it.group==groupIndex }
   item {
    GlassCard(Modifier.fillMaxWidth()) {
     Text(groupName.uppercase(),color=Gold,fontWeight=FontWeight.Black,fontSize=12.sp)
     Text(facilities.size.toString()+" facilities shown in your gameplay video",color=Muted,fontSize=10.sp)
    }
   }
   items(facilities) { facility ->
    GlassCard(Modifier.fillMaxWidth()) {
     Text(facility.name,fontWeight=FontWeight.Bold,fontSize=14.sp)
     Text(facility.effect,color=Color(0xFF9BE36A),fontSize=11.sp)
    }
   }
  }
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Text("RAW UPGRADE NODES",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
    Text("Edit State, weeksToUnlock, dan pathType menggunakan Key asli dari save.",color=Muted,fontSize=10.sp)
   }
  }
  grouped.toSortedMap().forEach { (pathType,rows) ->
   item {
    GlassCard(Modifier.fillMaxWidth()) {
     Text(HqGroupNames.getOrNull(pathType) ?: "Unmapped path type "+pathType,color=Gold,fontWeight=FontWeight.Bold)
     Text(rows.size.toString()+" upgrade nodes",color=Muted,fontSize=10.sp)
    }
   }
   items(rows,key={it.first}) { (listIndex,gameKey,value) ->
    val state=value.get("State")
    val weeks=value.get("weeksToUnlock")
    val path=value.get("pathType")
    val stateNumber=state?.asInt ?: -1
    val status=when(stateNumber){0->"Locked";1->"Unlocked";2->"Upgrade in progress";else->"State "+stateNumber}
    GlassCard(Modifier.fillMaxWidth()) {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text("Upgrade node #"+gameKey,fontWeight=FontWeight.Bold,fontSize=13.sp)
       Text(status,color=when(stateNumber){1->Color(0xFF82D7A2);0->Red;else->Gold},fontSize=11.sp)
      }
      if(state!=null) TextButton(onClick={onEdit("Game Data",listOf("headquarters","m_Upgrades","["+listIndex+"]","Value","State"),state)}){Text("STATE")}
     }
     if(weeks!=null) {
      InfoLine("Weeks to unlock",preview(weeks))
      TextButton(onClick={onEdit("Game Data",listOf("headquarters","m_Upgrades","["+listIndex+"]","Value","weeksToUnlock"),weeks)}){Text("EDIT TIMER")}
     }
     if(path!=null) {
      InfoLine("Upgrade path",HqGroupNames.getOrNull(path.asInt) ?: preview(path))
      TextButton(onClick={onEdit("Game Data",listOf("headquarters","m_Upgrades","["+listIndex+"]","Value","pathType"),path)}){Text("EDIT PATH")}
     }
    }
   }
  }
 }
}

private val SupplierNames=listOf("Micronix Racing","Shimizu","Polymat Group","Tectra Fibres","Norex","Mersault","Kitano")
private fun componentTypeName(type:Int)=when(type){1->"Suspension";3->"Exhaust";4->"ECU";5->"Gearbox";else->"Component type #"+type}
private fun componentRarityName(rarity:Int)=when(rarity){0->"Common";1->"Uncommon";else->"Rarity #"+rarity}
private fun componentStatName(stat:Int)=when(stat){
 0->"Top Speed";1->"Acceleration";2->"Braking";3->"Low Speed Cornering";4->"High Speed Cornering";5->"Tyre Wear";6->"Fuel Efficiency";else->"Stat #"+stat
}

@Composable
private fun ComponentMarketplacePage(doc:Mm4SaveCodec.Document,onEdit:(String,List<String>,JsonElement)->Unit) {
 val market=doc.data.getAsJsonObject("componentMarketplace") ?: JsonObject()
 val openItems=market.getAsJsonArray("m_Components")?.mapIndexed { index,element->index to element }.orEmpty()
 val supplierRows=market.getAsJsonArray("m_CompanyComponents")?.mapIndexed { index,element->index to element }.orEmpty()
 LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Text("COMPONENT MARKETPLACE",color=Gold,fontWeight=FontWeight.Black,fontSize=12.sp)
    Text("Open Market & 7 supplier stores",fontSize=21.sp,fontWeight=FontWeight.Black)
    Text("Edit harga, rarity, diskon, status stok, tipe komponen, serta bonus statistik. Nama supplier mengikuti tab di video.",color=Muted,fontSize=10.sp)
   }
  }
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Text("PREMIUM STORE • READ ONLY",color=Gold,fontWeight=FontWeight.Black,fontSize=12.sp)
    Text("Investments",fontWeight=FontWeight.Bold,fontSize=11.sp)
    Text("$15,000,000 pack • Rp 52,000  |  $60,000,000 pack • Rp 109,000",color=Muted,fontSize=10.sp)
    Text("Game Changers",fontWeight=FontWeight.Bold,fontSize=11.sp)
    Text("Chairman's Payment • Game Editor",color=Muted,fontSize=10.sp)
    Text("Bundles",fontWeight=FontWeight.Bold,fontSize=11.sp)
    Text("Game Editor + Chairman's Payment • Premium Liveries Bundle",color=Muted,fontSize=10.sp)
    Text("Katalog premium ini terlihat di video, tetapi tidak tersimpan di SlotTwo.msv. Bagian ini hanya referensi; tidak ada kontrol edit palsu untuk data yang tidak ada di save.",color=Muted,fontSize=10.sp)
   }
  }
  item {
   GlassCard(Modifier.fillMaxWidth()) {
    Text("MARKET SETTINGS",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
    listOf("OutOfStockChance","m_CompanyRacesToRestock").forEach { key ->
     val value=market.get(key)
     if(value!=null && value.isJsonPrimitive) {
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) {
        Text(humanizeField(key),fontSize=11.sp)
        Text(preview(value),color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
       }
       IconButton(onClick={onEdit("Game Data",listOf("componentMarketplace",key),value)}){Icon(Icons.Default.Edit,contentDescription="Edit "+key,tint=Muted)}
      }
     }
    }
    market.getAsJsonArray("m_ComponentMarks")?.forEachIndexed { index,value ->
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Text(SupplierNames.getOrNull(index) ?: "Supplier #"+index,Modifier.weight(1f),fontSize=10.sp)
      Text(preview(value),color=Gold,fontSize=10.sp)
      IconButton(onClick={onEdit("Game Data",listOf("componentMarketplace","m_ComponentMarks","["+index+"]"),value)}){Icon(Icons.Default.Edit,contentDescription="Edit supplier mark",tint=Muted)}
     }
    }
   }
  }
  item { GlassCard(Modifier.fillMaxWidth()) { Text("OPEN MARKET",color=Gold,fontWeight=FontWeight.Black,fontSize=13.sp); Text(openItems.size.toString()+" components in this save",color=Muted,fontSize=10.sp) } }
  items(openItems,key={"open-"+it.first}) { (index,element)->MarketplaceComponentCard(element,"m_Components",index,"Open Market",onEdit) }
  supplierRows.forEach { (supplierIndex,supplierElement)->
   val components=if(supplierElement.isJsonArray)supplierElement.asJsonArray.mapIndexed{index,item->index to item}else emptyList()
   item { GlassCard(Modifier.fillMaxWidth()) { Text(SupplierNames.getOrNull(supplierIndex) ?: "Supplier #"+supplierIndex,color=Gold,fontWeight=FontWeight.Black,fontSize=13.sp); Text(components.size.toString()+" supplier components",color=Muted,fontSize=10.sp) } }
   items(components,key={"supplier-"+supplierIndex+"-"+it.first}) { (componentIndex,component)->MarketplaceComponentCard(component,"m_CompanyComponents",componentIndex,SupplierNames.getOrNull(supplierIndex) ?: "Supplier #"+supplierIndex,onEdit,supplierIndex) }
  }
 }
}

@Composable
private fun MarketplaceComponentCard(element:JsonElement,arrayKey:String,componentIndex:Int,sourceLabel:String,onEdit:(String,List<String>,JsonElement)->Unit,supplierIndex:Int?=null) {
 if(!element.isJsonObject)return
 val component=element.asJsonObject
 val prefix=buildList {
  add("componentMarketplace");add(arrayKey)
  if(supplierIndex!=null)add("["+supplierIndex+"]")
  add("["+componentIndex+"]")
 }
 fun edit(field:String,value:JsonElement?){if(value!=null)onEdit("Game Data",prefix+field,value)}
 val type=component.get("m_Type")?.asInt ?: -1
 val rarity=component.get("m_Rarity")?.asInt ?: -1
 val stats=component.getAsJsonArray("m_Stats")
 GlassCard(Modifier.fillMaxWidth()) {
  Row(verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {
    Text(componentTypeName(type),fontWeight=FontWeight.Black,fontSize=14.sp)
    Text(sourceLabel+" • "+componentRarityName(rarity),color=Muted,fontSize=10.sp)
   }
   TextButton(onClick={edit("m_Type",component.get("m_Type"))}){Text("TYPE")}
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
   listOf("m_Price" to "Price","m_Discount" to "Discount","m_State" to "Stock state","m_Rarity" to "Rarity").forEach { (key,label)->
    val value=component.get(key)
    if(value!=null) Column(Modifier.weight(1f).clickable{edit(key,value)}.padding(vertical=4.dp)) {
     Text(label,color=Muted,fontSize=9.sp);Text(preview(value),color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
    }
   }
  }
  if(stats!=null) {
   Divider(color=Color.White.copy(alpha=0.12f))
   Text("PERFORMANCE STATS",color=Gold,fontWeight=FontWeight.Bold,fontSize=9.sp)
   stats.forEachIndexed { statIndex,stat->
    if(stat.isJsonObject) {
     val statObject=stat.asJsonObject
     val statId=statObject.get("Stat")?.asInt ?: -1
     val statValue=statObject.get("Value")
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Text(componentStatName(statId),Modifier.weight(1f),fontSize=11.sp)
      Text(statValue?.let { preview(it) } ?: "—",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
      if(statValue!=null)TextButton(onClick={onEdit("Game Data",prefix+listOf("m_Stats","["+statIndex+"]","Value"),statValue)}){Text("EDIT")}
      statObject.get("Stat")?.let{statField->IconButton(onClick={onEdit("Game Data",prefix+listOf("m_Stats","["+statIndex+"]","Stat"),statField)}){Icon(Icons.Default.Edit,contentDescription="Edit stat type",tint=Muted)}}
     }
    }
   }
  }
 }
}
@Composable
private fun InfoPage(doc: Mm4SaveCodec.Document, onEdit: (String,List<String>,JsonElement)->Unit) {
    val fields = listOf("teamName" to "Team name", "playerFirstName" to "Manager first name", "playerLastName" to "Manager last name", "gameDate" to "Career date", "playerAge" to "Manager age", "championshipTier" to "Championship tier", "championshipSeries" to "Championship series", "eventNumber" to "Event number", "hardMode" to "Hard mode", "timePlayed" to "Play time")
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(fields) { (key,label) ->
            val v = doc.info.get(key)
            if (v != null) GlassCard(Modifier.fillMaxWidth().clickable { onEdit("Save Info", listOf(key), v) }) {
                InfoLine(label, preview(v))
                Text("Tap to edit", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun AdvancedPage(doc: Mm4SaveCodec.Document, onEdit: (String,List<String>,JsonElement)->Unit) {
    var query by remember { mutableStateOf("") }
    val fields=remember(doc.data,query) {
        flatten(doc.data).filter { (path,value) ->
            path.none { it.startsWith("\$") } && value.isJsonPrimitive && (query.isBlank() ||
                humanizeField(path.last()).contains(query,true) ||
                path.joinToString(" ").contains(query,true))
        }
    }
    val groups=fields.groupBy { it.first.firstOrNull() ?: "Other" }.toSortedMap()
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("PENGATURAN LANJUTAN",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                Text("Bagian ini berisi field teknis yang belum punya editor khusus. Nama dan lokasi field ditampilkan agar jelas sumber datanya. Ubah hanya nilai yang kamu pahami; data mentah dan referensi internal tidak ditampilkan sebagai pengaturan biasa.",color=Muted,fontSize=11.sp)
                Text("${fields.size} editable fields",fontSize=20.sp,fontWeight=FontWeight.Black)
            }
        }
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Cari data lanjutan…")},leadingIcon={Icon(Icons.Default.Search,null)}) }
        groups.forEach { (group,items) ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(worldGroupLabel(group),color=Gold,fontWeight=FontWeight.Bold)
                    Text("${items.size} nilai yang bisa diubah",color=Muted,fontSize=10.sp)
                }
            }
            items(items,key={it.first.joinToString("/")}) { (path,value) ->
                GlassCard(Modifier.fillMaxWidth().clickable { onEdit("Game Data",path,value) }) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(humanizeField(path.last()),fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                            Text(path.dropLast(1).joinToString(" / "),color=Muted,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Text(preview(value),color=Gold,fontWeight=FontWeight.Bold,fontSize=12.sp)
                        Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

private fun facilityGroupLabel(pathType:Int):String = when(pathType) {
    0 -> "Fasilitas utama"
    1 -> "Fasilitas pengembangan"
    2 -> "Fasilitas lanjutan"
    3 -> "Fasilitas spesialis"
    4 -> "Fasilitas tingkat tinggi"
    else -> "Fasilitas"
}

private fun worldGroupLabel(key:String):String = when(key) {
    "championshipManager" -> "Kejuaraan & jenjang liga"
    "componentMarketplace" -> "Toko komponen mobil"
    "supplierNetworkManager" -> "Supplier & jaringan pemasok"
    "researchManager" -> "Riset & pengembangan mobil"
    "sessionManager" -> "Jadwal & sesi balapan"
    "circuitManager" -> "Sirkuit"
    "weatherManager" -> "Cuaca balapan"
    "regenRules" -> "Aturan regenerasi pembalap"
    "settings" -> "Pengaturan permainan"
    "challengeManager" -> "Tantangan karier"
    "invitationalChampionshipManager" -> "Kejuaraan invitasi"
    else -> humanLabel(key)
}

private data class EntityOption(val id:String,val name:String,val detail:String="")

private fun teamOptions(data:JsonObject):List<EntityOption> {
    val all=collectObjectsWithId(data).associateBy { it.second.get("\$id")?.asString }
    return data.getAsJsonObject("teamManager")?.getAsJsonArray("m_Teams")?.mapNotNull { ref ->
        val id=ref.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.asString ?: return@mapNotNull null
        val team=all[id]?.second ?: return@mapNotNull null
        EntityOption(id,team.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.ifBlank { null } ?: "Team $id")
    }?.distinctBy { it.id }.orEmpty()
}

private fun driverOptions(data:JsonObject):List<EntityOption> {
    val all=collectObjectsWithId(data).associateBy { it.second.get("\$id")?.asString }
    val teams=teamOptions(data).associate { it.id to it.name }
    return data.getAsJsonObject("driverManager")?.getAsJsonArray("m_Drivers")?.mapNotNull { ref ->
        val id=ref.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.asString ?: return@mapNotNull null
        val driver=all[id]?.second ?: return@mapNotNull null
        val details=driver.getAsJsonObject("m_PersonDetails")
        val name=details?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.ifBlank { null } ?: "Driver $id"
        val teamId=driver.getAsJsonObject("contract")?.getAsJsonObject("m_Team")?.get("\$ref")?.asString
        EntityOption(id,name,teamId?.let { teams[it] } ?: "Free Agent")
    }?.distinctBy { it.id }.orEmpty()
}

internal fun moveDriverToTeam(data:JsonObject,driverId:String,targetTeamId:String):Boolean {
    val teams=teamOptions(data)
    if(teams.none { it.id==targetTeamId }) return false
    val driver=collectObjectsWithId(data).firstOrNull { it.second.get("\$id")?.asString==driverId }?.second ?: return false
    val contract=driver.get("contract")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
    val teamRef=contract.get("m_Team")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
    if(teamRef.get("\$ref")?.asString==targetTeamId) return false
    val oldTeamId=teamRef.get("\$ref")?.asString
    teamRef.addProperty("\$ref",targetTeamId)
    val driverRef=JsonObject().apply { addProperty("\$ref",driverId) }
    val allTeams=collectObjectsWithId(data).map { it.second }
    allTeams.forEach { team ->
        val roster=team.get("m_Drivers")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach
        for(i in roster.size()-1 downTo 0) {
            if(roster[i].isJsonObject && roster[i].asJsonObject.get("\$ref")?.asString==driverId) roster.remove(i)
        }
    }
    val target=collectObjectsWithId(data).firstOrNull { it.second.get("\$id")?.asString==targetTeamId }?.second
    val targetRoster=target?.get("m_Drivers")?.takeIf { it.isJsonArray }?.asJsonArray
    if(targetRoster!=null && targetRoster.none { it.isJsonObject && it.asJsonObject.get("\$ref")?.asString==driverId }) targetRoster.add(driverRef)
    // The contract reference is authoritative; roster arrays are synchronized only when present.
    return oldTeamId!=targetTeamId
}

internal fun swapTeamsBetweenDivisions(data:JsonObject,teamA:String,teamB:String):Boolean {
    if(teamA==teamB) return false
    val objects=collectObjectsWithId(data).mapNotNull { (_,obj) -> obj.get("\$id")?.asString?.let { it to obj } }.toMap()
    val teamAObject=objects[teamA] ?: return false
    val teamBObject=objects[teamB] ?: return false
    val teamAId=teamAObject.get("m_TeamID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return false
    val teamBId=teamBObject.get("m_TeamID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return false
    if(teamAId==teamBId) return false
    val championshipRefs=data.getAsJsonObject("championshipManager")?.getAsJsonArray("championships") ?: return false
    val championships=championshipRefs.mapNotNull { ref ->
        val id=ref.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.asString ?: return@mapNotNull null
        objects[id]
    }
    data class Slot(val championship:JsonObject,val index:Int)
    fun findSlot(teamId:Int):Slot? {
        val matches=championships.flatMap { championship ->
            val ids=championship.get("m_TeamIDs")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@flatMap emptyList()
            ids.mapIndexedNotNull { index,value ->
                if(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asInt==teamId) Slot(championship,index) else null
            }
        }
        return matches.singleOrNull()
    }
    val slotA=findSlot(teamAId) ?: return false
    val slotB=findSlot(teamBId) ?: return false
    if(slotA.championship===slotB.championship) return false
    val teamAChamp=teamAObject.get("m_Championship")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
    val teamBChamp=teamBObject.get("m_Championship")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
    val champAId=slotA.championship.get("\$id")?.asString ?: return false
    val champBId=slotB.championship.get("\$id")?.asString ?: return false
    val oldA=teamAChamp.get("\$ref")?.asString ?: return false
    val oldB=teamBChamp.get("\$ref")?.asString ?: return false
    if(oldA!=champAId || oldB!=champBId) return false
    val idsA=slotA.championship.getAsJsonArray("m_TeamIDs")
    val idsB=slotB.championship.getAsJsonArray("m_TeamIDs")
    idsA.set(slotA.index,com.google.gson.JsonPrimitive(teamBId))
    idsB.set(slotB.index,com.google.gson.JsonPrimitive(teamAId))
    teamAChamp.addProperty("\$ref",champBId)
    teamBChamp.addProperty("\$ref",champAId)
    return true
}


private fun championshipEntityById(data:JsonObject, championshipId:Int):JsonObject? {
    val all=collectObjectsWithId(data).associateBy { it.second.get("\$id")?.asString }
    val refs=data.getAsJsonObject("championshipManager")?.getAsJsonArray("championships") ?: return null
    return refs.firstNotNullOfOrNull { ref ->
        val id=ref.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.asString ?: return@firstNotNullOfOrNull null
        all[id]?.second?.takeIf { it.get("m_ID")?.asInt==championshipId }
    }
}

internal fun careerDivisionLabel(championshipId:Int):String = when(championshipId) {
    0 -> "World Motorsport"
    1 -> "Asia Pacific Super Cup"
    2 -> "European Racing"
    3 -> "Open Wheel Sprint"
    else -> "Divisi $championshipId"
}

private fun divisionTeamIds(data:JsonObject,divisionId:Int):List<String> {
    val championship=championshipEntityById(data,divisionId) ?: return emptyList()
    val ids=championship.getAsJsonArray("m_TeamIDs") ?: return emptyList()
    val teamObjects=collectObjectsWithId(data).map { it.second }
    return ids.mapNotNull { teamId ->
        val numericId=teamId.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return@mapNotNull null
        teamObjects.firstOrNull { it.get("m_TeamID")?.takeIf { v -> v.isJsonPrimitive && v.asJsonPrimitive.isNumber }?.asInt==numericId }
            ?.get("\$id")?.asString
    }
}

private fun resolveRosterDriver(slot:JsonObject,objects:Map<String,JsonObject>):JsonObject? {
    val driver=slot.get("driver")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val inlineId=driver.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString
    if(inlineId!=null) return objects[inlineId] ?: driver
    val directRef=driver.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
    if(directRef!=null) return objects[directRef]
    val nestedRef=driver.getAsJsonObject("contract")?.getAsJsonObject("m_Driver")
        ?.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
    return nestedRef?.let { objects[it] }
}

private fun remapDriverSelfReferences(value:JsonElement,sourceId:String,targetId:String):JsonElement {
    val copy=value.deepCopy()
    fun visit(node:JsonElement) {
        when {
            node.isJsonObject -> {
                val obj=node.asJsonObject
                val ref=obj.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
                if(ref==sourceId) obj.addProperty("\$ref",targetId)
                obj.entrySet().toList().forEach { (_,child) -> visit(child) }
            }
            node.isJsonArray -> node.asJsonArray.forEach { visit(it) }
        }
    }
    visit(copy)
    return copy
}

/**
 * Apply a user-selected MM4 save as the F1-F4 source template.
 * Team names and both driver slots per team are copied by championship/team/roster order.
 * Target entity IDs, UUIDs, contracts, team links, career history and roster references remain local.
 */
internal fun applyOfficialF1Preset2026(data:JsonObject,templateData:JsonObject):Boolean {
    val targetObjects=collectObjectsWithId(data).mapNotNull { (_,obj) ->
        obj.get("\$id")?.asString?.let { it to obj }
    }.toMap()
    val sourceObjects=collectObjectsWithId(templateData).mapNotNull { (_,obj) ->
        obj.get("\$id")?.asString?.let { it to obj }
    }.toMap()

    fun championships(root:JsonObject,objects:Map<String,JsonObject>):List<JsonObject>? {
        val refs=root.getAsJsonObject("championshipManager")?.getAsJsonArray("championships") ?: return null
        val found=(0..3).map { divisionId ->
            val ref=refs.firstNotNullOfOrNull { row ->
                val id=row.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.asString
                    ?: return@firstNotNullOfOrNull null
                id.takeIf { objects[id]?.get("m_ID")?.takeIf { v -> v.isJsonPrimitive }?.asInt==divisionId }
            } ?: return null
            objects[ref] ?: return null
        }
        return found
    }

    val targetChampionships=championships(data,targetObjects) ?: return false
    val sourceChampionships=championships(templateData,sourceObjects) ?: return false
    val teamPlan=mutableListOf<Triple<JsonObject,JsonObject,Int>>()
    val driverPlan=mutableListOf<Pair<JsonObject,JsonObject>>()
    val seenTargetTeams=mutableSetOf<Int>()
    val seenSourceTeams=mutableSetOf<Int>()
    val seenTargetDrivers=mutableSetOf<String>()
    val seenSourceDrivers=mutableSetOf<String>()

    for(division in 0..3) {
        val targetIds=targetChampionships[division].get("m_TeamIDs")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        val sourceIds=sourceChampionships[division].get("m_TeamIDs")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        if(targetIds.size()!=10 || sourceIds.size()!=10) return false
        for(teamIndex in 0 until 10) {
            val targetTeamId=targetIds[teamIndex].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return false
            val sourceTeamId=sourceIds[teamIndex].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return false
            if(!seenTargetTeams.add(targetTeamId) || !seenSourceTeams.add(sourceTeamId)) return false
            val targetTeam=targetObjects.values.firstOrNull { it.get("m_TeamID")?.let { v -> v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asInt==targetTeamId }==true } ?: return false
            val sourceTeam=sourceObjects.values.firstOrNull { it.get("m_TeamID")?.let { v -> v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asInt==sourceTeamId }==true } ?: return false
            if(targetTeam.get("name")?.isJsonPrimitive!=true || targetTeam.get("shortName")?.isJsonPrimitive!=true) return false
            if(sourceTeam.get("name")?.isJsonPrimitive!=true || sourceTeam.get("shortName")?.isJsonPrimitive!=true) return false
            val targetRoster=targetTeam.getAsJsonObject("m_TeamDriverData")?.get("m_Data")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
            val sourceRoster=sourceTeam.getAsJsonObject("m_TeamDriverData")?.get("m_Data")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
            if(targetRoster.size()!=2 || sourceRoster.size()!=2) return false
            teamPlan.add(Triple(targetTeam,sourceTeam,division))
            for(slotIndex in 0..1) {
                val targetDriver=resolveRosterDriver(targetRoster[slotIndex].asJsonObject,targetObjects) ?: return false
                val sourceDriver=resolveRosterDriver(sourceRoster[slotIndex].asJsonObject,sourceObjects) ?: return false
                val targetDriverId=targetDriver.get("\$id")?.asString ?: return false
                val sourceDriverId=sourceDriver.get("\$id")?.asString ?: return false
                if(!seenTargetDrivers.add(targetDriverId) || !seenSourceDrivers.add(sourceDriverId)) return false
                val targetDetails=targetDriver.get("m_PersonDetails")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
                val sourceDetails=sourceDriver.get("m_PersonDetails")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
                val sourceNationality=sourceDetails.get("nationality")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
                if(sourceNationality.get("mNationalityID")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }!=true) return false
                if(targetDetails.get("nationality")?.isJsonObject!=true) return false
                if(targetDriver.get("m_Stats")?.isJsonObject!=true || sourceDriver.get("m_Stats")?.isJsonObject!=true) return false
                driverPlan.add(targetDriver to sourceDriver)
            }
        }
    }
    if(teamPlan.size!=40 || driverPlan.size!=80) return false

    // All structural validation is complete before the first mutation.
    // Preserve active career team names and short names exactly as stored.
    val preservedDriverFields=setOf("contract","careerHistory","id","\$id","\$version")
    driverPlan.forEach { (targetDriver,sourceDriver) ->
        val targetId=targetDriver.get("\$id").asString
        val sourceId=sourceDriver.get("\$id").asString
        sourceDriver.entrySet().forEach { (key,value) ->
            if(key !in preservedDriverFields) {
                targetDriver.add(key,remapDriverSelfReferences(value,sourceId,targetId))
            }
        }
        // Nationality is a game enum ID, not a display string. Preserve the exact
        // mNationalityID from the selected source save rather than deriving it from a flag/name.
        val sourceNationality=sourceDriver.getAsJsonObject("m_PersonDetails").getAsJsonObject("nationality")
        targetDriver.getAsJsonObject("m_PersonDetails").add("nationality",sourceNationality.deepCopy())
    }
    return true
}

internal fun renameCareerDivisions(data:JsonObject):Int {
    val names=mapOf(
        "World Motorsport" to "F1",
        "Asia Pacific Super Cup" to "F2",
        "European Racing" to "F3",
        "Open Wheel Sprint" to "F4"
    )
    var changed=0
    fun visit(node:JsonElement) {
        when {
            node.isJsonObject -> {
                val obj=node.asJsonObject
                listOf("name","m_Name","displayName","m_DisplayName","seriesName","m_SeriesName").forEach { key ->
                    val value=obj.get(key)
                    if(value!=null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        val replacement=names[value.asString]
                        if(replacement!=null) { obj.addProperty(key,replacement); changed++ }
                    }
                }
                obj.entrySet().forEach { (_,value) -> visit(value) }
            }
            node.isJsonArray -> node.asJsonArray.forEach { visit(it) }
        }
    }
    visit(data.get("championshipManager") ?: return 0)
    return changed
}

internal fun unlockSupplierNetwork(data:JsonObject):Int {
    val supplier=data.get("supplierNetworkManager")?.takeIf { it.isJsonObject }?.asJsonObject ?: return 0
    val dictionary=supplier.get("m_NodeDictionary") ?: return 0
    val entries:List<JsonElement> = when {
        dictionary.isJsonArray -> dictionary.asJsonArray.toList()
        dictionary.isJsonObject -> dictionary.asJsonObject.entrySet().map { it.value }
        else -> return 0
    }
    if(entries.isEmpty()) return 0

    // Build a read-only ID index first. Only supplier graph edges are traversed below.
    val byId=mutableMapOf<String,JsonObject>()
    val scan=java.util.ArrayDeque<JsonElement>()
    scan.add(data)
    while(scan.isNotEmpty()) {
        when(val element=scan.removeLast()) {
            is JsonObject -> {
                element.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString?.let { byId[it]=element }
                element.entrySet().forEach { (_,value) -> if(!value.isJsonNull) scan.add(value) }
            }
            is JsonArray -> element.forEach { if(!it.isJsonNull) scan.add(it) }
            else -> Unit
        }
    }

    val pending=java.util.ArrayDeque<JsonElement>()
    entries.forEach { entry ->
        if(entry.isJsonObject) {
            val value=entry.asJsonObject.get("Value")
            if(value!=null && !value.isJsonNull) pending.add(value)
            else if(entry.asJsonObject.has("\$ref") || entry.asJsonObject.has("m_Nodes") ||
                entry.asJsonObject.has("m_IsOwned") || entry.asJsonObject.has("m_HasOwnedBeenRevealed")) {
                pending.add(entry)
            }
        }
    }
    supplier.get("m_StartNode")?.takeIf { !it.isJsonNull }?.let { pending.add(it) }
    supplier.get("m_TargetNodes")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.forEach { if(!it.isJsonNull) pending.add(it) }

    var recognizedNodes=0
    val visited=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<JsonObject,Boolean>())
    while(pending.isNotEmpty()) {
        when(val element=pending.removeLast()) {
            is JsonObject -> {
                val ref=element.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
                if(ref!=null) {
                    byId[ref]?.let { pending.add(it) }
                    continue
                }
                if(!visited.add(element)) continue

                val owned=element.get("m_IsOwned")
                val revealed=element.get("m_HasOwnedBeenRevealed")
                val hasOwned=owned?.isJsonPrimitive==true && owned.asJsonPrimitive.isBoolean
                val hasRevealed=revealed?.isJsonPrimitive==true && revealed.asJsonPrimitive.isBoolean
                if(hasOwned || hasRevealed) {
                    recognizedNodes++
                    if(hasOwned && !owned!!.asBoolean) element.addProperty("m_IsOwned",true)
                    if(hasRevealed && !revealed!!.asBoolean) element.addProperty("m_HasOwnedBeenRevealed",true)
                }

                // Follow only graph edges; never recurse into arbitrary save objects.
                element.get("m_Nodes")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.forEach { if(!it.isJsonNull) pending.add(it) }
            }
            is JsonArray -> element.forEach { if(!it.isJsonNull) pending.add(it) }
            else -> Unit
        }
    }

    // MM4 serializes these category maps as Key/Value entries.
    listOf("m_SupplierUnlocked","m_SupplierAvailable").forEach { field ->
        val map=supplier.get(field) ?: return@forEach
        val rows=when {
            map.isJsonArray -> map.asJsonArray.toList()
            map.isJsonObject -> map.asJsonObject.entrySet().map { it.value }
            else -> emptyList()
        }
        rows.forEach { row ->
            if(row.isJsonObject) {
                val value=row.asJsonObject.get("Value")
                if(value?.isJsonPrimitive==true && value.asJsonPrimitive.isNumber && value.asInt!=1) {
                    row.asJsonObject.addProperty("Value",1)
                }
            }
        }
    }
    return recognizedNodes
}

private fun applyGodModeAction(current:Mm4SaveCodec.Document,action:String,templateData:JsonObject?=null):Mm4SaveCodec.Document? {
    val data=current.data.deepCopy().asJsonObject
    when {
        action=="max_balance" -> {
            val finance=data.getAsJsonObject("financeManager") ?: return null
            finance.addProperty("m_Balance",2_000_000_000L)
        }
        action=="unlock_hq" -> {
            val hq=data.getAsJsonObject("headquarters") ?: return null
            val upgrades=hq.getAsJsonArray("m_Upgrades") ?: return null
            var changed=0
            upgrades.forEach { row ->
                val value=row.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("Value")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                if(value.get("State")?.isJsonPrimitive == true) { value.addProperty("State",1); changed++ }
                if(value.get("weeksToUnlock")?.isJsonPrimitive == true) value.addProperty("weeksToUnlock",0)
            }
            if(changed==0) return null
        }
        action=="max_research" -> {
            val research=data.getAsJsonObject("researchManager") ?: return null
            val cap=research.get("m_ResearchCap")?.asInt ?: 100000
            research.addProperty("m_SeasonResearchPointsMM4",cap)
            research.addProperty("m_IsDebugUnlocked",true)
        }
        action=="debug_unlock" -> {
            val research=data.getAsJsonObject("researchManager") ?: return null
            research.addProperty("m_IsDebugUnlocked",true)
        }
        action=="rename_divisions" -> {
            if(renameCareerDivisions(data)==0) return null
        }
        action=="official_f1_2026" -> {
            if(templateData==null || !applyOfficialF1Preset2026(data,templateData)) return null
        }
        action.startsWith("swap_teams|") -> {
            val parts=action.split("|")
            if(parts.size!=3 || !swapTeamsBetweenDivisions(data,parts[1],parts[2])) return null
        }
        action.startsWith("move_driver|") -> {
            val parts=action.split("|")
            if(parts.size!=3 || !moveDriverToTeam(data,parts[1],parts[2])) return null
        }
        action=="unlock_suppliers" -> {
            if(unlockSupplierNetwork(data)==0) return null
        }
        else -> return null
    }
    return current.copy(data=data)
}

@Composable
private fun GodModePage(doc:Mm4SaveCodec.Document,onAction:(String)->Unit,onEdit:(String,List<String>,JsonElement)->Unit) {
    val supplierManager=doc.data.get("supplierNetworkManager")?.takeIf { it.isJsonObject }?.asJsonObject
    val supplierNodeCount=supplierManager?.get("m_NodeDictionary")?.let { node ->
        when {
            node.isJsonObject -> node.asJsonObject.size()
            node.isJsonArray -> node.asJsonArray.size()
            else -> 0
        }
    } ?: 0
    val balance=doc.data.getAsJsonObject("financeManager")?.get("m_Balance")?.asLong ?: 0L
    val hq=doc.data.getAsJsonObject("headquarters")?.getAsJsonArray("m_Upgrades")
    val unlocked=hq?.count { row ->
        row.isJsonObject && row.asJsonObject.get("Value")?.takeIf { it.isJsonObject }
            ?.asJsonObject?.get("State")?.let { it.isJsonPrimitive && it.asInt==1 } == true
    } ?: 0
    val research=doc.data.getAsJsonObject("researchManager")
    val teams=remember(doc.data) { teamOptions(doc.data) }
    val drivers=remember(doc.data) { driverOptions(doc.data) }
    var driverId by remember { mutableStateOf<String?>(null) }
    var targetTeam by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    val pickerOptions=when(picker) { "targetTeam" -> teams; "driver" -> drivers; else -> emptyList() }
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("GOD MODE",color=Gold,fontWeight=FontWeight.Black,fontSize=13.sp)
                Text("Kontrol khusus untuk mengubah karier secara ekstrem. Setiap tombol mengubah data save dan bisa dibatalkan dengan membuka file asli lagi.",color=Muted,fontSize=11.sp)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("F1–F4 DRIVER TEMPLATE",color=Gold,fontWeight=FontWeight.Bold)
                Text("Mengimpor profil, portrait, nationality, atribut, dan potential pembalap dari save template. Nama tim pada career aktif tidak disentuh.",color=Muted,fontSize=10.sp)
                
                Button(onClick={onAction("official_f1_2026")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Red)) { Text("PILIH & TERAPKAN TEMPLATE DRIVER") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("NAMA DIVISI RESMI",color=Gold,fontWeight=FontWeight.Bold)
                Text("Label F1–F4 dipakai editor berdasarkan championship ID. SlotOne.msv tidak menyimpan field nama divisi; judul seperti “World Motorsport Championship” berasal dari teks internal game dan tidak bisa diganti lewat save data saja.",color=Muted,fontSize=10.sp)
                InfoLine("ID 0",careerDivisionLabel(0))
                InfoLine("ID 1",careerDivisionLabel(1))
                InfoLine("ID 2",careerDivisionLabel(2))
                InfoLine("ID 3",careerDivisionLabel(3))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("MOVE DRIVER • PINDAH TIM",color=Gold,fontWeight=FontWeight.Bold)
                Text("Memindahkan pembalap dengan memperbarui referensi tim pada kontrak. Roster tim ikut disinkronkan jika struktur save menyediakan daftar m_Drivers.",color=Muted,fontSize=10.sp)
                Button(onClick={picker="driver"},modifier=Modifier.fillMaxWidth()) { Text(drivers.firstOrNull { it.id==driverId }?.name ?: "PILIH PEMBALAP") }
                Button(onClick={picker="targetTeam"},modifier=Modifier.fillMaxWidth()) { Text(teams.firstOrNull { it.id==targetTeam }?.name ?: "PILIH TIM TUJUAN") }
                Button(onClick={if(driverId!=null && targetTeam!=null) onAction("move_driver|$driverId|$targetTeam")},enabled=driverId!=null && targetTeam!=null,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Blue)) { Text("PINDAHKAN PEMBALAP") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("KEUANGAN TANPA BATAS",fontWeight=FontWeight.Bold)
                Text("Saldo sekarang ${formatMoney(balance)}",color=Muted,fontSize=11.sp)
                Button(onClick={onAction("max_balance")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Red)) { Text("SET SALDO £2 MILIAR") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("HQ INSTANT UPGRADE",fontWeight=FontWeight.Bold)
                Text("$unlocked dari ${hq?.size() ?: 0} upgrade fasilitas berstatus terbuka.",color=Muted,fontSize=11.sp)
                Button(onClick={onAction("unlock_hq")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Gold)) { Text("BUKA SEMUA UPGRADE HQ") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("RISET MAKSIMUM",fontWeight=FontWeight.Bold)
                Text("Riset saat ini ${research?.get("m_SeasonResearchPointsMM4")?.let { preview(it) } ?: "—"} / ${research?.get("m_ResearchCap")?.let { preview(it) } ?: "—"}",color=Muted,fontSize=11.sp)
                Button(onClick={onAction("max_research")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Blue)) { Text("MAKSIMALKAN RISET & DEBUG") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("DEBUG / UNLOCK FLAG",fontWeight=FontWeight.Bold)
                Text("Mengaktifkan flag debug yang memang tersedia di save. Tidak mengarang flag store yang tidak ada di struktur file.",color=Muted,fontSize=11.sp)
                OutlinedButton(onClick={onAction("debug_unlock")},modifier=Modifier.fillMaxWidth()) { Text("AKTIFKAN DEBUG UNLOCK") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("BUKA JARINGAN SUPPLIER",fontWeight=FontWeight.Bold)
                Text(
                    if(supplierNodeCount>0) "Ditemukan $supplierNodeCount entri jaringan supplier. Membuka node yang benar-benar tersimpan; komponen terpasang tidak diubah."
                    else "Save ini belum memiliki node jaringan supplier (dictionary kosong). Game belum menyimpan graph yang bisa dibuka, jadi tombol dinonaktifkan agar tidak memberi hasil palsu.",
                    color=Muted,fontSize=11.sp
                )
                Button(
                    onClick={onAction("unlock_suppliers")},
                    enabled=supplierNodeCount>0,
                    modifier=Modifier.fillMaxWidth(),
                    colors=ButtonDefaults.buttonColors(containerColor=Blue,disabledContainerColor=PanelAlt)
                ) { Text(if(supplierNodeCount>0) "BUKA SEMUA SUPPLIER" else "JARINGAN BELUM TERSEDIA") }
            }
        }
    }
    if(picker!=null) AlertDialog(
        onDismissRequest={picker=null},
        title={Text(when(picker) { "teamA"->"Pilih tim pertama"; "teamB"->"Pilih tim kedua"; "driver"->"Pilih pembalap"; else->"Pilih tim tujuan" })},
        text={
            LazyColumn(Modifier.heightIn(max=420.dp)) {
                items(pickerOptions,key={it.id}) { option ->
                    TextButton(onClick={
                        when(picker) { "teamA"->teamA=option.id; "teamB"->teamB=option.id; "driver"->driverId=option.id; "targetTeam"->targetTeam=option.id }
                        picker=null
                    },modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(option.name,color=Color.White,fontWeight=FontWeight.SemiBold)
                            if(option.detail.isNotBlank()) Text(option.detail,color=Muted,fontSize=10.sp)
                        }
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={picker=null}) { Text("Tutup") }}
    )
}
@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    var entered by remember { mutableStateOf(false) }
    val edgeAlpha = 0.18f
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0.965f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "glass-card-entrance"
    )
    Column(
        modifier.graphicsLayer {
            scaleX = entrance
            scaleY = entrance
            alpha = entrance
        }.animateContentSize(animationSpec = tween(260)).clip(shape)
            .border(1.dp, Color.White.copy(alpha = edgeAlpha), shape)
            .background(Brush.linearGradient(listOf(Color(0xE02A3039), Color(0xC91A212A), Color(0xE0141A22))))
            .padding(1.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(listOf(Color(0xD51B222B), Color(0xC9151B23), Color(0xE010151C)))),
    ) {
        Box(Modifier.padding(start = 14.dp, top = 12.dp).width(28.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(Red))
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), content = content)
    }
}

@Composable
private fun QuickLink(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xD51D242D), Color(0xB5151B23))))
            .border(1.dp, GlassEdge, RoundedCornerShape(14.dp))
            .clickable(onClick = click).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x22E10600)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Red)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(desc, color = Muted, fontSize = 10.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Gold)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 11.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

private fun formatMoney(value: Long): String {
    val sign = if (value < 0) "-" else ""
    return sign + "£" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.UK).format(kotlin.math.abs(value))
}

private fun humanizeField(value: String): String = value.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace("m_", "").replace('_', ' ').split(' ').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }

@Composable
private fun JsonBrowser(
    root: JsonObject,
    rootName: String,
    query: String,
    moduleFilter: String?,
    onEdit: (List<String>, JsonElement) -> Unit
) {
    var expanded by remember(root, moduleFilter) { mutableStateOf(setOf<String>()) }
    val allNodes = remember(root) { flatten(root).filterNot { entry -> entry.first.lastOrNull()?.let(::isMetadataKey) == true } }
    val matches = remember(allNodes, query, moduleFilter) {
        allNodes.filter { (path, value) ->
            (moduleFilter == null || path.firstOrNull() == moduleFilter) &&
                (query.isBlank() ||
                    path.joinToString(".").contains(query, ignoreCase = true) ||
                    value.toString().contains(query, ignoreCase = true))
        }
    }
    val categories = remember(root, moduleFilter) {
        root.entrySet().filter { !isMetadataKey(it.key) && (moduleFilter == null || it.key == moduleFilter) }
            .sortedBy { it.key.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (query.isBlank()) "WORKSPACE / $rootName" else "HASIL PENCARIAN",
                    color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    if (query.isBlank()) "${categories.size} kelompok data • ${allNodes.size} field"
                    else "${matches.size} hasil ditemukan",
                    color = Muted, fontSize = 11.sp
                )
            }
            if (query.isBlank()) {
                TextButton(onClick = { expanded = if (expanded.size == categories.size) emptySet() else categories.map { it.key }.toSet() }) {
                    Text(if (expanded.size == categories.size) "TUTUP SEMUA" else "BUKA SEMUA",
                        color = Gold, fontSize = 9.sp)
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (query.isNotBlank()) {
                items(matches, key = { it.first.joinToString("/") }) { (path, value) ->
                    FieldRow(path, value, 0, onEdit)
                }
            } else {
                items(categories, key = { it.key }) { entry ->
                    val path = listOf(entry.key)
                    val isOpen = entry.key in expanded
                    val count = flatten(entry.value).size + 1
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF242B34), Color(0xFF1A2027))))
                            .padding(1.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                                .background(Panel).clickable {
                                    if (entry.value.isJsonObject || entry.value.isJsonArray) {
                                        expanded = if (isOpen) expanded - entry.key else expanded + entry.key
                                    } else onEdit(path, entry.value)
                                }.padding(horizontal = 15.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF30313A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(categoryIcon(entry.key), contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(humanLabel(entry.key), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(categoryDescription(entry.value, count), color = Muted, fontSize = 10.sp)
                            }
                            Text(count.toString(), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (isOpen) Icons.Default.ExpandLess else if (entry.value.isJsonObject || entry.value.isJsonArray) Icons.Default.ExpandMore else Icons.Default.Edit,
                                contentDescription = if (isOpen) "Tutup kelompok" else "Buka kelompok",
                                tint = Muted, modifier = Modifier.size(20.dp)
                            )
                        }
                        if (isOpen) {
                            val children = if (entry.value.isJsonObject)
                                entry.value.asJsonObject.entrySet().sortedBy { it.key.lowercase() }.map { it.key to it.value }
                            else if (entry.value.isJsonArray)
                                entry.value.asJsonArray.mapIndexed { index, item -> "[$index]" to item }
                            else emptyList()
                            children.forEach { (key, value) ->
                                TreeField(
                                    label = key,
                                    value = value,
                                    path = path + key,
                                    depth = 1,
                                    expanded = expanded,
                                    onToggle = { childPath ->
                                        val id = childPath.joinToString("/")
                                        expanded = if (id in expanded) expanded - id else expanded + id
                                    },
                                    onEdit = onEdit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeField(
    label: String,
    value: JsonElement,
    path: List<String>,
    depth: Int,
    expanded: Set<String>,
    onToggle: (List<String>) -> Unit,
    onEdit: (List<String>, JsonElement) -> Unit
) {
    val id = path.joinToString("/")
    val isContainer = value.isJsonObject || value.isJsonArray
    val isOpen = id in expanded
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = (depth * 13).dp, end = 9.dp)
                .clickable { if (isContainer) onToggle(path) else onEdit(path, value) }
                .padding(start = 12.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).background(if (isContainer) Gold else Red))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(humanLabel(label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(preview(value), color = if (isContainer) Muted else Gold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                if (isContainer) { if (isOpen) Icons.Default.ExpandLess else Icons.Default.ChevronRight } else Icons.Default.Edit,
                contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp)
            )
        }
        if (isOpen && value.isJsonObject) {
            value.asJsonObject.entrySet().filterNot { isMetadataKey(it.key) }.sortedBy { it.key.lowercase() }.forEach { (key, child) ->
                TreeField(key, child, path + key, depth + 1, expanded, onToggle, onEdit)
            }
        } else if (isOpen && value.isJsonArray) {
            value.asJsonArray.forEachIndexed { index, child ->
                TreeField("Item ${index + 1}", child, path + "[$index]", depth + 1, expanded, onToggle, onEdit)
            }
        }
    }
}

@Composable
private fun FieldRow(path: List<String>, value: JsonElement, depth: Int, onEdit: (List<String>, JsonElement) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
            .clickable { onEdit(path, value) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(humanLabel(path.lastOrNull() ?: "Field"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(path.dropLast(1).joinToString(" / "), color = Muted, fontSize = 9.sp)
            Text(preview(value), color = Gold, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Muted)
    }
}

private fun isMetadataKey(key: String): Boolean = key.firstOrNull() == 36.toChar()

private fun humanLabel(raw: String): String {
    val spaced = raw.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace('_', ' ').replace('-', ' ')
        .replace(Regex("\\[(\\d+)]"), " $1")
    return spaced.split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

private fun categoryDescription(value: JsonElement, count: Int): String = when {
    value.isJsonObject -> "${value.asJsonObject.size()} data di dalam kelompok"
    value.isJsonArray -> "${value.asJsonArray.size()} item tersimpan"
    else -> "Nilai: ${preview(value).take(48)}"
}

@Composable
private fun categoryIcon(key: String) = when {
    key.contains("team", true) || key.contains("club", true) -> Icons.Default.Groups
    key.contains("driver", true) || key.contains("staff", true) -> Icons.Default.Person
    key.contains("race", true) || key.contains("event", true) -> Icons.Default.Flag
    key.contains("car", true) || key.contains("vehicle", true) -> Icons.Default.DirectionsCar
    key.contains("money", true) || key.contains("finance", true) || key.contains("budget", true) -> Icons.Default.AccountBalance
    key.contains("date", true) || key.contains("season", true) -> Icons.Default.CalendarMonth
    key.contains("color", true) || key.contains("visual", true) -> Icons.Default.Palette
    else -> Icons.Default.Folder
}

private fun displayInteger(value: String): String = runCatching {
    java.math.BigDecimal(value).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
}.getOrDefault(value)

private fun preview(value: JsonElement): String = when {
    value.isJsonNull -> "Tidak tersedia"
    value.isJsonObject -> "${value.asJsonObject.size()} data terkait"
    value.isJsonArray -> "${value.asJsonArray.size()} item"
    value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> if (value.asBoolean) "Aktif" else "Nonaktif"
    value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> runCatching {
        java.math.BigDecimal(value.asString).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
    }.getOrDefault("0")
    value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
    else -> "Data tersedia"
}

internal fun flatten(root: JsonElement): List<Pair<List<String>, JsonElement>> {
    val result = mutableListOf<Pair<List<String>, JsonElement>>()
    fun visit(node: JsonElement, path: List<String>) {
        if (path.isNotEmpty()) result += path to node
        when {
            node.isJsonObject -> node.asJsonObject.entrySet().filterNot { isMetadataKey(it.key) }.forEach { (key, child) ->
                visit(child, path + key)
            }
            node.isJsonArray -> node.asJsonArray.forEachIndexed { index, child ->
                visit(child, path + "[$index]")
            }
        }
    }
    visit(root, emptyList())
    return result
}


internal fun stepNumericValue(current: String, step: Double, direction: Int): String? = runCatching {
    val currentValue = java.math.BigDecimal(current)
    val stepValue = java.math.BigDecimal.valueOf(step).multiply(java.math.BigDecimal.valueOf(direction.toLong()))
    currentValue.add(stepValue).stripTrailingZeros().toPlainString()
}.getOrNull()

@Composable
private fun WatermarkFooter() {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF090D12))
            .border(width = 0.5.dp, color = GlassEdge)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(18.dp).height(2.dp).background(Red))
        Spacer(Modifier.width(8.dp))
        Text("ENGINEERED FOR THE PADDOCK", color = Muted, fontSize = 8.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("MOREPP", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    }
}

internal fun editorBounds(document:Mm4SaveCodec.Document?,root:String,path:List<String>):Pair<String?,String?> {
    if(root!="Game Data" || path.isEmpty()) return null to null
    return when {
        path.last() in setOf("m_Stat","m_Potential") -> "0" to "20"
        path==listOf("financeManager","m_Balance") -> "0" to "2000000000"
        path==listOf("influenceManager","m_Influence") -> "0" to "2000000000"
        path.firstOrNull()=="headquarters" && path.lastOrNull()=="State" -> "0" to "2"
        path.firstOrNull()=="headquarters" && path.lastOrNull()=="pathType" -> "0" to "4"
        path.size==4 && path[0]=="headquarters" && path[1]=="m_CurrentTierInformation" && path[3]=="Value" -> {
            val index=path[2].removePrefix("[").removeSuffix("]").toIntOrNull()
            val row=index?.let { document?.data?.getAsJsonObject("headquarters")?.getAsJsonArray("m_CurrentTierInformation")?.get(it) }
            val key=row?.takeIf { it.isJsonObject }?.asJsonObject?.get("Key")?.asInt
            val nodes=document?.data?.getAsJsonObject("headquarters")?.getAsJsonArray("m_Upgrades")
            val max=if(key!=null) nodes?.count { node ->
                node.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonObject("Value")?.get("pathType")?.asInt==key
            } else null
            if(max!=null && max>0) "0" to max.toString() else null to null
        }
        else -> null to null
    }
}

@Composable
private fun EditValueDialog(target: EditTarget, onClose: () -> Unit, onSave: (JsonElement) -> Unit) {
    val primitive = target.value.asJsonPrimitive
    val isBoolean = primitive.isBoolean
    val isNumber = primitive.isNumber
    var value by remember(target) { mutableStateOf(if (primitive.isString) primitive.asString else primitive.toString()) }
    val currentNumber = if (isNumber) runCatching { primitive.asDouble }.getOrDefault(0.0) else 0.0
    val isAttribute = target.path.lastOrNull() in setOf("m_Stat", "m_Potential")
    val attributeValue = currentNumber.toInt().coerceIn(0, 20)
    val meaning = when { attributeValue <= 4 -> "Sangat rendah"; attributeValue <= 8 -> "Rendah"; attributeValue <= 12 -> "Menengah"; attributeValue <= 16 -> "Tinggi"; else -> "Elite" }
    var validationMessage by remember(target) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(humanizeField(target.path.lastOrNull() ?: "Edit value")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.path.joinToString("  /  ") { humanizeField(it) }, color = Muted, fontSize = 10.sp)
                when {
                    isBoolean -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nonaktif", Modifier.weight(1f))
                        Switch(checked = value.toBoolean(), onCheckedChange = { value = it.toString() })
                        Text("Aktif", Modifier.padding(start = 8.dp))
                    }
                    isNumber -> {
                        if (isAttribute) Text("BATAS ATRIBUT 0–20", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        OutlinedTextField(
                            value = value,
                            onValueChange = { typed -> if (typed.isEmpty() || typed.all { it.isDigit() }) value = typed },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isAttribute) "Nilai atribut" else "Masukkan angka") },
                            supportingText = { Text(if (isAttribute) "Masukkan angka bulat 0 sampai 20." else "Input angka manual. Batas save divalidasi otomatis.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                                        else -> OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if(target.path.lastOrNull()=="dateOfBirth") "Tanggal lahir (YYYY-MM-DD)" else "Text") }, singleLine = true)
                }
                validationMessage?.let { Text(it,color=Red,fontSize=10.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = when {
                    isBoolean -> com.google.gson.JsonPrimitive(value.toBoolean())
                    isNumber -> {
                        val number=java.math.BigDecimal(value)
                        val min=(if (isAttribute) "0" else target.minValue)?.let { java.math.BigDecimal(it) }
                        val max=(if (isAttribute) "20" else target.maxValue)?.let { java.math.BigDecimal(it) }
                        if (isAttribute && number.stripTrailingZeros().scale() > 0) { validationMessage="Atribut harus berupa angka bulat dari 0 sampai 20."; return@Button }
                        if((min!=null && number<min) || (max!=null && number>max)) {
                            validationMessage="Nilai harus berada di antara " + (target.minValue ?: "—") + " dan " + (target.maxValue ?: "—")
                            return@Button
                        }
                        com.google.gson.JsonPrimitive(number)
                    }
                    target.path.lastOrNull()=="dateOfBirth" -> {
                        val date=runCatching { java.time.LocalDate.parse(value.take(10)).toString() }.getOrElse {
                            validationMessage="Masukkan tanggal dengan format YYYY-MM-DD."
                            return@Button
                        }
                        com.google.gson.JsonPrimitive(date+"T00:00:00.0000000")
                    }
                    else -> com.google.gson.JsonPrimitive(value)
                }
                onSave(result)
            }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("SAVE CHANGES") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("CANCEL") } }
    )
}
internal fun resolvePath(root: JsonElement, path: List<String>): JsonElement? {
    var node = root
    for (part in path) {
        node = when {
            node.isJsonObject -> node.asJsonObject.get(part) ?: return null
            node.isJsonArray && part.startsWith("[") -> {
                val index = part.removePrefix("[").removeSuffix("]").toIntOrNull() ?: return null
                if (index !in 0 until node.asJsonArray.size()) return null
                node.asJsonArray[index]
            }
            else -> return null
        }
    }
    return node
}

internal fun setPath(root: JsonObject, path: List<String>, value: JsonElement): Boolean {
    if (path.isEmpty()) return false
    val parent = resolvePath(root, path.dropLast(1)) ?: return false
    val key = path.last()
    return when {
        parent.isJsonObject -> {
            if (!parent.asJsonObject.has(key)) false
            else { parent.asJsonObject.add(key, value.deepCopy()); true }
        }
        parent.isJsonArray && key.startsWith("[") -> {
            val index = key.removePrefix("[").removeSuffix("]").toIntOrNull() ?: return false
            if (index !in 0 until parent.asJsonArray.size()) false
            else { parent.asJsonArray.set(index, value.deepCopy()); true }
        }
        else -> false
    }
}

internal fun appendChild(
    root: JsonObject,
    path: List<String>,
    key: String?,
    value: JsonElement
): Boolean {
    val target = resolvePath(root, path) ?: return false
    return when {
        target.isJsonObject -> {
            val property = key?.takeIf { it.isNotBlank() } ?: return false
            if (target.asJsonObject.has(property)) false
            else { target.asJsonObject.add(property, value.deepCopy()); true }
        }
        target.isJsonArray -> {
            target.asJsonArray.add(value.deepCopy())
            true
        }
        else -> false
    }
}

internal fun removePath(root: JsonObject, path: List<String>): Boolean {
    if (path.isEmpty()) return false
    val parent = resolvePath(root, path.dropLast(1)) ?: return false
    val key = path.last()
    return when {
        parent.isJsonObject -> parent.asJsonObject.remove(key) != null
        parent.isJsonArray && key.startsWith("[") -> {
            val index = key.removePrefix("[").removeSuffix("]").toIntOrNull() ?: return false
            if (index !in 0 until parent.asJsonArray.size()) false
            else { parent.asJsonArray.remove(index); true }
        }
        else -> false
    }
}


private fun readSaveBytes(input: java.io.InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= 64 * 1024 * 1024) { "File terlalu besar untuk dibuka dengan aman." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}


internal fun mutateDocument(
    current: Mm4SaveCodec.Document,
    rootName: String,
    mutation: (JsonObject) -> Boolean
): Mm4SaveCodec.Document? {
    val sourceRoot = if (rootName == "Save Info") current.info else current.data
    val editedRoot = sourceRoot.deepCopy().asJsonObject
    if (!mutation(editedRoot)) return null
    return if (rootName == "Save Info") {
        current.copy(info = editedRoot)
    } else {
        current.copy(data = editedRoot)
    }
}
