package docfiller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import docfiller.excel.ImportParser
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.JFileChooser

/**
 * Импорт людей из таблицы ответов формы: вставка из буфера обмена
 * (Google Sheets / Яндекс Формы → таблица) или файл .xlsx/.csv.
 */
@Composable
fun ImportDialog(state: AppState) {
    var pastedText by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ImportParser.ParsedTable?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var upsertByFio by remember { mutableStateOf(true) }

    fun setParsed(table: ImportParser.ParsedTable) {
        parsed = if (table.rows.isEmpty()) null else table
        parseError = if (table.rows.isEmpty()) "Не удалось разобрать данные — проверьте формат" else null
    }

    AlertDialog(
        onDismissRequest = { state.showImportDialog = false },
        title = { Text("Импорт людей") },
        text = {
            Column(modifier = Modifier.height(480.dp).fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .getData(DataFlavor.stringFlavor) as? String
                        }.getOrNull()?.let { pastedText = it; setParsed(ImportParser.parseDelimited(it)) }
                            ?: run { parseError = "В буфере обмена нет текста" }
                    }) { Text("Вставить из буфера") }
                    OutlinedButton(onClick = {
                        chooseImportFile()?.let { file ->
                            runCatching {
                                if (file.extension.equals("xlsx", true)) ImportParser.parseXlsx(file)
                                else ImportParser.parseDelimited(file.readText())
                            }.onSuccess(::setParsed)
                                .onFailure { parseError = "Не удалось прочитать файл: ${it.message}" }
                        }
                    }) { Text("Файл (.xlsx/.csv)…") }
                }

                OutlinedTextField(
                    value = pastedText,
                    onValueChange = {
                        pastedText = it
                        setParsed(ImportParser.parseDelimited(it))
                    },
                    label = { Text("Или вставьте скопированные строки сюда (первая строка — заголовки)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(top = 8.dp),
                )

                parseError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                parsed?.let { table ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Строк: ${table.rows.size}, колонок: ${table.headers.count { it.isNotBlank() }}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                        items(table.headers.size) { i ->
                            val header = table.headers[i]
                            if (header.isNotBlank()) {
                                val exists = state.columns.any { it.equals(header, ignoreCase = true) }
                                Text(
                                    "• $header → " + if (exists) "существующая колонка" else "новая колонка",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (exists) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Checkbox(checked = upsertByFio, onCheckedChange = { upsertByFio = it })
                        Text(
                            "Обновлять существующих по ФИО (иначе все строки — новые люди)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null,
                onClick = { parsed?.let { state.importRows(it, upsertByFio) } },
            ) { Text("Импортировать") }
        },
        dismissButton = {
            TextButton(onClick = { state.showImportDialog = false }) { Text("Отмена") }
        },
    )
}

private fun chooseImportFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Файл с ответами формы"
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "Таблицы (*.xlsx, *.csv, *.tsv)", "xlsx", "csv", "tsv"
        )
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
