package docfiller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Диалог ввода each-полей (например, суммы) для каждого выбранного человека.
 * Поля предзаполнены из Excel, если там есть одноимённые колонки.
 */
@Composable
fun EachFieldsDialog(state: AppState) {
    val fields = state.selectedTemplate?.eachFields ?: emptyList()
    val people = state.selectedPeople

    AlertDialog(
        onDismissRequest = { state.showEachFieldsDialog = false },
        title = { Text("Параметры для каждого человека") },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp).fillMaxWidth()) {
                items(people, key = { it.id }) { person ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            person.displayName,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(0.45f),
                        )
                        fields.forEach { field ->
                            OutlinedTextField(
                                value = state.eachValues["${person.id}|$field"] ?: "",
                                onValueChange = { state.eachValues["${person.id}|$field"] = it },
                                label = { Text(field.replace('_', ' ')) },
                                singleLine = true,
                                modifier = Modifier.weight(0.55f / fields.size),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { state.confirmEachFields() }) { Text("Сгенерировать") }
        },
        dismissButton = {
            TextButton(onClick = { state.showEachFieldsDialog = false }) { Text("Отмена") }
        },
    )
}
