package com.morepp.mm4savedataeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private val Ink = Color(0xFF101A3A)
private val Panel = Color(0xD91D2D58)
private val PanelAlt = Color(0xE62A3F73)
private val Red = Color(0xFFE53935)
private val Gold = Color(0xFFB9E84F)
private val Blue = Color(0xFF3488DC)
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
                        val mismatch = Mm4SaveCodec.firstDifference(verified, current)
                        check(mismatch == null) {
                            "Validasi round-trip gagal pada $mismatch. File belum ditulis agar save tidak rusak."
                        }
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Penyedia file tidak bisa membuka tujuan export untuk ditulis.")
                        output.use { stream -> stream.write(bytes); stream.flush() }
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
            primary = Gold,
            secondary = Blue,
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
                        onEdit = { root, path, value -> if (value.isJsonPrimitive) editTarget = EditTarget(root, path, value.deepCopy()) },
                        onGodAction = { action ->
                            val latest = document ?: return@CareerWorkspace
                            val changed = applyGodModeAction(latest, action)
                            if (changed != null) { document = changed; hasChanges = true }
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
                    val updated = mutateDocument(current, target.root) { setPath(it, target.path, value) }
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
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("BUKA FILE SAVE (.MSV)")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "CAREER EDITOR • KONTROL TERARAH • LZ4 EXPORT",
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


private val Sections = listOf("Overview", "Drivers", "Staff", "Teams", "Cars", "Finance", "HQ", "World", "Save Info", "Advanced", "God Mode")

@Composable
private fun CareerWorkspace(
    document: Mm4SaveCodec.Document, section: String, onSection: (String) -> Unit,
    selectedDriver: String?, onDriver: (String?) -> Unit,
    onEdit: (String, List<String>, JsonElement) -> Unit,
    onGodAction: (String) -> Unit
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
            "God Mode" -> GodModePage(document, onGodAction)
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
                                Text("${driver.team} • Age ${ageFromDate(details?.get("dateOfBirth")?.asString)} • Driver ID ${driver.data.get("m_DriverID")?.asString ?: "—"}",color=Muted,fontSize=10.sp)
                            }
                            Icon(Icons.Default.ChevronRight,null,tint=Muted)
                        }
                    }
                }
            }
        }
    } else {
        val d=chosen.data
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
                            Text(preview(value),color=Gold,fontWeight=FontWeight.SemiBold,fontSize=11.sp)
                            Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(14.dp))
                        }
                    }
                    val nationality=details?.getAsJsonObject("nationality")?.get("mNationalityID")
                    if(nationality!=null) InfoLine("Nationality", nationalityLabel(nationality))
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
                            Text("Potential ${potential?.asString ?: "—"} / 20",color=Muted,fontSize=9.sp)
                        }
                        Text("${value?.asString ?: "—"} / 20",color=Gold,fontWeight=FontWeight.Bold)
                        IconButton(onClick={if(value!=null) onEdit("Game Data",profilePath+listOf("m_Stats","m_Stats","[$n]","m_Stat"),value)}) { Icon(Icons.Default.Edit,null,tint=Muted) }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(potential!=null) TextButton(onClick={onEdit("Game Data",profilePath+listOf("m_Stats","m_Stats","[$n]","m_Potential"),potential)}) { Text("Edit potential",fontSize=9.sp) }
                        val modifier=stat.get("m_StatModifier")
                        if(modifier!=null) TextButton(onClick={onEdit("Game Data",profilePath+listOf("m_Stats","m_Stats","[$n]","m_StatModifier"),modifier)}) { Text("Modifier ${preview(modifier)}",fontSize=9.sp) }
                    }
                }
            }

        }
    }
}

