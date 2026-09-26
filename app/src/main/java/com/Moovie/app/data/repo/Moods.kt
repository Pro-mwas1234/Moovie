package com.Moovie.app.data.repo

/**
 * Mood chips for the Discover screen, mapped to TMDB genre IDs (OR with |)
 * and keyword IDs (OR with |).
 */
data class Mood(
    val id: String,
    val label: String,
    val emoji: String,
    val genreIds: List<Int>,
    val keywordIds: List<Int> = emptyList(),
)

object Moods {
    // TMDB genre ids: 28 Action, 35 Comedy, 18 Drama, 27 Horror, 10749 Romance,
    // 878 Sci-Fi, 16 Animation, 53 Thriller, 99 Documentary, 12 Adventure,
    // 14 Fantasy, 80 Crime, 10402 Music, 37 Western
    val ALL = listOf(
        Mood("fun", "Fun", "\uD83D\uDE04", listOf(35, 12, 16), listOf(9748)),          // comedy/adv/anim + feel-good
        Mood("scary", "Scary", "\uD83D\uDE28", listOf(27), listOf(1299)),              // horror + monster
        Mood("sad", "Cry me a river", "\uD83D\uDE22", listOf(18), listOf(1741)),       // drama + tear-jerker
        Mood("mindbend", "Mind-bend", "\uD83E\uDD2F", listOf(878, 53), listOf(4565)),  // sci-fi/thriller + time travel
        Mood("romance", "Romance", "\u2764\uFE0F", listOf(10749), listOf(9748)),       // romance + feel-good
        Mood("hype", "Hype", "\uD83D\uDD25", listOf(28, 53), listOf(10596)),           // action/thriller + adrenaline
        Mood("chill", "Chill", "\uD83E\uDDCA", listOf(99, 10402), listOf(159551)),     // doc/music
        Mood("family", "Family night", "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66", listOf(16, 10751), listOf(157311)), // animation/family
    )
}
