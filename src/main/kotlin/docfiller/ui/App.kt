package docfiller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { state.init() }
    LaunchedEffect(state.error, state.importMessage) {
        state.error?.let {
            snackbar.showSnackbar(it)
            state.error = null
        }
        state.importMessage?.let {
            snackbar.showSnackbar(it)
            state.importMessage = null
        }
    }

    MaterialTheme {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(0.45f)) {
                        PeoplePanel(state)
                    }
                    VerticalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Column(modifier = Modifier.weight(0.55f)) {
                        GeneratePanel(state)
                    }
                }
            }
        }
    }

    if (state.showTagsDialog) {
        TagsDialog(state)
    }
    if (state.showImportDialog) {
        ImportDialog(state)
    }
    if (state.showEachFieldsDialog) {
        EachFieldsDialog(state)
    }
    state.result?.let {
        ResultDialog(state, it)
    }
}
