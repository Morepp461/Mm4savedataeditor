package com.morepp.mm4savedataeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF101216)
private val Panel = Color(0xFF1B2027)
private val PanelAlt = Color(0xFF282F39)
private val Red = Color(0xFFE53935)
private val Gold = Color(0xFFD9B45B)
private val Muted = Color(0xFF9CA6B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditorApp() }
    }
}

private data class EditTarget(
    val root: String,
    val path: List<String>,
    val value: JsonElement
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<Mm4SaveCodec.Document?>(null) }
    var selectedTab by remember { mutableStateOf("Save Info") }
    var search by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf("Belum ada save data") }
    var sourceUri by remember { mutableStateOf<String?>(null) }
    var selectedModule by remember { mutableStateOf<String?>(null) }
    var hasChanges by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var confirmDiscardOpen by remember { mutableStateOf(false) }

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
                selectedTab = "Save Info"
                selectedModule = null
                search = ""
                hasChanges = false
            } catch (failure: Throwable) {
                errorMessage = failure.message ?: "Gagal membuka save."
            } finally {
                busy = false
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
                        val bytes = Mm4SaveCodec.encode(current)
                        val verified = Mm4SaveCodec.decode(bytes, current.sourceName)
                        check(verified.info == current.info && verified.data == current.data) {
                            "Validasi round-trip gagal."
                        }
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                            ?: error("Tidak bisa menulis file tujuan.")
                    }
                    hasChanges = false
                } catch (failure: Throwable) {
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
            secondary = Gold,
            onSurface = Color.White,
            onBackground = Color.White,
            onSurfaceVariant = Muted
        )
    ) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "MM4 SAVE DATA EDITOR",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "CAREER DATA WORKSHOP",
                                color = Gold,
                                fontSize = 9.sp,
                                letterSpacing = 1.3.sp
                            )
                        }
                    },
                    actions = {
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
                        containerColor = Ink,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
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
                    Row(
                        Modifier.padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Save Info", "Game Data").forEach { tab ->
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab; selectedModule = null; search = "" },
                                label = { Text(tab) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        placeholder = { Text("Cari field, ID, entitas, atau nilai…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    val root = if (selectedTab == "Save Info") current.info else current.data
                    if (selectedTab == "Game Data") {
                        val moduleNames = current.data.keySet().sorted()
                        LazyRow(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedModule == null,
                                    onClick = { selectedModule = null },
                                    label = { Text("All") }
                                )
                            }
                            items(moduleNames) { module ->
                                FilterChip(
                                    selected = selectedModule == module,
                                    onClick = { selectedModule = module },
                                    label = { Text(module) }
                                )
                            }
                        }
                    }
                    JsonBrowser(
                        root = root,
                        rootName = selectedTab,
                        query = search,
                        moduleFilter = if (selectedTab == "Game Data") selectedModule else null,
                        onEdit = { path, value ->
                            editTarget = EditTarget(selectedTab, path, value.deepCopy())
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

        editTarget?.let { target ->
            EditJsonDialog(
                target = target,
                onClose = { editTarget = null },
                onSave = { value ->
                    val current = document ?: return@EditJsonDialog
                    val updated = mutateDocument(current, target.root) {
                        setPath(it, target.path, value)
                    }
                    if (updated != null) {
                        document = updated
                        hasChanges = true
                        editTarget = null
                    }
                },
                onAddChild = { key, value ->
                    val current = document ?: return@EditJsonDialog
                    val updated = mutateDocument(current, target.root) {
                        appendChild(it, target.path, key, value)
                    }
                    if (updated != null) {
                        document = updated
                        hasChanges = true
                        editTarget = null
                    }
                },
                onDelete = {
                    val current = document ?: return@EditJsonDialog
                    val updated = mutateDocument(current, target.root) {
                        removePath(it, target.path)
                    }
                    if (updated != null) {
                        document = updated
                        hasChanges = true
                        editTarget = null
                    }
                }
            )
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
                .background(
                    Brush.linearGradient(
                        listOf(PanelAlt, Panel, Color(0xFF3A2024))
                    )
                )
                .padding(24.dp)
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = Red,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "YOUR CAREER.\nYOUR RULES.",
                fontSize = 30.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Buka save Motorsport Manager 4 untuk menjelajahi dan mengedit data karier.",
                color = Muted
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpen,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("BUKA FILE SAVE (.MSV)")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "DYNAMIC DATA BROWSER • RAW JSON EDITOR • LZ4 EXPORT",
                color = Gold,
                fontSize = 9.sp
            )
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
            .padding(14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(PanelAlt, Panel, Color(0xFF382326))
                )
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), fontWeight = FontWeight.Black)
            Text(
                fileName,
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (hasChanges) "UNSAVED CHANGES" else "SAVE DATA READY",
                color = if (hasChanges) Gold else Color(0xFF82D7A2),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Button(
            onClick = onExport,
            colors = ButtonDefaults.buttonColors(containerColor = Red)
        ) {
            Text("EXPORT")
        }
    }
}

@Composable
private fun JsonBrowser(
    root: JsonObject,
    rootName: String,
    query: String,
    moduleFilter: String?,
    onEdit: (List<String>, JsonElement) -> Unit
) {
    val allEntries = remember(root) { flatten(root) }
    val filteredEntries = remember(allEntries, query, moduleFilter) {
        allEntries.filter { (path, value) ->
            (moduleFilter == null || path.firstOrNull() == moduleFilter) &&
                (query.isBlank() ||
                    path.joinToString(".").contains(query, ignoreCase = true) ||
                    ((value.isJsonPrimitive || value.isJsonNull) &&
                        value.toString().contains(query, ignoreCase = true)))
        }
    }
    val groups = remember(filteredEntries) {
        filteredEntries.groupBy { it.first.firstOrNull() ?: "Data" }
    }
    var expanded by remember(rootName, moduleFilter) { mutableStateOf(setOf<String>()) }
    LaunchedEffect(query) {
        if (query.isNotBlank()) expanded = groups.keys
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (rootName == "Save Info") "INFORMASI KARIER" else "DATABASE KARIER",
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    if (query.isBlank()) "${groups.size} kategori • ${filteredEntries.size} data" else "${filteredEntries.size} hasil pencarian",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            if (query.isBlank()) {
                TextButton(onClick = {
                    expanded = if (expanded.size == groups.size) emptySet() else groups.keys
                }) {
                    Text(if (expanded.size == groups.size) "Tutup semua" else "Buka semua", color = Gold, fontSize = 11.sp)
                }
            }
        }
        if (filteredEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada data yang cocok. Coba kata kunci lain.", color = Muted)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                groups.forEach { (groupKey, items) ->
                    val groupValue = root.get(groupKey)
                    val isOpen = query.isNotBlank() || groupKey in expanded
                    item(key = "group:$groupKey") {
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF242B35), Color(0xFF1A2028), Color(0xFF292326))
                                    )
                                )
                                .clickable {
                                    expanded = if (groupKey in expanded) expanded - groupKey else expanded + groupKey
                                }
                                .padding(15.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(humanizeField(groupKey), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${items.size} data • ${dataTypeLabel(groupValue)}",
                                        color = Muted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(preview(groupValue ?: JsonParser.parseString("null")), color = Gold, fontSize = 11.sp)
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isOpen) "Tutup kategori" else "Buka kategori",
                                    tint = Gold
                                )
                            }
                            if (isOpen && groupValue != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "PILIH DATA DI BAWAH UNTUK MELIHAT ATAU MENGUBAH NILAINYA",
                                    color = Muted,
                                    fontSize = 9.sp,
                                    letterSpacing = .5.sp
                                )
                            }
                        }
                    }
                    if (isOpen) {
                        items.filter { it.first.size > 1 || query.isNotBlank() }.forEach { (path, value) ->
                            item(key = "field:${path.joinToString("/")}") {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1B222A))
                                        .clickable { onEdit(path, value) }
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            humanizeField(path.lastOrNull() ?: groupKey),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            path.dropLast(1).drop(1).joinToString("  ›  ") { humanizeField(it) }
                                                .ifBlank { humanizeField(groupKey) },
                                            color = Muted,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            preview(value),
                                            color = Gold,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(dataTypeLabel(value), color = Muted, fontSize = 9.sp)
                                        Icon(Icons.Default.Edit, contentDescription = "Edit data", tint = Color(0xFFB8C1CD), modifier = Modifier.padding(top = 6.dp).size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun humanizeField(raw: String): String {
    val known = mapOf(
        "createdDate" to "Tanggal Dibuat",
        "lastEditedDate" to "Terakhir Diubah",
        "gameDate" to "Tanggal Dalam Game",
        "teamColor" to "Warna Tim",
        "primaryColor" to "Warna Utama",
        "secondaryColor" to "Warna Sekunder",
        "teamName" to "Nama Tim",
        "playerName" to "Nama Pemain",
        "teams" to "Daftar Tim",
        "drivers" to "Daftar Pembalap",
        "staff" to "Staf",
        "sponsors" to "Sponsor",
        "calendar" to "Kalender",
        "races" to "Balapan",
        "standings" to "Klasemen",
        "contracts" to "Kontrak",
        "finances" to "Keuangan",
        "facilities" to "Fasilitas",
        "car" to "Mobil Balap",
        "research" to "Riset",
        "championship" to "Kejuaraan"
    )
    known[raw]?.let { return it }
    val spaced = raw.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .replace('-', ' ')
    return spaced.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun dataTypeLabel(value: JsonElement?): String = when {
    value == null || value.isJsonNull -> "KOSONG"
    value.isJsonObject -> "OBJEK"
    value.isJsonArray -> "DAFTAR"
    value.asJsonPrimitive.isBoolean -> "LOGIKA"
    value.asJsonPrimitive.isNumber -> "ANGKA"
    else -> "TEKS"
}

private fun preview(value: JsonElement): String = when {
    value.isJsonNull -> "null"
    value.isJsonObject -> "{ ${value.asJsonObject.size()} properties }"
    value.isJsonArray -> "[ ${value.asJsonArray.size()} items ]"
    else -> value.toString()
}

internal fun flatten(root: JsonElement): List<Pair<List<String>, JsonElement>> {
    val result = mutableListOf<Pair<List<String>, JsonElement>>()
    fun visit(node: JsonElement, path: List<String>) {
        if (path.isNotEmpty()) result += path to node
        when {
            node.isJsonObject -> node.asJsonObject.entrySet().forEach { (key, child) ->
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

@Composable
private fun EditJsonDialog(
    target: EditTarget,
    onClose: () -> Unit,
    onSave: (JsonElement) -> Unit,
    onAddChild: (String?, JsonElement) -> Unit,
    onDelete: () -> Unit
) {
    var raw by remember(target) { mutableStateOf(target.value.toString()) }
    var newKey by remember(target) { mutableStateOf("") }
    var newValue by remember(target) { mutableStateOf("null") }
    var invalid by remember(target) { mutableStateOf(false) }
    var showAdd by remember(target) { mutableStateOf(false) }
    var confirmDelete by remember(target) { mutableStateOf(false) }
    val isObject = target.value.isJsonObject
    val isArray = target.value.isJsonArray

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("EDIT FIELD") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    target.path.joinToString(" › "),
                    color = Gold,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it; invalid = false },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    isError = invalid,
                    label = { Text("JSON value") }
                )
                if (invalid) {
                    Text("JSON tidak valid atau operasi tidak bisa diterapkan.", color = Red, fontSize = 11.sp)
                }
                Text(
                    "Tipe data dipertahankan sebagai JSON. Contoh: 120, true, \"Nama\", null",
                    color = Muted,
                    fontSize = 10.sp
                )
                if (isObject || isArray) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { showAdd = !showAdd },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isObject) "TAMBAH PROPERTY" else "TAMBAH ARRAY ITEM")
                    }
                    if (showAdd) {
                        if (isObject) {
                            OutlinedTextField(
                                value = newKey,
                                onValueChange = { newKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Nama property baru") },
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = newValue,
                            onValueChange = { newValue = it; invalid = false },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nilai JSON baru") },
                            minLines = 2,
                            maxLines = 4
                        )
                        Button(
                            onClick = {
                                try {
                                    val parsed = JsonParser.parseString(newValue)
                                    if (isObject && newKey.isBlank()) {
                                        invalid = true
                                    } else {
                                        onAddChild(if (isObject) newKey else null, parsed)
                                    }
                                } catch (_: Throwable) {
                                    invalid = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Red)
                        ) {
                            Text("TAMBAHKAN")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (target.path.isNotEmpty()) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Red)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("HAPUS NODE INI")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        onSave(JsonParser.parseString(raw))
                    } catch (_: Throwable) {
                        invalid = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("SIMPAN NILAI") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("BATAL") }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus node?") },
            text = { Text("Node ini akan dihapus dari data yang sedang diedit. Export belum dilakukan.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Red)
                ) { Text("HAPUS") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("KEMBALI") }
            }
        )
    }
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
