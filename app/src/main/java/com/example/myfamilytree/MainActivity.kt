package com.example.myfamilytree

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import kotlin.math.roundToInt

// --- 1. Data Model ---
class MemberNode(
    val id: Long,
    initialName: String,
    initialX: Float,
    initialY: Float,
    initialParentId: Long? = null,
    initialSpouseId: Long? = null,
    initialRelation: String = "สมาชิก",
    initialGender: String = "Male",
    initialBirthDate: String = "",
    initialIsDeceased: Boolean = false,
    initialImageUri: String? = null
) {
    var name by mutableStateOf(initialName)
    var posX by mutableFloatStateOf(initialX)
    var posY by mutableFloatStateOf(initialY)
    var parentId by mutableStateOf(initialParentId)
    var spouseId by mutableStateOf(initialSpouseId)
    var relation by mutableStateOf(initialRelation)
    var gender by mutableStateOf(initialGender)
    var birthDate by mutableStateOf(initialBirthDate)
    var isDeceased by mutableStateOf(initialIsDeceased)
    var imageUri by mutableStateOf(initialImageUri)
}

// --- 2. Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFEBEBEB)) {
                    FamilyTreeCleanApp()
                }
            }
        }
    }
}

// --- 3. Helper Functions ---
suspend fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "profile_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            inputStream?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file.absolutePath
        } catch (_: Exception) { // FIX: Rename 'e' to '_'
            null
        }
    }
}

fun calculateAge(birthDateStr: String, isDeceased: Boolean): String {
    if (birthDateStr.isBlank()) return ""
    val regex = Regex("\\d{4}")
    val match = regex.find(birthDateStr) ?: return ""
    return try {
        val birthYear = match.value.toInt()
        val currentYearAD = Calendar.getInstance().get(Calendar.YEAR)
        val currentYearTH = currentYearAD + 543
        val age = if (birthYear > 2400) currentYearTH - birthYear else currentYearAD - birthYear
        if (isDeceased) "สิริอายุประมาณ $age ปี" else "อายุ $age ปี"
    } catch (_: Exception) { // FIX: Rename 'e' to '_'
        ""
    }
}

