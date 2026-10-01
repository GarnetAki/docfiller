package docfiller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import javax.swing.JFileChooser

@Composable
fun GeneratePanel(state: AppState) {
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
        Text("Генерация договоров", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }) {
                    Text(state.selectedTemplate?.file?.name ?: "Выбрать шаблон…")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (state.templates.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("В папке templates нет .docx") },
                            onClick = { expanded = false },
                        )
                    }
                    state.templates.forEach { file ->
                        DropdownMenuItem(text = { Text(file.name) }, onClick = {
                            state.selectTemplate(file)
                            expanded = false
                        })
                    }
                }
            }
            TextButton(onClick = { state.reloadTemplates() }) { Text("Обновить") }
        }

        val template = state.selectedTemplate
        if (template != null) {
            val missing = state.missingColumns()
            if (missing.isNotEmpty()) {
                Text(
                    "В people.xlsx нет колонок: ${missing.joinToString()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (template.eventFields.isNotEmpty()) {
                Text(
                    "Данные события",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                template.eventFields.forEach { field ->
                    OutlinedTextField(
                        value = state.eventValues[field] ?: "",
                        onValueChange = { state.eventValues[field] = it },
                        label = { Text(field.replace('_', ' ').replaceFirstChar(Char::uppercase)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            if (template.usesContractNumber) {
                OutlinedTextField(
                    value = state.startNumber,
                    onValueChange = { state.startNumber = it.filter(Char::isDigit) },
                    label = { Text("Начальный номер договора") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.outputDir,
                onValueChange = {},
                readOnly = true,
                label = { Text("Папка для сохранения") },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { chooseDirectory()?.let { state.outputDir = it } }) {
                Text("Обзор…")
            }
        }

        Button(
            onClick = { state.generate() },
            enabled = state.canGenerate(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            if (state.generating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Сгенерировать (${state.selectedPeople.size})")
            }
        }
    }
}

private fun chooseDirectory(): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Куда сохранить договоры"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}
