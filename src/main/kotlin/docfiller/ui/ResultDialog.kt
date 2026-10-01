package docfiller.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import docfiller.model.GenerationResult
import java.awt.Desktop

@Composable
fun ResultDialog(state: AppState, result: GenerationResult) {
    val eventWarnings = result.warnings.filter { it.isEventField }.map { it.fieldName }.distinct()
    val personWarnings = result.warnings.filter { !it.isEventField }
        .groupBy({ it.personName }, { it.message })

    AlertDialog(
        onDismissRequest = { state.result = null },
        title = { Text("Генерация завершена") },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                item {
                    Text(
                        "Создано документов: ${result.documents.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                result.documents.forEach { doc ->
                    item {
                        Text(
                            "№${doc.contractNumber} — ${doc.personName}: ${doc.file.name}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                if (result.warnings.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Предупреждения (документы созданы; незаполненные поля заменены на __________ — их можно вписать от руки или найти по подчёркиваниям)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (eventWarnings.isNotEmpty()) {
                        item {
                            Text(
                                "Поля события: ${eventWarnings.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                    personWarnings.forEach { (person, messages) ->
                        item {
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    person,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                messages.forEach { message ->
                                    Text(
                                        "  • $message",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { Desktop.getDesktop().open(result.documents.first().file.parentFile) }
                state.result = null
            }) { Text("Открыть папку") }
        },
        dismissButton = {
            TextButton(onClick = { state.result = null }) { Text("Закрыть") }
        },
    )
}
