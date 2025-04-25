package com.example.kazanim_app1

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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

    // File picker launcher (only used in the submenu detail screen)
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isLoading = true
            scope.launch {
                processJsonFile(context, uri) { loadedSections ->
                    selectedSubMenu?.sections = loadedSections
                    isLoading = false
                    // Save the updated sub-menus list with the new sections.
                    saveSubMenus(context, subMenus)
                }
            }
        }
    }

    // Dialog state for adding a new sub-menu
    var showAddDialog by remember { mutableStateOf(false) }
    if (showAddDialog) {
        AddSubMenuDialog(
            onAdd = { name ->
                // Add the new sub-menu.
                subMenus.add(SubMenu(name))
                showAddDialog = false
                // Save the updated list.
                scope.launch {
                    saveSubMenus(context, subMenus)
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kazanımlar", color = Color.Black) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.LightGray),
                actions = {
                    if (selectedSubMenu == null) {
                        // Main screen: show "Ders Ekle"
                        TextButton(onClick = { showAddDialog = true }) {
                            Text("Ders Ekle", color = Color.Black)
                        }
                    } else {
                        // In submenu detail: show "JSON Yükle"
                        TextButton(onClick = { filePickerLauncher.launch("application/json") }) {
                            Text("JSON Yükle", color = Color.Black)
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
                            ElevatedButton(
                                onClick = { selectedSubMenu = submenu },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(submenu.name, fontSize = 18.sp)
                            }
                        }
                    }
                } else {
                    // Sub-menu detail screen
                    SubMenuDetailScreen(subMenu = selectedSubMenu!!) {
                        selectedSubMenu = null // Go back to main screen
                    }
                }
            }
        }
    }
}

@Composable
fun AddSubMenuDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Ders Ekle") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Ders adı") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) { onAdd(text) }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubMenuDetailScreen(subMenu: SubMenu, onBack: () -> Unit) {
    // Local state to control which section is selected.
    var selectedSection by remember { mutableStateOf<Section?>(null) }

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
                    ElevatedButton(
                        onClick = { selectedSection = section },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(section.name, fontSize = 18.sp)
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

    // Optionally, re-calculate the target page if the section changes
    LaunchedEffect(section, pagerState.currentPage) {
        val targetPage = section.entries.indexOfFirst {
            isCurrentDateInRange(it.head1.orEmpty())
        }.takeIf { it != -1 } ?: 0
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

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
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun isCurrentDateInRange(range: String): Boolean {
    val processedRange = range.substringAfter(':').trim()
    val pattern = """(\d{1,2})(?:\s*([A-Za-z]+))?\s*-\s*(\d{1,2})\s*([A-Za-z]+)""".toRegex()
    val match = pattern.find(processedRange) ?: return false
    val (startDayStr, startMonthStr, endDayStr, endMonthStr) = match.destructured
    val startDay = startDayStr.toIntOrNull() ?: return false
    val endDay = endDayStr.toIntOrNull() ?: return false

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
    val startMonth = if (startMonthStr.isNotBlank()) {
        months[startMonthStr]
    } else {
        months[endMonthStr]
    } ?: return false

    val endMonth = months[endMonthStr] ?: return false

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