private fun ageFromDate(date:String?):String {
    if(date.isNullOrBlank()) return "—"
    return try {
        val birth=java.time.LocalDate.parse(date.take(10))
        val today=java.time.LocalDate.now()
        java.time.Period.between(birth,today).years.coerceAtLeast(0).toString()
    } catch(_:Throwable) { "—" }
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
                    val stats=obj.getAsJsonObject("stats") ?: return@mapNotNull null
                    val role=when {
                        stats.has("m_Engineer") -> "Designer"
                        stats.has("m_Mechanic") -> "Mechanic"
                        stats.has("m_Strategist") -> "Strategist"
                        else -> return@mapNotNull null
                    }
                    val person=obj.getAsJsonObject("m_PersonDetails")
                    val personName=person?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    val regen=obj.getAsJsonObject("m_PersonRegenDetails")
                    val displayName=personName.ifBlank {
                        val seed=regen?.get("regenSeed")?.asString ?: id
                        "Prospek " + role + " • " + seed.takeLast(5)
                    }
                    val teamRef=obj.getAsJsonObject("contract")?.getAsJsonObject("m_Team")?.get("\$ref")?.asString
                    val group=teamRef?.let { teamNames[it] } ?: "Free agent / belum ditempatkan"
                    val isApplicant=path.any { it=="m_Applicants" }
                    CatalogRecord(path,obj,displayName,if(isApplicant) role + " • Kandidat" else role,group)
                }.distinctBy { it.data.get("\$id")?.asString }.sortedWith(compareBy({it.group},{it.type},{it.name}))
            }
        }
    }
    val chosen=records.firstOrNull { it.path.joinToString("/") == selected }
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
        val stats=if(category=="staff") chosen.data.getAsJsonObject("stats")?.getAsJsonArray("m_Stats") else chosen.data.getAsJsonArray("m_Stats")
        val labels=when {
            isCar -> listOf("Top Speed","Acceleration","Braking","Low Speed Cornering","High Speed Cornering","Tyre Wear","Fuel Efficiency")
            category=="staff" && chosen.type.contains("Designer",true) -> listOf("Performance","Reliability","Leadership","Bonus","Success Rate")
            category=="staff" && chosen.type.contains("Strategist",true) -> listOf("Race Starts","Overtaking","Defending","Consistency","Focus")
            category=="staff" && chosen.type.contains("Mechanic",true) -> listOf("Setup Skill","Car Knowledge","Pit Stop Skill","Reliability")
            else -> emptyList()
        }
        LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
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
                            Text(preview(statValue),color=Gold,fontWeight=FontWeight.Bold)
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
                            Text(path.dropLast(1).joinToString(" / "),color=Muted,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
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
    return path.any { part -> financialTerms.any { term -> part.contains(term, ignoreCase = true) } }
}

internal fun isStaffEditablePath(path: List<String>): Boolean {
    if (path.isEmpty()) return false
    val root = path.first()
    if (root in setOf("m_PersonDetails", "contract")) return true
    val compensationTerms = listOf("salary", "wage", "pay", "perracecost", "signingfee")
    return path.any { part -> compensationTerms.any { term -> part.contains(term, ignoreCase = true) } }
}

private fun nationalityLabel(value: JsonElement): String { return if (value.isJsonPrimitive) "Kode negara ${value.asString} (pemetaan ID MM4 belum tersedia)" else "Nationality belum dikenali" }

