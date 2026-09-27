package com.Moovie.app.data.repo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EscalatorWarning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Mood chips for the Discover screen, mapped to TMDB genre IDs (OR with |)
 * and keyword IDs (OR with |).
 */
data class Mood(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val genreIds: List<Int>,
    val keywordIds: List<Int> = emptyList(),
)

object Moods {
    // TMDB genre ids: 28 Action, 35 Comedy, 18 Drama, 27 Horror, 10749 Romance,
    // 878 Sci-Fi, 16 Animation, 53 Thriller, 99 Documentary, 12 Adventure,
    // 14 Fantasy, 80 Crime, 10402 Music, 37 Western
    val ALL = listOf(
        Mood("fun", "Fun", Icons.Filled.SentimentSatisfiedAlt, listOf(35, 12, 16), listOf(9748)),          // comedy/adv/anim + feel-good
        Mood("scary", "Scary", Icons.Filled.SentimentVeryDissatisfied, listOf(27), listOf(1299)),          // horror + monster
        Mood("sad", "Cry me a river", Icons.Filled.SentimentDissatisfied, listOf(18), listOf(1741)),       // drama + tear-jerker
        Mood("mindbend", "Mind-bend", Icons.Filled.Psychology, listOf(878, 53), listOf(4565)),             // sci-fi/thriller + time travel
        Mood("romance", "Romance", Icons.Filled.Favorite, listOf(10749), listOf(9748)),                    // romance + feel-good
        Mood("hype", "Hype", Icons.Filled.LocalFireDepartment, listOf(28, 53), listOf(10596)),             // action/thriller + adrenaline
        Mood("chill", "Chill", Icons.Filled.SelfImprovement, listOf(99, 10402), listOf(159551)),           // doc/music
        Mood("family", "Family night", Icons.Filled.EscalatorWarning, listOf(16, 10751), listOf(157311)),  // animation/family
    )
}
