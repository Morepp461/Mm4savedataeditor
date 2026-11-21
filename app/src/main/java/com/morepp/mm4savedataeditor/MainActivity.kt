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
                        check(Mm4SaveCodec.sameContent(verified, current)) {
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
                    CareerWorkspace(
                        document = current,
                        section = selectedTab,
                        onSection = { selectedTab = it; selectedDriver = null },
                        selectedDriver = selectedDriver,
                        onDriver = { selectedDriver = it },
                        onEdit = { root, path, value -> editTarget = EditTarget(root, path, value.deepCopy()) }
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
            if (target.value.isJsonPrimitive) {
                EditValueDialog(
                    target = target,
                    onClose = { editTarget = null },
                    onSave = { value ->
                        val current = document ?: return@EditValueDialog
                        val updated = mutateDocument(current, target.root) { setPath(it, target.path, value) }
                        if (updated != null) { document = updated; hasChanges = true; editTarget = null }
                    }
                )
            } else EditJsonDialog(
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


private val Sections = listOf("Overview", "Drivers", "Staff", "Teams", "Cars", "Finance", "HQ", "World", "Save Info", "Advanced")

@Composable
private fun CareerWorkspace(
    document: Mm4SaveCodec.Document, section: String, onSection: (String) -> Unit,
    selectedDriver: String?, onDriver: (String?) -> Unit,
    onEdit: (String, List<String>, JsonElement) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Sections) { s -> FilterChip(selected = section == s, onClick = { onSection(s) }, label = { Text(s, fontSize = 11.sp) }) }
        }
        when (section) {
            "Overview" -> OverviewPage(document, onSection)
            "Drivers" -> DriverPage(document, selectedDriver, onDriver, onEdit)
            "Staff" -> EntityCatalogPage(document, "staff", onEdit)
            "Teams" -> EntityCatalogPage(document, "team", onEdit)
            "Cars" -> EntityCatalogPage(document, "car", onEdit)
            "Finance" -> FinancePage(document, onEdit)
            "HQ" -> HqPage(document, onEdit)
            "World" -> WorldSettingsPage(document, onEdit)
            "Save Info" -> InfoPage(document, onEdit)
            else -> AdvancedPage(document, onEdit)
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
    val entities = doc.data.getAsJsonObject("entityManager")?.getAsJsonArray("m_Entities")
    val drivers = entities?.mapIndexedNotNull { i, el ->
        if (el.isJsonObject && el.asJsonObject.get("\$type")?.asString == "Driver")
            Triple(i, el.asJsonObject, el.asJsonObject.get("\$id")?.asString ?: i.toString()) else null
    }?.sortedBy { it.second.getAsJsonObject("m_PersonDetails")?.get("name")?.asString ?: "" }.orEmpty()
    val chosen = drivers.firstOrNull { it.third == selected }
    if (chosen == null) {
        var search by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), placeholder = { Text("Cari nama pembalap") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drivers.filter { it.second.getAsJsonObject("m_PersonDetails")?.get("name")?.asString?.contains(search,true) == true }) { (_, d, id) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).clickable { onSelect(id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Gold, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.getAsJsonObject("m_PersonDetails")?.get("name")?.asString ?: "Unknown", fontWeight = FontWeight.Bold)
                            Text("Driver ID " + (d.get("m_DriverID")?.asString ?: "—"), color = Muted, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    } else {
        val (idx, d, _) = chosen
        val details = d.getAsJsonObject("m_PersonDetails")
        val contract = d.getAsJsonObject("contract")
        val stats = d.getAsJsonObject("m_Stats")?.getAsJsonArray("m_Stats")
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelect(null) }) { Icon(Icons.Default.ArrowBack, "Back") }
                Column {
                    Text(details?.get("name")?.asString ?: "Driver profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Driver profile", color = Muted, fontSize = 10.sp)
                }
            }
            LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("PERSONAL INFORMATION", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        InfoLine("Date of birth", details?.get("dateOfBirth")?.asString?.take(10) ?: "—")
                        InfoLine("Driver ID", d.get("m_DriverID")?.asString ?: "—")
                        InfoLine("Nationality ID", details?.getAsJsonObject("nationality")?.get("mNationalityID")?.asString ?: "—")
                    }
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("CONTRACT", color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        InfoLine("Wage per race", contract?.get("m_PerRaceCost")?.asString ?: "—")
                        InfoLine("Contract end year", contract?.get("m_EndYear")?.asString ?: "—")
                        InfoLine("Races remaining", contract?.get("m_RacesLeft")?.asString ?: "—")
                    }
                }
                item { Text("DRIVER ATTRIBUTES", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                items(stats?.size() ?: 0) { n ->
                    val s = stats!![n].asJsonObject
                    val value = s.get("m_Stat")
                    GlassCard(Modifier.fillMaxWidth().clickable { if (value != null) onEdit("Game Data", listOf("entityManager","m_Entities","[" + idx + "]","m_Stats","m_Stats","[" + n + "]","m_Stat"), value) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Attribute " + (n + 1), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(value?.asString ?: "—", color = Gold, fontWeight = FontWeight.Bold)
                            Text("  /  Potential " + (s.get("m_Potential")?.asString ?: "—"), color = Muted, fontSize = 10.sp)
                            Icon(Icons.Default.Edit, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                item { Text("ALL DRIVER DATA", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                items(flatten(d).filter { it.second.isJsonPrimitive }) { (path, value) ->
                    val label = if (path.any { it == "m_Stats" }) statAttributeLabel("driver", "Driver", path.last().removePrefix("[").removeSuffix("]").toIntOrNull() ?: 0) else humanLabel(path.last())
                    GlassCard(Modifier.fillMaxWidth().clickable { onEdit("Game Data", listOf("entityManager","m_Entities","[$idx]") + path, value) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text(preview(value), color = Gold, fontSize = 11.sp)
                            Icon(Icons.Default.Edit, null, tint = Muted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                item { Text("Semua field primitif pada profil dapat diedit melalui kontrol bertipe. Field internal yang belum dikenal tetap dipertahankan.", color = Muted, fontSize = 10.sp) }
            }
        }
    }
}


@Composable
private fun TeamPage(doc: Mm4SaveCodec.Document, onEdit: (String,List<String>,JsonElement)->Unit) {
    val details = doc.data.getAsJsonObject("player")?.getAsJsonObject("m_TeamDetails")
    val colors = details?.getAsJsonObject("m_TeamColors")
    val teamName = doc.info.get("teamName")?.asString ?: "Career team"
    val colorNames = listOf("primaryColor", "secondaryColor", "tertiaryColor", "trimColor", "carColor", "staffPrimaryColor", "staffSecondaryColor", "helmetColour")
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
                        Text((if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else value.toString()), color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Icon(Icons.Default.Edit, null, tint = Muted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}


@Composable
private fun EntityCatalogPage(doc: Mm4SaveCodec.Document, category: String, onEdit: (String,List<String>,JsonElement)->Unit) {
    var selected by remember(category) { mutableStateOf<Int?>(null) }
    var query by remember(category) { mutableStateOf("") }
    val entities = doc.data.getAsJsonObject("entityManager")?.getAsJsonArray("m_Entities")
    val records = entities?.mapIndexedNotNull { index, element ->
        if (!element.isJsonObject) return@mapIndexedNotNull null
        val obj = element.asJsonObject
        val type = obj.get("$"+"type")?.asString ?: return@mapIndexedNotNull null
        val match = when(category) {
            "staff" -> listOf("staff","designer","strategist","mechanic","engineer").any { type.contains(it,true) } && !type.contains("driver",true)
            "team" -> type.contains("team",true) && !type.contains("teamdetail",true)
            else -> type.contains("car",true) || type.contains("vehicle",true)
        }
        if (!match) null else {
            val person = obj.getAsJsonObject("m_PersonDetails")
            val name = listOf("name","m_Name","Name","m_TeamName").firstNotNullOfOrNull { key ->
                person?.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                    ?: obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            } ?: (type + " #" + (obj.get("$"+"id")?.asString ?: index.toString()))
            Triple(index, obj, name)
        }
    }.orEmpty()
    val chosen = records.firstOrNull { it.first == selected }
    if (chosen == null) {
        LazyColumn(contentPadding=PaddingValues(14.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            item { GlassCard(Modifier.fillMaxWidth()) {
                Text(when(category) { "staff" -> "STAFF MANAGEMENT"; "team" -> "TEAM DATABASE"; else -> "CAR DATABASE" }, color=Gold, fontWeight=FontWeight.Bold, fontSize=11.sp)
                Text(records.size.toString()+" records found", fontSize=23.sp, fontWeight=FontWeight.Black)
                Text("Semua record yang dikenali di save ditampilkan. Pilih satu untuk membuka profil dan mengedit field secara langsung.", color=Muted, fontSize=11.sp)
            } }
            item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Cari nama atau tipe")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true) }
            items(records.filter { it.third.contains(query,true) || it.second.get("$"+"type")?.asString?.contains(query,true)==true },key={it.first}) { record ->
                GlassCard(Modifier.fillMaxWidth().clickable { selected=record.first }) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(when(category) { "staff" -> Icons.Default.Badge; "team" -> Icons.Default.Groups; else -> Icons.Default.DirectionsCar },null,tint=Gold,modifier=Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(record.third,fontWeight=FontWeight.Bold); Text(record.second.get("$"+"type")?.asString ?: category,color=Muted,fontSize=10.sp) }
                        Icon(Icons.Default.ChevronRight,null,tint=Muted)
                    }
                }
            }
            if(category=="team") item {
                val player=doc.data.getAsJsonObject("player")?.getAsJsonObject("m_TeamDetails")
                if(player!=null) GlassCard(Modifier.fillMaxWidth()) {
                    Text("PLAYER TEAM SETTINGS",color=Gold,fontWeight=FontWeight.Bold)
                    flatten(player).filter { it.second.isJsonPrimitive }.forEach { (path,value) ->
                        Row(Modifier.fillMaxWidth().clickable { onEdit("Game Data",listOf("player","m_TeamDetails")+path,value) }.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(humanLabel(path.last()),Modifier.weight(1f),fontSize=11.sp)
                            Text(preview(value),color=Gold,fontSize=11.sp)
                            Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    } else {
        val (index,record,name)=chosen
        val type=record.get("$"+"type")?.asString ?: category
        val fields=flatten(record).filter { it.second.isJsonPrimitive && it.first.lastOrNull()!="$"+"version" }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={selected=null}) { Icon(Icons.Default.ArrowBack,"Kembali") }
                Column { Text(name,fontWeight=FontWeight.Bold,fontSize=18.sp); Text(humanLabel(type),color=Muted,fontSize=10.sp) }
            }
            LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                item { GlassCard(Modifier.fillMaxWidth()) {
                    Text("PROFILE EDITOR",color=Gold,fontWeight=FontWeight.Bold,fontSize=10.sp)
                    Text(fields.size.toString()+" editable values",fontSize=21.sp,fontWeight=FontWeight.Black)
                    Text("Nama, umur, kontrak, atribut, identitas, dan nilai lain pada record ini dapat diedit tanpa input JSON.",color=Muted,fontSize=10.sp)
                } }
                items(fields,key={it.first.joinToString("/")}) { (path,value) ->
                    val label=if(path.any { it=="m_Stats" }) statAttributeLabel(category,type,path.last().removePrefix("[").removeSuffix("]").toIntOrNull() ?: 0) else humanLabel(path.last())
                    GlassCard(Modifier.fillMaxWidth().clickable { onEdit("Game Data",listOf("entityManager","m_Entities","[$index]")+path,value) }) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(label,fontWeight=FontWeight.SemiBold,fontSize=12.sp); Text(path.dropLast(1).joinToString(" / "),color=Muted,fontSize=9.sp,maxLines=1) }
                            Text(preview(value),color=Gold,fontWeight=FontWeight.Bold,fontSize=12.sp)
                            Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun statAttributeLabel(category:String,type:String,index:Int):String {
    val labels=when {
        category=="staff" && type.contains("designer",true) -> listOf("Performance","Reliability","Leadership","Bonus","Success Rate")
        category=="staff" && type.contains("strategist",true) -> listOf("Race Starts","Overtaking","Defending","Consistency","Focus")
        category=="staff" && type.contains("mechanic",true) -> listOf("Setup Skill","Car Knowledge","Pit Stop Skill","Reliability")
        category=="car" -> listOf("Top Speed","Acceleration","Braking","Low Speed Cornering","High Speed Cornering","Tyre Wear","Fuel Efficiency","Car Reliability")
        else -> listOf("Braking","Smoothness","Control","Consistency","Race Starts","Awareness","Cornering","Defending","Instinct","Focus","Reactions","Decisions","Overtaking","Bravery","Composure")
    }
    return labels.getOrNull(index) ?: "Attribute "+(index+1)
}

@Composable
private fun WorldSettingsPage(doc:Mm4SaveCodec.Document,onEdit:(String,List<String>,JsonElement)->Unit) {
    val keywords=listOf("store","shop","unlock","rule","regulation","gameplay","league","championship","race","calendar","difficulty","option","setting")
    val groups=doc.data.entrySet().filter { e -> keywords.any { e.key.contains(it,true) } }
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("CAREER WORLD & RULES",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
            Text("Store, unlocks, regulations, championship and gameplay settings detected in this save.",color=Muted,fontSize=11.sp)
            Text(groups.size.toString()+" data groups detected",fontSize=20.sp,fontWeight=FontWeight.Black)
        } }
        items(groups) { group ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(humanLabel(group.key),fontWeight=FontWeight.Bold)
                Text(categoryDescription(group.value,flatten(group.value).size),color=Muted,fontSize=10.sp)
                flatten(group.value).filter { it.second.isJsonPrimitive }.forEach { (path,value) ->
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
        items(history?.size() ?: 0) { i -> GlassCard(Modifier.fillMaxWidth()) { InfoLine("Transaction " + (i + 1), formatMoney(history!![i].asLong)) } }
    }
}

@Composable
private fun HqPage(doc: Mm4SaveCodec.Document,onEdit:(String,List<String>,JsonElement)->Unit) {
    val hq=doc.data.getAsJsonObject("headquarters") ?: JsonObject()
    val upgrades=hq.get("m_Upgrades")
    val completed=hq.get("m_CompletedUpgrades")
    val entries: List<Pair<String, JsonElement>> = when {
        upgrades==null -> emptyList()
        upgrades.isJsonObject -> upgrades.asJsonObject.entrySet().map { it.key to it.value }.sortedBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }
        upgrades.isJsonArray -> upgrades.asJsonArray.mapIndexed { i,v -> i.toString() to v }
        else -> emptyList()
    }
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("HEADQUARTERS",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
            val count=completed?.let { if(it.isJsonArray) it.asJsonArray.size() else if(it.isJsonObject) it.asJsonObject.size() else 0 } ?: 0
            Text("$"+"count completed upgrades",fontSize=22.sp,fontWeight=FontWeight.Black)
            Text("${entries.size} facility records",color=Muted,fontSize=11.sp)
            Text("Status fasilitas ditampilkan sebagai kontrol yang bisa dipahami.",color=Muted,fontSize=10.sp)
        } }
        items(entries,key={it.first}) { (key,value) ->
            val path=if(upgrades?.isJsonObject==true) listOf("headquarters","m_Upgrades",key) else listOf("headquarters","m_Upgrades","[$key]")
            val obj=value.takeIf { it.isJsonObject }?.asJsonObject
            val state=obj?.get("State")
            val weeks=obj?.get("WeeksToUnlock")
            val pathType=obj?.get("PathType")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Facility ${key.toIntOrNull()?.plus(1) ?: key}",fontWeight=FontWeight.Bold)
                Text(when(state?.takeIf { it.isJsonPrimitive }?.asInt) { 1 -> "Unlocked / Active"; 2 -> "In progress"; 0 -> "Locked"; else -> "Status not identified" },color=if(state?.takeIf { it.isJsonPrimitive }?.asInt==1) Color(0xFF82D7A2) else Gold,fontSize=11.sp)
                if(weeks!=null) InfoLine("Weeks to unlock",preview(weeks))
                if(pathType!=null) InfoLine("Upgrade path",preview(pathType))
                if(state!=null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onEdit("Game Data",path+"State",state)}) { Text("EDIT STATUS") }
                    if(weeks!=null) OutlinedButton(onClick={onEdit("Game Data",path+"WeeksToUnlock",weeks)}) { Text("EDIT TIMER") }
                }
                if(obj==null) Text("Detail fasilitas tersedia pada Advanced Data.",color=Muted,fontSize=10.sp)
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
                InfoLine(label, if (v.isJsonPrimitive && v.asJsonPrimitive.isString) v.asString else v.toString())
                Text("Tap to edit", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun AdvancedPage(doc: Mm4SaveCodec.Document, onEdit: (String,List<String>,JsonElement)->Unit) {
    val entries = doc.data.entrySet().sortedBy { it.key }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text("Advanced structures. Gunakan hanya jika memahami field game.", color = Muted, fontSize = 11.sp) }
        items(entries) { e ->
            GlassCard(Modifier.fillMaxWidth().clickable { onEdit("Game Data", listOf(e.key), e.value) }) {
                Text(humanizeField(e.key), fontWeight = FontWeight.Bold)
                Text(if (e.value.isJsonArray) "List • " + e.value.asJsonArray.size() + " items" else if (e.value.isJsonObject) "Data group • " + e.value.asJsonObject.size() + " fields" else "Value", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xDD2A3038), Color(0xCC191E25)))).padding(14.dp), content = content)
}

@Composable
private fun QuickLink(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable(onClick = click).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Gold); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(desc, color = Muted, fontSize = 10.sp) }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
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
    val allNodes = remember(root) { flatten(root) }
    val matches = remember(allNodes, query, moduleFilter) {
        allNodes.filter { (path, value) ->
            (moduleFilter == null || path.firstOrNull() == moduleFilter) &&
                (query.isBlank() ||
                    path.joinToString(".").contains(query, ignoreCase = true) ||
                    value.toString().contains(query, ignoreCase = true))
        }
    }
    val categories = remember(root, moduleFilter) {
        root.entrySet().filter { moduleFilter == null || it.key == moduleFilter }
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
            value.asJsonObject.entrySet().sortedBy { it.key.lowercase() }.forEach { (key, child) ->
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
private fun EditValueDialog(target: EditTarget, onClose: () -> Unit, onSave: (JsonElement) -> Unit) {
    val primitive = target.value.asJsonPrimitive
    val isBoolean = primitive.isBoolean
    val isNumber = primitive.isNumber
    var value by remember(target) { mutableStateOf(if (primitive.isString) primitive.asString else primitive.toString()) }
    var invalid by remember(target) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(humanizeField(target.path.lastOrNull() ?: "Edit value")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.path.joinToString("  /  "), color = Muted, fontSize = 10.sp)
                if (isBoolean) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Off", Modifier.weight(1f))
                        Switch(checked = value.toBoolean(), onCheckedChange = { value = it.toString() })
                        Text("On", Modifier.padding(start = 8.dp))
                    }
                } else {
                    OutlinedTextField(
                        value = value, onValueChange = { value = it; invalid = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isNumber) "Number" else "Text") },
                        singleLine = true,
                        isError = invalid
                    )
                }
                if (invalid) Text("Nilai tidak valid.", color = Red, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val result = when {
                        isBoolean -> com.google.gson.JsonPrimitive(value.toBoolean())
                        isNumber -> com.google.gson.JsonParser.parseString(value).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }
                        else -> com.google.gson.JsonPrimitive(value)
                    }
                    onSave(result)
                } catch (_: Throwable) { invalid = true }
            }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("SAVE CHANGES") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("CANCEL") } }
    )
}

@Composable
private fun EditJsonDialog(
    target: EditTarget,
    onClose: () -> Unit,
    onSave: (JsonElement) -> Unit,
    onAddChild: (String?, JsonElement) -> Unit,
    onDelete: () -> Unit
) {
    var working by remember(target) { mutableStateOf(target.value.deepCopy()) }
    var newKey by remember(target) { mutableStateOf("") }
    var newValue by remember(target) { mutableStateOf("") }
    var newType by remember(target) { mutableStateOf("Text") }
    var showAdd by remember(target) { mutableStateOf(false) }
    var invalid by remember(target) { mutableStateOf(false) }
    var confirmDelete by remember(target) { mutableStateOf(false) }
    val fields = remember(working) { flatten(working).filter { it.second.isJsonPrimitive } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("EDIT DATA GROUP") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(target.path.joinToString(" › "), color=Gold, fontSize=10.sp)
                Text(fields.size.toString()+" editable values",color=Muted,fontSize=10.sp)
                fields.forEach { (path,value) ->
                    val primitive=value.asJsonPrimitive
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(9.dp)) {
                        Text(humanLabel(path.last()),fontWeight=FontWeight.SemiBold,fontSize=11.sp)
                        if(primitive.isBoolean) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(if(primitive.asBoolean) "Enabled" else "Disabled",Modifier.weight(1f),color=Muted)
                                Switch(checked=primitive.asBoolean,onCheckedChange={ next -> working=replaceNested(working,path,com.google.gson.JsonPrimitive(next)) })
                            }
                        } else {
                            var textValue by remember(target,path,working.toString()) { mutableStateOf(if(primitive.isString) primitive.asString else primitive.toString()) }
                            OutlinedTextField(textValue,{ textValue=it },Modifier.fillMaxWidth(),singleLine=true)
                            TextButton(onClick={
                                try {
                                    val next=if(primitive.isNumber) com.google.gson.JsonParser.parseString(textValue).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) } else com.google.gson.JsonPrimitive(textValue)
                                    working=replaceNested(working,path,next)
                                } catch(_:Throwable) { invalid=true }
                            }) { Text("APPLY FIELD") }
                        }
                    }
                }
                if(working.isJsonObject || working.isJsonArray) {
                    OutlinedButton(onClick={showAdd=!showAdd},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Add,null); Text("ADD FIELD / ITEM") }
                    if(showAdd) {
                        if(working.isJsonObject) OutlinedTextField(newKey,{newKey=it},Modifier.fillMaxWidth(),label={Text("Field name")},singleLine=true)
                        OutlinedTextField(newValue,{newValue=it},Modifier.fillMaxWidth(),label={Text("Value")},singleLine=true)
                        Text("Value type",color=Muted,fontSize=10.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            listOf("Text","Number","Boolean","Object","List").forEach { type ->
                                FilterChip(selected=newType==type,onClick={newType=type},label={Text(type,fontSize=9.sp)})
                            }
                        }
                        Button(onClick={
                            try {
                                val value=when(newType) {
                                    "Number" -> com.google.gson.JsonParser.parseString(newValue).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }
                                    "Boolean" -> com.google.gson.JsonPrimitive(newValue.toBooleanStrict())
                                    "Object" -> JsonObject()
                                    "List" -> JsonArray()
                                    else -> com.google.gson.JsonPrimitive(newValue)
                                }
                                if(working.isJsonObject) {
                                    require(newKey.isNotBlank() && !working.asJsonObject.has(newKey))
                                    working=working.deepCopy().also { it.asJsonObject.add(newKey,value) }
                                } else working=working.deepCopy().also { it.asJsonArray.add(value) }
                                newKey=""; newValue=""; showAdd=false; invalid=false
                            } catch(_:Throwable) { invalid=true }
                        },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Red)) { Text("ADD") }
                        if(invalid) Text("Periksa nama field dan tipe nilainya.",color=Red,fontSize=10.sp)
                    }
                }
                if(target.path.isNotEmpty()) TextButton(onClick={confirmDelete=true},colors=ButtonDefaults.textButtonColors(contentColor=Red)) { Text("DELETE GROUP") }
            }
        },
        confirmButton={ Button(onClick={onSave(working)},colors=ButtonDefaults.buttonColors(containerColor=Red)) { Text("SAVE CHANGES") } },
        dismissButton={ TextButton(onClick=onClose) { Text("CANCEL") } }
    )
    if(confirmDelete) AlertDialog(
        onDismissRequest={confirmDelete=false},
        title={Text("Delete this data group?")},
        text={Text("This removes the selected group from the edited save.")},
        confirmButton={TextButton(onClick={onDelete}) { Text("DELETE") }},
        dismissButton={TextButton(onClick={confirmDelete=false}) { Text("CANCEL") }}
    )
}

private fun replaceNested(root:JsonElement,path:List<String>,value:JsonElement):JsonElement {
    if(path.isEmpty()) return value.deepCopy()
    val wrapper=JsonObject()
    wrapper.add("root",root.deepCopy())
    val ok=setPath(wrapper,listOf("root")+path,value)
    return if(ok) wrapper.get("root") else root
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