private fun collectObjectsWithId(root:JsonElement):List<Pair<List<String>,JsonObject>> {
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
            Text("DUNIA KARIER & ATURAN",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
            Text("Pengaturan musim, kejuaraan, toko komponen, supplier, riset, cuaca, dan aturan yang benar-benar ditemukan di file save.",color=Muted,fontSize=11.sp)
            Text(groups.size.toString()+" data groups detected",fontSize=20.sp,fontWeight=FontWeight.Black)
        } }
        items(groups) { group ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(worldGroupLabel(group.key),fontWeight=FontWeight.Bold)
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
private fun HqPage(doc:Mm4SaveCodec.Document,onEdit:(String,List<String>,JsonElement)->Unit) {
    val hq=doc.data.getAsJsonObject("headquarters") ?: JsonObject()
    val upgrades=hq.getAsJsonArray("m_Upgrades")
    val completed=hq.getAsJsonArray("m_CompletedUpgrades")
    data class Facility(val listIndex:Int,val gameKey:String,val value:JsonObject)
    val facilities=upgrades?.mapIndexedNotNull { index, element ->
        if (!element.isJsonObject) return@mapIndexedNotNull null
        val row=element.asJsonObject
        val key=row.get("Key")?.asString ?: index.toString()
        val value=row.getAsJsonObject("Value") ?: return@mapIndexedNotNull null
        Facility(index,key,value)
    }.orEmpty()
    val tierRows=hq.getAsJsonArray("m_CurrentTierInformation")
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("HEADQUARTERS",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                Text("${completed?.size() ?: 0} completed upgrades",fontSize=23.sp,fontWeight=FontWeight.Black)
                Text("${facilities.size} facility upgrade records",color=Muted,fontSize=11.sp)
                Text("Status fasilitas, waktu pembukaan, dan jalur upgrade dapat diubah dari kontrol di bawah.",color=Muted,fontSize=10.sp)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("FACILITY LEVELS",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                tierRows?.forEachIndexed { i, row ->
                    if(row.isJsonObject) {
                        val key=row.asJsonObject.get("Key")?.asString ?: i.toString()
                        val value=row.asJsonObject.get("Value")
                        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(facilityGroupLabel(key.toIntOrNull() ?: i),Modifier.weight(1f),color=Muted,fontSize=11.sp)
                            Text(value?.asString ?: "—",color=Gold,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
        }
        items(facilities,key={it.listIndex}) { facility ->
            val state=facility.value.get("State")
            val weeks=facility.value.get("weeksToUnlock")
            val pathType=facility.value.get("pathType")
            val stateNumber=state?.asInt ?: -1
            val status=when(stateNumber) { 0 -> "Locked"; 1 -> "Unlocked"; 2 -> "Upgrade in progress"; else -> "Unknown status" }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${facilityGroupLabel(facility.value.get("pathType")?.asInt ?: 0)} • Upgrade ${facility.gameKey}",fontWeight=FontWeight.Bold,fontSize=14.sp)
                        Text(status,color=when(stateNumber){1->Color(0xFF82D7A2);0->Red;else->Gold},fontSize=11.sp)
                    }
                    TextButton(onClick={onEdit("Game Data",listOf("headquarters","m_Upgrades","[${facility.listIndex}]","Value","State"),state ?: com.google.gson.JsonPrimitive(0))}) { Text("CHANGE STATUS") }
                }
                if(weeks!=null) InfoLine("Weeks until unlock",weeks.asString)
                if(pathType!=null) InfoLine("Upgrade path",pathType.asString)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    if(weeks!=null) OutlinedButton(onClick={onEdit("Game Data",listOf("headquarters","m_Upgrades","[${facility.listIndex}]","Value","weeksToUnlock"),weeks)}) { Text("EDIT TIMER",fontSize=10.sp) }
                    if(pathType!=null) OutlinedButton(onClick={onEdit("Game Data",listOf("headquarters","m_Upgrades","[${facility.listIndex}]","Value","pathType"),pathType)}) { Text("EDIT PATH",fontSize=10.sp) }
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
                InfoLine(label, if (v.isJsonPrimitive && v.asJsonPrimitive.isString) v.asString else v.toString())
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
            value.isJsonPrimitive && (query.isBlank() ||
                humanizeField(path.last()).contains(query,true) ||
                path.joinToString(" ").contains(query,true))
        }
    }
    val groups=fields.groupBy { it.first.firstOrNull() ?: "Other" }.toSortedMap()
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("PENGATURAN LANJUTAN",color=Gold,fontWeight=FontWeight.Bold,fontSize=11.sp)
                Text("Semua data lanjutan dikelompokkan berdasarkan bagian game. Cari dengan istilah sederhana, buka kelompok, lalu pilih nilai yang ingin diubah. Tidak perlu menulis JSON.",color=Muted,fontSize=11.sp)
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

private fun applyGodModeAction(current:Mm4SaveCodec.Document,action:String):Mm4SaveCodec.Document? {
    val data=current.data.deepCopy().asJsonObject
    when(action) {
        "max_balance" -> {
            val finance=data.getAsJsonObject("financeManager") ?: return null
            finance.addProperty("m_Balance",2_000_000_000L)
        }
        "unlock_hq" -> {
            val hq=data.getAsJsonObject("headquarters") ?: return null
            val upgrades=hq.getAsJsonArray("m_Upgrades") ?: return null
            upgrades.forEach { row ->
                val value=row.asJsonObject.getAsJsonObject("Value") ?: return@forEach
                if(value.has("State")) value.addProperty("State",1)
                if(value.has("weeksToUnlock")) value.addProperty("weeksToUnlock",0)
            }
        }
        "max_research" -> {
            val research=data.getAsJsonObject("researchManager") ?: return null
            val cap=research.get("m_ResearchCap")?.asInt ?: 100000
            research.addProperty("m_SeasonResearchPointsMM4",cap)
            research.addProperty("m_IsDebugUnlocked",true)
        }
        "debug_unlock" -> {
            val research=data.getAsJsonObject("researchManager") ?: return null
            research.addProperty("m_IsDebugUnlocked",true)
        }
        "unlock_suppliers" -> {
            val supplier=data.getAsJsonObject("supplierNetworkManager") ?: return null
            val nodes=supplier.getAsJsonArray("m_NodeDictionary") ?: return null
            var changed=0
            fun unlock(node:JsonElement) {
                if(node.isJsonObject) {
                    val obj=node.asJsonObject
                    if(obj.has("m_IsOwned")) { obj.addProperty("m_IsOwned",true); changed++ }
                    if(obj.has("m_HasOwnedBeenRevealed")) obj.addProperty("m_HasOwnedBeenRevealed",true)
                    obj.get("m_Nodes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { unlock(it) }
                } else if(node.isJsonArray) node.asJsonArray.forEach { unlock(it) }
            }
            nodes.forEach { row ->
                val value=row.asJsonObject.get("Value")
                if(value!=null) unlock(value)
            }
            if(changed==0) return null
        }
        else -> return null
    }
    return current.copy(data=data)
}

@Composable
private fun GodModePage(doc:Mm4SaveCodec.Document,onAction:(String)->Unit) {
    val balance=doc.data.getAsJsonObject("financeManager")?.get("m_Balance")?.asLong ?: 0L
    val hq=doc.data.getAsJsonObject("headquarters")?.getAsJsonArray("m_Upgrades")
    val unlocked=hq?.count { it.asJsonObject.getAsJsonObject("Value")?.get("State")?.asInt==1 } ?: 0
    val research=doc.data.getAsJsonObject("researchManager")
    LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("GOD MODE",color=Gold,fontWeight=FontWeight.Black,fontSize=13.sp)
                Text("Kontrol khusus untuk mengubah karier secara ekstrem. Setiap tombol mengubah data save dan bisa dibatalkan dengan membuka file asli lagi.",color=Muted,fontSize=11.sp)
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
                Text("Riset saat ini ${research?.get("m_SeasonResearchPointsMM4")?.asString ?: "—"} / ${research?.get("m_ResearchCap")?.asString ?: "—"}",color=Muted,fontSize=11.sp)
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
                Text("Membuka seluruh node supplier yang tersimpan di jaringan pemasok. Ini membuka supplier, bukan mengubah komponen yang sudah terpasang di mobil.",color=Muted,fontSize=11.sp)
                Button(onClick={onAction("unlock_suppliers")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Blue)) { Text("BUKA SEMUA SUPPLIER") }
            }
        }
        item {
            Text("Pemindahan tim antar divisi dan transfer pembalap belum diaktifkan pada build ini. Keduanya harus menjaga referensi roster, kontrak, klasemen, dan kejuaraan secara serentak. Tidak akan dibuat tombol yang berisiko merusak save.",color=Muted,fontSize=10.sp)
        }
    }
}
@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.animateContentSize(animationSpec = tween(240)).clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .background(Brush.linearGradient(listOf(Color(0xC936568A), Color(0xB91B2D58), Color(0xA51A2445))))
            .padding(1.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(listOf(Color(0xD82B416B), Color(0xC51B2A50), Color(0xB9152344))))
            .padding(14.dp),
        content = content
    )
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
    var choicesOpen by remember(target) { mutableStateOf(false) }
    val currentNumber = if (isNumber) runCatching { primitive.asDouble }.getOrDefault(0.0) else 0.0
    val isAttribute = target.path.any { it == "m_Stats" || it == "m_Stat" || it == "m_Potential" }
    val attributeValue = currentNumber.toInt().coerceIn(0, 20)
    val numberStep = when { kotlin.math.abs(currentNumber) >= 1000000 -> 100000.0; kotlin.math.abs(currentNumber) >= 10000 -> 1000.0; kotlin.math.abs(currentNumber) >= 100 -> 10.0; else -> 1.0 }
    val meaning = when { attributeValue <= 4 -> "Sangat rendah"; attributeValue <= 8 -> "Rendah"; attributeValue <= 12 -> "Menengah"; attributeValue <= 16 -> "Tinggi"; else -> "Elite" }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(humanizeField(target.path.lastOrNull() ?: "Edit value")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.path.joinToString("  /  "), color = Muted, fontSize = 10.sp)
                when {
                    isBoolean -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nonaktif", Modifier.weight(1f))
                        Switch(checked = value.toBoolean(), onCheckedChange = { value = it.toString() })
                        Text("Aktif", Modifier.padding(start = 8.dp))
                    }
                    isNumber && isAttribute -> {
                        OutlinedButton(onClick = { choicesOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(attributeValue.toString() + " / 20 • " + meaning)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = choicesOpen, onDismissRequest = { choicesOpen = false }) {
                            (0..20).forEach { choice ->
                                val label = when { choice <= 4 -> "Sangat rendah"; choice <= 8 -> "Rendah"; choice <= 12 -> "Menengah"; choice <= 16 -> "Tinggi"; else -> "Elite" }
                                DropdownMenuItem(text = { Text(choice.toString() + " / 20 • " + label) }, onClick = { value = choice.toString(); choicesOpen = false })
                            }
                        }
                        Text("Skala atribut 0–20. Keterangan ini panduan umum, bukan label resmi game.", color = Muted, fontSize = 10.sp)
                    }
                    isNumber -> {
                        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { value = (value.toDouble() - numberStep).toLong().toString() }) { Text("−") }
                            Text("Langkah " + numberStep.toLong(), color = Muted, fontSize = 10.sp)
                            OutlinedButton(onClick = { value = (value.toDouble() + numberStep).toLong().toString() }) { Text("+") }
                        }
                        Text("Gunakan tombol untuk memilih nilai, tanpa mengetik angka manual.", color = Muted, fontSize = 10.sp)
                    }
                    else -> OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Text") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = when { isBoolean -> com.google.gson.JsonPrimitive(value.toBoolean()); isNumber -> com.google.gson.JsonPrimitive(value.toDouble()); else -> com.google.gson.JsonPrimitive(value) }
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
