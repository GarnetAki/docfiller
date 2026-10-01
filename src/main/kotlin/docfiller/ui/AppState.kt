package docfiller.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import docfiller.excel.ImportParser
import docfiller.excel.PeopleRepository
import docfiller.model.GenerationResult
import docfiller.model.Person
import docfiller.model.TemplateInfo
import docfiller.template.ContractGenerator
import docfiller.template.TemplateScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppState(private val scope: CoroutineScope) {

    private val repo = PeopleRepository(File("data/people.xlsx"))
    private val templatesDir = File("templates")
    private val tagsRegistryFile = File("data/tags.txt")

    var people by mutableStateOf<List<Person>>(emptyList())
        private set
    var columns by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
    var search by mutableStateOf("")
    var tagFilter by mutableStateOf<String?>(null)

    var templates by mutableStateOf<List<File>>(emptyList())
        private set
    var selectedTemplate by mutableStateOf<TemplateInfo?>(null)
        private set
    val eventValues = mutableStateMapOf<String, String>()
    var startNumber by mutableStateOf("")
    var outputDir by mutableStateOf("")

    var generating by mutableStateOf(false)
        private set
    var result by mutableStateOf<GenerationResult?>(null)
    var error by mutableStateOf<String?>(null)
    var showTagsDialog by mutableStateOf(false)
    var showEachFieldsDialog by mutableStateOf(false)
    var showImportDialog by mutableStateOf(false)
    var importMessage by mutableStateOf<String?>(null)

    /** Введённые при генерации значения each-полей: «id человека|поле» → значение. */
    val eachValues = mutableStateMapOf<String, String>()

    var tagRegistry by mutableStateOf<Set<String>>(emptySet())
        private set

    val allTags: List<String>
        get() = (tagRegistry + people.flatMap { it.tags }).sorted()

    val visiblePeople: List<Person>
        get() {
            val tag = tagFilter
            val query = search
            return people.filter { person ->
                (tag == null || tag in person.tags) &&
                    (query.isBlank() || person.displayName.contains(query, ignoreCase = true))
            }
        }

    val selectedPeople: List<Person>
        get() = people.filter { it.id in selectedIds }

    fun init() {
        loadTagRegistry()
        reloadPeople()
        reloadTemplates()
    }

    private fun loadTagRegistry() {
        tagRegistry = runCatching {
            if (tagsRegistryFile.exists()) {
                tagsRegistryFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }
        }.getOrDefault(emptySet())
    }

    private fun saveTagRegistry() {
        runCatching {
            tagsRegistryFile.parentFile?.mkdirs()
            tagsRegistryFile.writeText(tagRegistry.sorted().joinToString("\n"))
        }.onFailure { error = "Не удалось сохранить список тегов: ${it.message}" }
    }

    fun reloadPeople() {
        try {
            val data = repo.load()
            people = data.people
            columns = data.columns
            selectedIds = selectedIds.intersect(data.people.map { it.id }.toSet())
        } catch (e: Exception) {
            error = "Не удалось прочитать people.xlsx: ${e.message}"
        }
    }

    fun reloadTemplates() {
        templatesDir.mkdirs()
        templates = templatesDir.listFiles { f ->
            f.extension.equals("docx", ignoreCase = true) && !f.name.startsWith("~$")
        }?.sortedBy { it.name } ?: emptyList()
    }

    fun selectTemplate(file: File) {
        try {
            val info = TemplateInfo(file, TemplateScanner.scan(file))
            selectedTemplate = info
            eventValues.clear()
            info.eventFields.forEach { eventValues[it] = "" }
        } catch (e: Exception) {
            error = "Не удалось прочитать шаблон ${file.name}: ${e.message}"
        }
    }

    fun togglePerson(id: String, checked: Boolean) {
        selectedIds = if (checked) selectedIds + id else selectedIds - id
    }

    fun selectAllVisible() {
        selectedIds = selectedIds + visiblePeople.map { it.id }
    }

    fun deselectAll() {
        selectedIds = emptySet()
    }

    /** Создать тег (без привязки к выбранным людям). */
    fun createTag(tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty() || clean in tagRegistry) return
        tagRegistry = tagRegistry + clean
        saveTagRegistry()
    }

    fun setTagForPerson(personId: String, tag: String, on: Boolean) {
        updateTags(setOf(personId)) { if (on) it + tag else it - tag }
    }

    fun setTagForAll(tag: String, on: Boolean) {
        updateTags(people.map { it.id }.toSet()) { if (on) it + tag else it - tag }
    }

    fun addTagToSelected(tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        createTag(clean)
        updateTags(selectedIds) { it + clean }
    }

    fun removeTagFromSelected(tag: String) {
        updateTags(selectedIds) { it - tag }
    }

    fun deleteTag(tag: String) {
        tagRegistry = tagRegistry - tag
        saveTagRegistry()
        updateTags(people.map { it.id }.toSet()) { it - tag }
        if (tagFilter == tag) tagFilter = null
    }

    private fun updateTags(ids: Set<String>, transform: (Set<String>) -> Set<String>) {
        if (ids.isEmpty()) return
        people = people.map { if (it.id in ids) it.copy(tags = transform(it.tags)) else it }
        try {
            repo.saveTags(people)
        } catch (e: Exception) {
            error = e.message
            reloadPeople()
        }
    }

    fun missingColumns(): List<String> {
        val template = selectedTemplate ?: return emptyList()
        return template.personFields.filter { field ->
            columns.none { it.equals(field, ignoreCase = true) }
        }
    }

    fun canGenerate(): Boolean {
        val template = selectedTemplate ?: return false
        if (selectedPeople.isEmpty() || outputDir.isBlank()) return false
        if (template.usesContractNumber && startNumber.toIntOrNull() == null) return false
        return !generating
    }

    fun importRows(parsed: ImportParser.ParsedTable, upsertByFio: Boolean) {
        try {
            val maps = parsed.toMaps()
            if (maps.isEmpty()) {
                error = "Не найдено строк для импорта"
                return
            }
            val result = repo.importRows(maps, upsertByFio)
            reloadPeople()
            showImportDialog = false
            importMessage = "Импорт: добавлено ${result.added}, обновлено ${result.updated}" +
                if (result.newColumns.isNotEmpty()) ", новые колонки: ${result.newColumns.joinToString()}" else ""
        } catch (e: Exception) {
            error = "Ошибка импорта: ${e.message}"
        }
    }

    fun generate() {
        val template = selectedTemplate ?: return
        if (template.eachFields.isNotEmpty()) {
            for (person in selectedPeople) {
                for (field in template.eachFields) {
                    eachValues.putIfAbsent("${person.id}|$field", person.value(field) ?: "")
                }
            }
            showEachFieldsDialog = true
        } else {
            doGenerate(emptyMap())
        }
    }

    fun confirmEachFields() {
        showEachFieldsDialog = false
        val perPerson = mutableMapOf<String, MutableMap<String, String>>()
        eachValues.forEach { (key, value) ->
            val separator = key.indexOf('|')
            if (separator < 0) return@forEach
            val personId = key.substring(0, separator)
            val field = key.substring(separator + 1)
            perPerson.getOrPut(personId) { mutableMapOf() }[field] = value
        }
        doGenerate(perPerson)
    }

    private fun doGenerate(perPersonValues: Map<String, Map<String, String>>) {
        val template = selectedTemplate ?: return
        val people = selectedPeople
        val events = eventValues.toMap()
        val number = startNumber.toIntOrNull() ?: 0
        val dir = File(outputDir)
        generating = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    ContractGenerator.generate(template.file, people, events, number, dir, perPersonValues)
                }
            }
            generating = false
            outcome.onSuccess { result = it }
                .onFailure { error = "Ошибка генерации: ${it.message}" }
        }
    }
}
