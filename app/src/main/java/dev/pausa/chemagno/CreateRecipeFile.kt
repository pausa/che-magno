package dev.pausa.chemagno

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

fun Uri.createRecipeFile(recipeName: String, contentResolver: ContentResolver): Recipe? {
    Log.i("RECIPE", "Saving '$recipeName' to $this")
    return recipeName.createFileName()
        .also { Log.i("RECIPE", "New file name: $it") }
        .let {
            val treeId = DocumentsContract.buildDocumentUriUsingTree(
                this,
                DocumentsContract.getTreeDocumentId(this)
            )
            DocumentsContract.createDocument(contentResolver, treeId, "application/*", it)
        }
        ?.let {
            it.createOrgFile(recipeName, contentResolver)
            Recipe(id = DocumentsContract.getDocumentId(it), title = recipeName)
        }
        ?: null.also { Log.e("RECIPE", "Could not create document uri") }


}

private fun String.createFileName() =
    lowercase()
        // TODO more special characters
        .replace(" ", "-")
        .plus(".org")

private fun Uri.createOrgFile(recipeName: String, contentResolver: ContentResolver) {
    contentResolver
        .openOutputStream(this)?.use {
            it.bufferedWriter().apply {
                append("#+title: $recipeName")
                newLine()
                append("#+filetags: :recipe:")
                newLine()
                append("* Notes")
                newLine()
                flush()
            }
        }
        ?: Log.e("RECIPE", "Could not obtain output stream for $this")
}