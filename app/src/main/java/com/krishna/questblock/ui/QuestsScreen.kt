@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.baseReward
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuestsViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    fun add(title: String, difficulty: Int) = viewModelScope.launch { repo.addTask(title, difficulty) }
    fun complete(task: TaskEntity) = viewModelScope.launch { repo.completeTask(task) }
    fun delete(task: TaskEntity)   = viewModelScope.launch { repo.deleteTask(task) }
}

private val DIFF_NAMES  = listOf("Easy", "Medium", "Hard")
private val DIFF_COLORS = listOf(ColorPrimary, ColorCoin, ColorDanger)

@Composable
fun QuestsScreen(vm: QuestsViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val pending = tasks.filter { !it.done }
    val done    = tasks.filter { it.done }

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = ColorBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSheet = true },
                containerColor = ColorPrimary,
                contentColor   = ColorOnPrimary,
                shape          = CircleShape,
            ) { Icon(Icons.Default.Add, contentDescription = "Add quest") }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Quests", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Spacer(Modifier.height(4.dp))
            }

            if (pending.isNotEmpty()) {
                item { Text("Active", style = MaterialTheme.typography.labelLarge, color = ColorMuted) }
                items(pending, key = { it.id }) { task ->
                    QuestCard(task, onComplete = { vm.complete(task) }, onDelete = { vm.delete(task) })
                }
            }
            if (done.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Completed", style = MaterialTheme.typography.labelLarge, color = ColorMuted)
                }
                items(done, key = { it.id }) { task ->
                    QuestCard(task, onComplete = {}, onDelete = { vm.delete(task) })
                }
            }
            if (tasks.isEmpty()) {
                item {
                    Spacer(Modifier.height(40.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🗺️", style = MaterialTheme.typography.displayMedium)
                        Text("No quests yet", style = MaterialTheme.typography.titleMedium, color = ColorText)
                        Text("Tap + to add your first quest.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = ColorSurface,
        ) {
            AddQuestSheet(onAdd = { title, diff ->
                vm.add(title, diff)
                scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
            })
        }
    }
}

@Composable
private fun QuestCard(task: TaskEntity, onComplete: () -> Unit, onDelete: () -> Unit) {
    val color = DIFF_COLORS.getOrElse(task.difficulty - 1) { ColorPrimary }
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.alpha(if (task.done) 0.45f else 1f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(22.dp)
                    .background(if (task.done) color.copy(alpha = 0.3f) else ColorSurfaceHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!task.done) {
                    IconButton(onClick = onComplete, modifier = Modifier.size(22.dp)) {
                        Box(Modifier.size(12.dp).background(color.copy(alpha = 0.4f), CircleShape))
                    }
                } else {
                    Text("✓", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium, color = ColorText,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(DIFF_NAMES.getOrElse(task.difficulty - 1) { "Easy" },
                            style = MaterialTheme.typography.labelSmall, color = color)
                    }
                    Text("+${baseReward(task.difficulty)} 🪙", style = MaterialTheme.typography.labelSmall, color = ColorCoin)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ColorMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddQuestSheet(onAdd: (String, Int) -> Unit) {
    var title      by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableIntStateOf(1) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("New Quest", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = ColorText)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("What do you need to do?", color = ColorMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ColorPrimary,
                unfocusedBorderColor = ColorSurfaceHigh,
                cursorColor          = ColorPrimary,
                focusedTextColor     = ColorText,
                unfocusedTextColor   = ColorText,
            ),
        )

        // Difficulty chips — full width row so Hard chip never gets cut off
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..3).forEach { d ->
                val color = DIFF_COLORS[d - 1]
                FilterChip(
                    selected = difficulty == d,
                    onClick  = { difficulty = d },
                    label    = { Text("${DIFF_NAMES[d - 1]} +${baseReward(d)}🪙") },
                    modifier = Modifier.weight(1f),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.2f),
                        selectedLabelColor     = color,
                        containerColor         = ColorSurfaceHigh,
                        labelColor             = ColorMuted,
                    ),
                )
            }
        }

        Button(
            onClick  = { if (title.isNotBlank()) onAdd(title.trim(), difficulty) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            shape    = RoundedCornerShape(12.dp),
        ) { Text("Add Quest", fontWeight = FontWeight.Bold) }
    }
}
