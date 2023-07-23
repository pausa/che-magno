package dev.pausa.chemagno

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.job

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AddRecipeDialog(
    open: MutableState<Boolean>,
    doneAction: (String) -> Unit
) {

    if (open.value) {
        var recipeName by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }

        Dialog(
            onDismissRequest = { open.value = false },
        ) {
            TextField(
                value = recipeName,
                modifier = Modifier
                    .focusRequester(focusRequester),
                onValueChange = { recipeName = it },
                singleLine = true,
                label = { Text("RecipeName") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if(recipeName.isNotBlank()) doneAction.invoke(recipeName.trim())
                        open.value = false
                    },
                ),
            )
            LaunchedEffect(Unit){
                // TODO there must be a way to do without delay
                delay(100)
                coroutineContext.job.invokeOnCompletion {
                    focusRequester.requestFocus()
                }
            }
        }


    }

}