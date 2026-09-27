package com.Moovie.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.Moovie.app.data.model.CastMemberUi
import com.Moovie.app.data.model.TmdbReview
import com.Moovie.app.data.model.VideoUi
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.components.SectionHeader

object TrailerPlayer {
    fun open(context: android.content.Context, key: String?) {
        key ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(TmdbClient.youtubeWatchUrl(key)))
        )
    }
}

@Composable
fun CastRow(cast: List<CastMemberUi>) {
    if (cast.isEmpty()) return
    Column {
        SectionHeader("Cast")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cast, key = { it.id }) { member ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(84.dp),
                ) {
                    AsyncImage(
                        model = TmdbClient.profileUrl(member.profilePath),
                        contentDescription = member.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        member.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        member.character ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun VideoRow(videos: List<VideoUi>) {
    if (videos.isEmpty()) return
    val context = LocalContext.current
    Column {
        SectionHeader("Videos")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(videos, key = { it.key }) { video ->
                Column(modifier = Modifier.width(200.dp).clickable {
                    TrailerPlayer.open(context, video.key)
                }) {
                    AsyncImage(
                        model = TmdbClient.youtubeThumbnail(video.key),
                        contentDescription = video.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Text(
                        video.name ?: "Trailer",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewsList(reviews: List<TmdbReview>) {
    if (reviews.isEmpty()) return
    Column {
        SectionHeader("Reviews")
        reviews.take(3).forEach { review ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        review.author ?: "Anonymous",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    review.authorDetails?.rating?.let { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Star,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                " %.1f".format(r),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Text(
                    review.content ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
