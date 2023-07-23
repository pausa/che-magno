package dev.pausa.chemagno

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.pausa.chemagno.ui.theme.CheMagnoTheme
import kotlinx.coroutines.job
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipes =  mutableStateListOf<Recipe>()
        loadRecipeFolder(recipes)
        setContent {
            MainContent(recipes = recipes) {
                loadSavedFolder()
                    ?.createRecipeFile(recipeName = it, contentResolver)
                    ?.let { recipe -> recipes += recipe }
            }
        }
    }

    // TODO this stuff should probably go  to a different file
    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadRecipeFolder(recipes: SnapshotStateList<Recipe>) {
        recipes.clear()
        try {
            loadSavedFolder()?.loadRecipes(recipes)
                ?: throw SecurityException("No folder saved, thus not permissions available")
        } catch (e: SecurityException) {
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.apply { persistPermissions() }
                    ?.loadRecipes(recipes)
            }.launch(loadSavedFolder())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun Uri.loadRecipes(recipes: SnapshotStateList<Recipe>) =
        this.listFiles().asSequence()
            .mapNotNull { it.toRecipe() }
            .sortedRecipes()
            .forEach { recipes += it }

    private fun Uri.toRecipe(): Recipe? =
        contentResolver.openInputStream(this)
            ?.use { stream ->
                val id = DocumentsContract.getDocumentId(this)
                var title: String? = null
                // TODO support notes
                var notes = null

                BufferedReader(InputStreamReader(stream)).use {
                    it.forEachLine { line ->
                        when {
                            line.startsWith("#+title: ", ignoreCase = true) -> {
                                title = line.removePrefix("#+title:").trim()
                            }
                        }
                    }
                }

                title?.let { Recipe(id = id, title = it) }
            }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun Uri.listFiles(): List<Uri> {
        val fileDocumentIds = emptyList<Pair<String, String>>().toMutableList()
        val treeId = DocumentsContract.getTreeDocumentId(this)
        val childId = DocumentsContract.buildChildDocumentsUriUsingTree(this, treeId)
        contentResolver.query(childId, null, null, null)
            ?.use { cursor ->
                val displayNameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                while (!cursor.isAfterLast) {
                    // TODO maybe there is a constant
                    fileDocumentIds += cursor.getString(0) to cursor.getString(displayNameColumn)
                    cursor.moveToNext()
                }
            }

        return fileDocumentIds
            .filter { it.second.endsWith(".org") }
            .map { DocumentsContract.buildDocumentUriUsingTree(this, it.first) }
    }

    private fun Uri.persistPermissions() {
        val takeFlags: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Check for the freshest data.
        contentResolver.takePersistableUriPermission(this, takeFlags)

        // TODO constant for folder
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString("recipeFolder", this.toString())
            .apply()
    }

    private fun loadSavedFolder() = getPreferences(Context.MODE_PRIVATE)
        .getString("recipeFolder", null)
        ?.let { Uri.parse(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    darkTheme: Boolean = isSystemInDarkTheme(),
    recipes: SnapshotStateList<Recipe>,
    addRecipeAction: (String) -> Unit,
) =
    CheMagnoTheme(useDarkTheme = darkTheme) {
        var resetList by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Che magno?") },
                    //colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = scheme.primaryContainer),
                    // TODO refactor out
                    actions = {
                        IconButton(onClick = {
                            recipes.sortRecipes().also { resetList = true }
                        }) {
                            Icon(imageVector = Icons.Default.List, contentDescription = null)
                        }
                    }
                )
            },
            floatingActionButton = {
                ShuffleRecipesActionButton {
                    recipes.shuffle()
                    resetList = true
                }
            }
        )
        {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
            ) {
                val openAddRecipeDialog = remember { mutableStateOf(false) }
                val listState = remember { LazyListState() }
                RecipeColumn(recipes, state = listState, openAddRecipeDialog)
                AddRecipeDialog(open = openAddRecipeDialog) { name ->
                    addRecipeAction.invoke(name)
                    recipes.sortRecipes()
                    resetList = true
                }
                LaunchedEffect(resetList) {
                    if (resetList) {
                        listState.animateScrollToItem(0)
                        coroutineContext.job.invokeOnCompletion {
                            resetList = false
                        }
                    }
                }
            }

        }
    }


@Composable
fun RecipeColumn(
    list: MutableList<Recipe>,
    state: LazyListState,
    openAddRecipeDialog: MutableState<Boolean>
) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(list, key = { it.id }) {
            RecipeCard(title = it.title)
        }
        item {
            TextButton(
                onClick = { openAddRecipeDialog.value = true },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text(text = "Add Recipe")
            }
        }
    }

}


@Composable
fun RecipeCard(title: String) =
    Card(modifier = Modifier.fillMaxSize()) {
        Text(
            text = title,
            fontSize = 5.em,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }

@Composable
fun ShuffleRecipesActionButton(action: () -> Unit) {
    FloatingActionButton(
        onClick = { action.invoke() },
        modifier = Modifier.defaultMinSize()
    ) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
    }
}

fun Sequence<Recipe>.sortedRecipes() = sortedBy { it.title.lowercase() }
fun MutableList<Recipe>.sortRecipes() = sortBy { it.title.lowercase() }
