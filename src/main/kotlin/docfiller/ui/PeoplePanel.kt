package docfiller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun PeoplePanel(state: AppState) {
    Text("Люди", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    OutlinedTextField(
        value = state.search,
        onValueChange = { state.search = it },
        label = { Text("Поиск по ФИО") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            var expanded by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { expanded = true }) {
                Text(state.tagFilter?.let { "Тег: $it" } ?: "Все теги")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Все") }, onClick = {
                    state.tagFilter = null
                    expanded = false
                })
                state.allTags.forEach { tag ->
                    DropdownMenuItem(text = { Text(tag) }, onClick = {
                        state.tagFilter = tag
                        expanded = false
                    })
                }
            }
        }
        TextButton(onClick = { state.selectAllVisible() }) { Text("Выбрать всех") }
        TextButton(onClick = { state.deselectAll() }) { Text("Снять") }
        TextButton(onClick = { state.showTagsDialog = true }) { Text("Теги…") }
        TextButton(onClick = { state.showImportDialog = true }) { Text("Импорт…") }
        TextButton(onClick = { state.reloadPeople() }) { Text("Обновить") }
    }

    Text(
        "Выбрано: ${state.selectedPeople.size} из ${state.people.size}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp),
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.visiblePeople, key = { it.id }) { person ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.togglePerson(person.id, person.id !in state.selectedIds) }
                    .padding(vertical = 2.dp),
            ) {
                Checkbox(
                    checked = person.id in state.selectedIds,
                    onCheckedChange = { state.togglePerson(person.id, it) },
                )
                Column {
                    Text(person.displayName)
                    if (person.tags.isNotEmpty()) {
                        Text(
                            person.tags.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
