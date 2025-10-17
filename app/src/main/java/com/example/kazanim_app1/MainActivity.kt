package com.example.kazanim_app1

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kazanim_app1.ui.theme.ExcelViewerTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExcelViewerTheme {
                JsonViewerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JsonViewerApp() {
    // A mutable list of sub-menus
    val subMenus = remember { mutableStateListOf<SubMenu>() }
    // Which sub-menu (if any) is currently selected
    var selectedSubMenu by remember { mutableStateOf<SubMenu?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load saved sub-menus on startup.
    LaunchedEffect(Unit) {
        loadSubMenus(context)?.let { loadedSubMenus ->
            subMenus.addAll(loadedSubMenus)
        }
    }

    // File picker launcher for adding new sub-menu from JSON
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isLoading = true
            scope.launch {
                val fileName = getFileNameFromUri(context, uri)
                val dersName = extractDersNameFromFileName(fileName)
                processJsonFile(context, uri) { loadedSections ->
                    // Check if JSON has only one section
                    if (loadedSections.size == 1) {
                        // Don't create sub-menu, add section directly to a new sub-menu
                        subMenus.add(SubMenu(dersName, loadedSections))
                    } else {
                        // Create sub-menu with multiple sections
                        subMenus.add(SubMenu(dersName, loadedSections))
                    }
                    isLoading = false
                    saveSubMenus(context, subMenus)
                }
            }
        }
    }


    // Handle system back: prevent app from closing on the root screen
    if (selectedSubMenu == null) {
        BackHandler {
            // Consume back to avoid closing the app when at root
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kazanımlar", color = Color.Black) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.LightGray),
                actions = {
                    if (selectedSubMenu == null) {
                        // Main screen: show "Ders Ekle" which uploads JSON
                        TextButton(onClick = { filePickerLauncher.launch("application/json") }) {
                            Text("Ders Ekle", color = Color.Black)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                if (selectedSubMenu == null) {
                    // Main screen: list of sub-menus
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        items(subMenus) { submenu ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ElevatedButton(
                                    onClick = { selectedSubMenu = submenu },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(submenu.name, fontSize = 18.sp)
                                }
                                TextButton(
                                    onClick = {
                                        subMenus.remove(submenu)
                                        scope.launch { saveSubMenus(context, subMenus) }
                                    }
                                ) {
                                    Text("Sil", color = Color.Red)
                                }
                            }
                        }
                    }
                } else {
                    // Sub-menu detail screen
                    SubMenuDetailScreen(
                        subMenu = selectedSubMenu!!,
                        onBack = { selectedSubMenu = null },
                        onDeleteSection = { section ->
                            val index = subMenus.indexOf(selectedSubMenu)
                            selectedSubMenu?.let { sm ->
                                sm.sections = sm.sections.filterNot { it.name == section.name }
                                if (index != -1) {
                                    // Trigger recomposition by updating the item in the state list
                                    subMenus[index] = sm
                                }
                                // Persist changes
                                scope.launch { saveSubMenus(context, subMenus) }
                            }
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubMenuDetailScreen(
    subMenu: SubMenu,
    onBack: () -> Unit,
    onDeleteSection: (Section) -> Unit
) {
    // Local state to control which section is selected.
    // If there's only one section, auto-select it
    var selectedSection by remember { 
        mutableStateOf<Section?>(if (subMenu.sections.size == 1) subMenu.sections.first() else null) 
    }

    // Handle back: go up one level instead of exiting
    BackHandler {
        if (selectedSection != null) {
            // If only one section exists, go back to main menu directly
            if (subMenu.sections.size == 1) {
                onBack()
            } else {
                selectedSection = null
            }
        } else {
            onBack()
        }
    }

    if (selectedSection == null) {
        // Show list of section buttons.
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack) {
                    Text("Ana Menü")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(subMenu.sections) { section ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ElevatedButton(
                            onClick = { selectedSection = section },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(section.name, fontSize = 18.sp)
                        }
                        TextButton(onClick = { onDeleteSection(section) }) {
                            Text("Sil", color = Color.Red)
                        }
                    }
                }
            }
        }
    } else {
        // If a section is selected, show its entries.
        SectionDetailScreen(section = selectedSection!!) {
            selectedSection = null // Go back to the section list.
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionDetailScreen(section: Section, onBack: () -> Unit) {
    // Use isCurrentDateInRange to determine the initial page for the entries
    val initialPage = section.entries.indexOfFirst {
        isCurrentDateInRange(it.head1.orEmpty())
    }.takeIf { it != -1 } ?: 0

    val pagerState = rememberPagerState(initialPage = initialPage) { section.entries.size }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("Geri")
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val currentEntry = section.entries[page]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = formatText(currentEntry.head1.orEmpty()),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    Text(
                        text = formatText(currentEntry.head2.orEmpty()),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    Text(
                        text = formatText(currentEntry.text.orEmpty()),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun isCurrentDateInRange(range: String): Boolean {
    val processedRange = range.substringAfter(':').trim()
    
    val months = mapOf(
        "January" to Calendar.JANUARY,
        "February" to Calendar.FEBRUARY,
        "March" to Calendar.MARCH,
        "April" to Calendar.APRIL,
        "May" to Calendar.MAY,
        "June" to Calendar.JUNE,
        "July" to Calendar.JULY,
        "August" to Calendar.AUGUST,
        "September" to Calendar.SEPTEMBER,
        "October" to Calendar.OCTOBER,
        "November" to Calendar.NOVEMBER,
        "December" to Calendar.DECEMBER
    )
    
    // Try pattern for "29-03 September-October" or "29-03September-October"
    val pattern1 = """(\d{1,2})\s*-\s*(\d{1,2})\s*([A-Za-z]+)\s*-\s*([A-Za-z]+)""".toRegex()
    val match1 = pattern1.find(processedRange)
    
    val startDay: Int
    val endDay: Int
    val startMonth: Int
    val endMonth: Int
    
    if (match1 != null) {
        // Format: "29-03 September-October" (day 29 of first month to day 03 of second month)
        val groups = match1.groupValues
        startDay = groups[1].toIntOrNull() ?: return false
        endDay = groups[2].toIntOrNull() ?: return false
        val firstMonthName = groups[3]
        val secondMonthName = groups[4]
        startMonth = months[firstMonthName] ?: return false
        endMonth = months[secondMonthName] ?: return false
    } else {
        // Try original pattern for "29 September - 03 October"
        val pattern2 = """(\d{1,2})(?:\s*([A-Za-z]+))?\s*-\s*(\d{1,2})\s*([A-Za-z]+)""".toRegex()
        val match2 = pattern2.find(processedRange) ?: return false
        val groups = match2.groupValues
        startDay = groups[1].toIntOrNull() ?: return false
        endDay = groups[3].toIntOrNull() ?: return false
        val startMonthStr = groups[2]
        val endMonthStr = groups[4]
        
        startMonth = if (startMonthStr.isNotBlank()) {
            months[startMonthStr]
        } else {
            months[endMonthStr]
        } ?: return false
        
        endMonth = months[endMonthStr] ?: return false
    }

    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val startCalendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, now.get(Calendar.YEAR))
        set(Calendar.MONTH, startMonth)
        set(Calendar.DAY_OF_MONTH, startDay)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val endCalendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, now.get(Calendar.YEAR))
        set(Calendar.MONTH, endMonth)
        set(Calendar.DAY_OF_MONTH, endDay)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (endCalendar.before(startCalendar)) {
        endCalendar.add(Calendar.YEAR, 1)
    }
    return now.timeInMillis in startCalendar.timeInMillis..endCalendar.timeInMillis
}

fun formatText(text: String): String {
    val keywords = setOf("Listening", "Spoken Interaction", "Reading", "Spoken Production", "Writing", "Speaking")
    return text.lines().joinToString("\n") { line ->
        val trimmedLine = line.replace("\\s+".toRegex(), " ").trim()
        if (keywords.any { trimmedLine.startsWith(it) }) {
            "\n**$trimmedLine**\n"
        } else {
            trimmedLine
        }
    }.trim()
}

suspend fun processJsonFile(
    context: Context,
    uri: Uri,
    onComplete: suspend (List<Section>) -> Unit
) {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val gson = Gson()
            val type = object : TypeToken<Map<String, List<Entry>>>() {}.type
            val data = gson.fromJson<Map<String, List<Entry>>>(inputStream.reader(), type)
            val sections = data.map { Section(it.key, it.value) }
            onComplete(sections)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun loadSubMenus(context: Context): List<SubMenu>? {
    val file = File(context.filesDir, "submenus.json")
    if (!file.exists()) return null
    return withContext(Dispatchers.IO) {
        try {
            FileReader(file).use {
                val type = object : TypeToken<List<SubMenu>>() {}.type
                Gson().fromJson<List<SubMenu>>(it, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

suspend fun saveSubMenus(context: Context, subMenus: List<SubMenu>) {
    val file = File(context.filesDir, "submenus.json")
    withContext(Dispatchers.IO) {
        try {
            FileWriter(file).use {
                Gson().toJson(subMenus, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var fileName = "unknown"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            fileName = cursor.getString(nameIndex)
        }
    }
    return fileName
}

fun extractDersNameFromFileName(fileName: String): String {
    // Remove file extension
    val nameWithoutExt = fileName.substringBeforeLast('.')
    
    // Normalize Turkish characters for matching
    val normalized = nameWithoutExt.lowercase()
    
    // Check for subject keywords (case-insensitive, Turkish character variants)
    val subjects = mapOf(
        "ingilizce" to "İngilizce",
        "inglizce" to "İngilizce",
        "gorgu" to "Görgü",
        "görgu" to "Görgü",
        "gorgu" to "Görgü",
        "görgü" to "Görgü",
        "rehberlik" to "Rehberlik",
        "muzik" to "Müzik",
        "müzik" to "Müzik",
        "muzık" to "Müzik",
        "müzık" to "Müzik"
    )
    
    var dersName = ""
    for ((key, value) in subjects) {
        if (normalized.contains(key)) {
            dersName = value
            break
        }
    }
    
    // Check if "secmeli" or variants exist
    val hasSecmeli = normalized.contains("secmeli") || 
                     normalized.contains("seçmeli") || 
                     normalized.contains("secmelı") || 
                     normalized.contains("seçmelı")
    
    if (hasSecmeli && dersName.isNotEmpty()) {
        dersName = "Seçmeli $dersName"
    }
    
    // If no match found, use the original filename
    if (dersName.isEmpty()) {
        dersName = nameWithoutExt
    }
    
    return dersName
}
