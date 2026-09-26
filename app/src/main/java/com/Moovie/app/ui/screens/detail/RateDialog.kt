package com.Moovie.app.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.ReviewPost
import com.Moovie.app.ui.components.StarRating
import kotlinx.coroutines.launch

@Composable
fun RateDialog(
    titleName: String,
    mediaType: String,
    tmdbId: Int,
    posterPath: String?,
    initialRating: Int?,
    onDismiss: () -> Unit,
) {
    var rating by remember { mutableStateOf(initialRating ?: 0) }
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate \"$titleName\"") },
        text = {
            Column {
                StarRating(rating = rating, onChange = { rating = it }, size = 36)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Write a review (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = rating > 0,
                onClick = {
                    val uid = ServiceLocator.auth.uid ?: return@TextButton
                    scope.launch {
                        ServiceLocator.social.postReview(
                            ReviewPost(
                                key = "$mediaType-$tmdbId",
                                mediaType = mediaType,
                                tmdbId = tmdbId,
                                titleName = titleName,
                                posterPath = posterPath,
                                uid = uid,
                                authorName = ServiceLocator.auth.name ?: "You",
                                rating = rating,
                                text = text,
                            )
                        )
                        onDismiss()
                    }
                },
            ) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
