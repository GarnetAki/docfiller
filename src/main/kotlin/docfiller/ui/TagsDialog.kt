package docfiller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Управление тегами: слева — список тегов (создание/удаление),
 * справа — люди с галочками принадлежности к выбранному тегу.
 * Предварительно выбирать людей в главном окне не нужно.
 */
@Composable
fun TagsDialog(state: AppState) {
    var newTag by remember { mutableStateOf("") }
    var activeTag by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { state.showTagsDialog = false },
        title = { Text("Теги") },
        text = {
            Column(modifier = Modifier.height(460.dp).fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("Новый тег") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newTag.isNotBlank(),
                        onClick = {
                            state.createTag(newTag)
                            activeTag = newTag.trim()
                            newTag = ""
                        },
                    ) { Text("Создать") }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.weight(1f)) {
                    // Список тегов
                    LazyColumn(modifier = Modifier.weight(0.4f).padding(end = 8.dp)) {
                        items(state.allTags) { tag ->
                            val count = state.people.count { tag in it.tags }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeTag = tag }
                                    .padding(vertical = 2.dp),
                            ) {
                                Text(
                                    "$tag ($count)",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (tag == activeTag) FontWeight.Bold else FontWeight.Normal,
                                    color = if (tag == activeTag) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                TextButton(onClick = {
                                    state.deleteTag(tag)
                                    if (activeTag == tag) activeTag = null
                                }) {
                                    Text("✕", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (state.allTags.isEmpty()) {
                            item { Text("Тегов пока нет", style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    // Люди для выбранного тега
                    Column(modifier = Modifier.weight(0.6f)) {
                        val tag = activeTag
                        if (tag == null) {
                            Text(
                                "Выберите тег слева, чтобы назначить его людям",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Тег «$tag»",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { state.setTagForAll(tag, true) }) { Text("Всем") }
                                TextButton(onClick = { state.setTagForAll(tag, false) }) { Text("Снять") }
                            }
                            LazyColumn {
                                items(state.people, key = { it.id }) { person ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                state.setTagForPerson(person.id, tag, tag !in person.tags)
                                            },
                                    ) {
                                        Checkbox(
                                            checked = tag in person.tags,
                                            onCheckedChange = { state.setTagForPerson(person.id, tag, it) },
                                        )
                                        Text(person.displayName, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.showTagsDialog = false }) { Text("Закрыть") }
        },
    )
}