// --- 4. Main Composable ---
@Composable
fun FamilyTreeCleanApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("family_clean_v1", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    val density = LocalDensity.current

    BoxWithConstraints {
        // ใช้ constraints local variable เพื่อหลีกเลี่ยงความสับสนของ Scope
        val constraints = this
        val screenWidthPx = with(density) { constraints.maxWidth.toPx() }
        val screenHeightPx = with(density) { constraints.maxHeight.toPx() }

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        val savedData = prefs.getString("data", null)
        val members = remember {
            mutableStateListOf<MemberNode>().apply {
                if (savedData != null) {
                    try {
                        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                        val rawList: List<Map<String, Any>> = gson.fromJson(savedData, listType)
                        rawList.forEach {
                            add(MemberNode(
                                id = (it["id"] as Double).toLong(),
                                initialName = it["name"] as String,
                                initialX = (it["posX"] as Double).toFloat(),
                                initialY = (it["posY"] as Double).toFloat(),
                                initialParentId = (it["parentId"] as? Double)?.toLong(),
                                initialSpouseId = (it["spouseId"] as? Double)?.toLong(),
                                initialRelation = it["relation"] as? String ?: "สมาชิก",
                                initialGender = it["gender"] as? String ?: "Male",
                                initialBirthDate = it["birthDate"] as? String ?: "",
                                initialIsDeceased = it["isDeceased"] as? Boolean ?: false,
                                initialImageUri = it["imageUri"] as? String
                            ))
                        }
                    } catch (_: Exception) { // FIX: Rename 'e' to '_'
                        add(MemberNode(1, "เริ่มต้น", 300f, 300f))
                    }
                } else { add(MemberNode(1, "เริ่มต้น", 300f, 300f)) }
            }
        }

        var editingMember by remember { mutableStateOf<MemberNode?>(null) }
        var showSearchDialog by remember { mutableStateOf(false) }

        val saveAll = {
            val rawData = members.map {
                mapOf(
                    "id" to it.id, "name" to it.name, "posX" to it.posX, "posY" to it.posY,
                    "parentId" to it.parentId, "spouseId" to it.spouseId,
                    "relation" to it.relation, "gender" to it.gender,
                    "birthDate" to it.birthDate, "isDeceased" to it.isDeceased,
                    "imageUri" to it.imageUri
                )
            }
            prefs.edit().putString("data", gson.toJson(rawData)).apply()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // --- Canvas Area ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(Color(0xFFF5F5F5))
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.1f, 3f)
                            val zoomFactor = newScale / oldScale
                            offset = (offset - centroid) * zoomFactor + centroid + pan
                            scale = newScale
                        }
                    }
            ) {
                // Wrapper Box for GraphicsLayer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                ) {
                    // 1. Grid Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSize = 100.dp.toPx()
                        val gridColor = Color(0xFFE0E0E0)
                        val viewW = size.width / scale
                        val viewH = size.height / scale
                        val sX = -offset.x / scale
                        val sY = -offset.y / scale
                        var x = (sX / gridSize).toInt() * gridSize
                        while (x < sX + viewW + gridSize) {
                            drawLine(gridColor, Offset(x, sY - gridSize), Offset(x, sY + viewH + gridSize), 2f)
                            x += gridSize
                        }
                        var y = (sY / gridSize).toInt() * gridSize
                        while (y < sY + viewH + gridSize) {
                            drawLine(gridColor, Offset(sX - gridSize, y), Offset(sX + viewW + gridSize, y), 2f)
                            y += gridSize
                        }
                    }

                    // 2. Lines Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        members.forEach { member ->
                            val cw = 180.dp.toPx()
                            val ch = 110.dp.toPx()
                            member.spouseId?.let { sId ->
                                val spouse = members.find { it.id == sId }
                                if (spouse != null && member.id < spouse.id) {
                                    val s = Offset(member.posX + cw / 2, member.posY + ch / 2)
                                    val e = Offset(spouse.posX + cw / 2, spouse.posY + ch / 2)
                                    drawLine(
                                        Color(0xFFE91E63),
                                        s,
                                        e,
                                        5f,
                                        StrokeCap.Round,
                                        PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                                    )
                                    drawCircle(Color(0xFFF8BBD0), 8f, Offset((s.x + e.x) / 2, (s.y + e.y) / 2))
                                }
                            }
                            member.parentId?.let { pId ->
                                val parent = members.find { it.id == pId }
                                if (parent != null) {
                                    val spouse = parent.spouseId?.let { sid -> members.find { it.id == sid } }
                                    val s = if (spouse != null) Offset(
                                        (parent.posX + spouse.posX) / 2 + cw / 2,
                                        (parent.posY + spouse.posY) / 2 + ch / 2
                                    )
                                    else Offset(parent.posX + cw / 2, parent.posY + ch)
                                    val e = Offset(member.posX + cw / 2, member.posY)
                                    val p = Path().apply {
                                        moveTo(s.x, s.y)
                                        if (spouse != null) cubicTo(
                                            s.x,
                                            s.y + (e.y - s.y) * 0.5f,
                                            e.x,
                                            s.y + (e.y - s.y) * 0.5f,
                                            e.x,
                                            e.y
                                        )
                                        else cubicTo(
                                            s.x,
                                            s.y + (e.y - s.y) / 2,
                                            e.x,
                                            s.y + (e.y - s.y) / 2,
                                            e.x,
                                            e.y
                                        )
                                    }
                                    drawPath(p, Color(0xFF90A4AE), style = Stroke(6f, cap = StrokeCap.Round))
                                    if (spouse != null) drawCircle(Color(0xFF90A4AE), 6f, s)
                                }
                            }
                        }
                    }

                    // 3. Cards
                    members.forEach { member ->
                        key(member.id) {
                            MemberCardClean(member, scale, { saveAll() }, { editingMember = member })
                        }
                    }
                }
            }

            // Buttons
            IconButton(
                onClick = { scale = 1f; offset = Offset.Zero },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
            ) {
                Icon(Icons.Default.CenterFocusStrong, "Reset")
            }

            IconButton(
                onClick = { showSearchDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
            ) {
                Icon(Icons.Default.Search, "ค้นหา", tint = Color(0xFF1976D2))
            }

            LargeFloatingActionButton(
                onClick = {
                    val newId = System.currentTimeMillis()
                    members.add(
                        MemberNode(
                            newId,
                            "สมาชิกใหม่",
                            (-offset.x + 300f) / scale,
                            (-offset.y + 500f) / scale
                        )
                    )
                    saveAll()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp),
                containerColor = Color(0xFF2E7D32), contentColor = Color.White
            ) { Icon(Icons.Default.PersonAdd, "Add") }

            // Dialogs
            editingMember?.let { member ->
                EditCleanDialog(member, members, { editingMember = null }, {
                    members.forEach {
                        if (it.parentId == member.id) it.parentId = null; if (it.spouseId == member.id) it.spouseId =
                        null
                    }
                    members.remove(member); saveAll(); editingMember = null
                }, { saveAll(); editingMember = null })
            }

            if (showSearchDialog) {
                SearchCleanDialog(
                    members = members,
                    onDismiss = { showSearchDialog = false },
                    onSelectMember = { member ->
                        scale = 1f
                        val cardW = with(density) { 180.dp.toPx() }
                        val cardH = with(density) { 110.dp.toPx() }

                        offset = Offset(
                            (screenWidthPx / 2) - (member.posX + cardW / 2),
                            (screenHeightPx / 2) - (member.posY + cardH / 2)
                        )
                        showSearchDialog = false
                    }
                )
            }
        }
    }
}

// --- 5. Component Functions ---
@Composable
fun SearchCleanDialog(
    members: List<MemberNode>,
    onDismiss: () -> Unit,
    onSelectMember: (MemberNode) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredMembers =
        members.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.name }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "ค้นหาสมาชิก",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("พิมพ์ชื่อ...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredMembers) { member ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    member.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (member.isDeceased) Color.Gray else Color.Black
                                )
                            },
                            supportingContent = { Text(member.relation) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (member.imageUri != null) AsyncImage(
                                        model = member.imageUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    else Icon(
                                        if (member.gender == "Female") Icons.Default.Face3 else Icons.Default.Face,
                                        null,
                                        tint = Color.White
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onSelectMember(member) }
                        )
                        // FIX: Use HorizontalDivider instead of Divider
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    }
                    if (filteredMembers.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ไม่พบรายชื่อ", color = Color.Gray)
                            }
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) { Text("ปิด") }
            }
        }
    }
}

@Composable
fun MemberCardClean(
    member: MemberNode,
    scale: Float,
    onDragEnd: () -> Unit,
    onClick: () -> Unit
) {
    val cardColor =
        if (member.isDeceased) Color(0xFFEEEEEE) else if (member.gender == "Female") Color(0xFFFCE4EC) else Color(
            0xFFE3F2FD
        )
    val borderColor =
        if (member.isDeceased) Color.Gray else if (member.gender == "Female") Color(0xFFF48FB1) else Color(
            0xFF90CAF9
        )
    val nameColor = if (member.isDeceased) Color.DarkGray else Color.Black
    val ageText = calculateAge(member.birthDate, member.isDeceased)

    Card(
        modifier = Modifier
            .offset { IntOffset(member.posX.roundToInt(), member.posY.roundToInt()) }
            .size(180.dp, 110.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onDragEnd() }) { change, dragAmount ->
                    change.consume(); member.posX += dragAmount.x / scale; member.posY += dragAmount.y / scale
                }
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(6.dp),
        border = BorderStroke(if (member.isDeceased) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(55.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.LightGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (member.imageUri != null) AsyncImage(
                    model = member.imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else Icon(
                    if (member.gender == "Female") Icons.Default.Face3 else Icons.Default.Face,
                    null,
                    tint = if (member.isDeceased) Color.Gray else Color.LightGray,
                    modifier = Modifier.size(35.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    member.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    color = nameColor
                )
                Text(member.relation, fontSize = 11.sp, color = Color.Gray)
                if (ageText.isNotEmpty()) Text(
                    ageText,
                    fontSize = 10.sp,
                    color = if (member.isDeceased) Color.Gray else Color(0xFF1976D2),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCleanDialog(
    member: MemberNode,
    allMembers: List<MemberNode>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showParentSelector by remember { mutableStateOf(false) }
    var showSpouseSelector by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                scope.launch {
                    member.imageUri = saveImageToInternalStorage(context, it)
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("แก้ไขข้อมูล") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable { launcher.launch("image/*") }
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (member.imageUri != null) AsyncImage(
                        model = member.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    else Icon(Icons.Default.AddAPhoto, null, tint = Color.White)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        "สถานะเสียชีวิต",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = member.isDeceased,
                        onCheckedChange = { member.isDeceased = it })
                }
                OutlinedTextField(
                    value = member.name,
                    onValueChange = { member.name = it },
                    label = { Text("ชื่อ") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = member.relation,
                    onValueChange = { member.relation = it },
                    label = { Text("ความสัมพันธ์") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = member.birthDate,
                    onValueChange = { member.birthDate = it },
                    label = { Text("ปีเกิด (เช่น 2530)") },
                    placeholder = { Text("ใส่ปีเพื่อคำนวณอายุ") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { member.gender = "Male" }) {
                        RadioButton(
                            selected = member.gender == "Male",
                            onClick = { member.gender = "Male" }); Text("ชาย")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { member.gender = "Female" }) {
                        RadioButton(
                            selected = member.gender == "Female",
                            onClick = { member.gender = "Female" }); Text("หญิง")
                    }
                }
                // FIX: Use HorizontalDivider instead of Divider
                HorizontalDivider()
                val parentName = allMembers.find { it.id == member.parentId }?.name ?: "ไม่ระบุ"
                OutlinedButton(
                    onClick = { showParentSelector = !showParentSelector; showSpouseSelector = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountTree, null); Spacer(Modifier.width(8.dp)); Text("ลูกของ: $parentName")
                }
                if (showParentSelector) {
                    Card(
                        modifier = Modifier.height(120.dp),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        LazyColumn {
                            item {
                                TextButton(onClick = {
                                    member.parentId = null; showParentSelector = false
                                }) { Text("❌ ลบเส้นพ่อแม่") }
                            }
                            items(allMembers.filter { it.id != member.id }) { p ->
                                TextButton(onClick = {
                                    member.parentId = p.id; showParentSelector = false
                                }) { Text(p.name) }
                            }
                        }
                    }
                }
                val spouseName = allMembers.find { it.id == member.spouseId }?.name ?: "ไม่ระบุ"
                Button(
                    onClick = { showSpouseSelector = !showSpouseSelector; showParentSelector = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFCE4EC),
                        contentColor = Color(0xFF880E4F)
                    )
                ) {
                    Icon(Icons.Default.Favorite, null); Spacer(Modifier.width(8.dp)); Text("คู่สมรส: $spouseName")
                }
                if (showSpouseSelector) {
                    Card(
                        modifier = Modifier.height(120.dp),
                        border = BorderStroke(1.dp, Color(0xFFF48FB1))
                    ) {
                        LazyColumn {
                            item {
                                TextButton(onClick = {
                                    allMembers.find { it.id == member.spouseId }?.spouseId = null; member.spouseId =
                                    null; showSpouseSelector = false
                                }) { Text("❌ ลบเส้นคู่สมรส") }
                            }
                            items(allMembers.filter { it.id != member.id }) { s ->
                                TextButton(onClick = {
                                    allMembers.find { it.spouseId == s.id }?.spouseId =
                                        null; allMembers.find { it.id == member.spouseId }?.spouseId =
                                    null; member.spouseId = s.id; s.spouseId =
                                    member.id; showSpouseSelector = false
                                }) { Text(s.name) }
                            }
                        }
                    }
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEBEE),
                        contentColor = Color.Red
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Delete, null); Text("ลบสมาชิก") }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("บันทึก") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } }
    )
}