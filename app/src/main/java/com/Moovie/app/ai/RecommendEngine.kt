package com.Moovie.app.ai

import com.google.gson.Gson
import com.Moovie.app.BuildConfig
import com.Moovie.app.data.model.Title
import com.Moovie.app.data.repo.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * "I want something like Inception but funnier" — chat-style recommendations.
 *
 * If an OpenAI key is configured, an LLM extracts mood/genre/vibe keywords and
 * suggests candidate titles; each candidate is resolved via TMDB search so
 * posters and metadata are real. Without a key, a built-in heuristic engine
 * parses the prompt for known vibes and maps them to TMDB discover queries.
 */
class RecommendEngine(private val tmdb: MovieRepository) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Suggestion(val title: Title?, val rawName: String, val matched: Boolean)

    data class Reply(val intro: String, val suggestions: List<Suggestion>)

    // ---------- Heuristic engine ----------

    data class Vibe(val id: String, val words: List<String>, val genreIds: List<Int>, val keywordIds: List<Int>)

    private val vibes = listOf(
        Vibe("funny", listOf("funny", "funnier", "comedy", "laugh", "hilarious", "humor"), listOf(35), emptyList()),
        Vibe("scary", listOf("scary", "horror", "creepy", "frightening", "spooky"), listOf(27), emptyList()),
        Vibe("mindbend", listOf("mind-bend", "mindbend", "confusing", "twist", "trippy", "inception"), listOf(878, 53), listOf(4565)),
        Vibe("romantic", listOf("romantic", "romance", "love story", "date night"), listOf(10749), emptyList()),
        Vibe("feelgood", listOf("feel-good", "feel good", "heartwarming", "cozy", "uplifting"), listOf(10751, 16), listOf(9748)),
        Vibe("action", listOf("action", "explosions", "adrenaline", "fight"), listOf(28), emptyList()),
        Vibe("sad", listOf("sad", "cry", "tearjerker", "emotional", "heartbreaking"), listOf(18), listOf(1741)),
        Vibe("space", listOf("space", "sci-fi", "alien", "interstellar", "dune"), listOf(878), emptyList()),
        Vibe("crime", listOf("crime", "heist", "mob", "gangster", "mafia"), listOf(80), emptyList()),
        Vibe("animated", listOf("animated", "animation", "cartoon", "pixar", "anime"), listOf(16), emptyList()),
        Vibe("truestory", listOf("true story", "based on", "biopic", "historical"), listOf(36), emptyList()),
        Vibe("musical", listOf("musical", "music", "sing"), listOf(10402), emptyList()),
    )

    data class ParsedPrompt(val vibes: List<Vibe>, val cleanText: String, val seedTitles: List<String>)

    /** Words likely to be movie titles: capitalized words not at sentence start. */
    private fun extractSeedTitles(text: String): List<String> {
        val stop = setOf(
            "something", "like", "but", "funnier", "scary", "with", "want", "movie",
            "movies", "show", "shows", "similar", "kind", "more", "that", "this",
        )
        val words = text.replace(Regex("[^\\w\\s-]"), " ").split(Regex("\\s+")).filter { it.length > 2 }
        val seeds = mutableListOf<String>()
        var i = 1
        while (i < words.size) {
            if (words[i][0].isUpperCase() && words[i].lowercase() !in stop) {
                seeds.add(words[i])
            }
            i++
        }
        return seeds.distinct().take(3)
    }

    fun parse(prompt: String): ParsedPrompt {
        val lower = prompt.lowercase()
        val matched = vibes.filter { v -> v.words.any { lower.contains(it) } }
        return ParsedPrompt(matched, lower, extractSeedTitles(prompt.trim()))
    }

    suspend fun recommend(prompt: String): Reply {
        return withContext(Dispatchers.IO) {
            if (BuildConfig.OPENAI_API_KEY.isNotBlank()) {
                runCatching { llmRecommend(prompt) }.getOrElse { heuristicRecommend(prompt) }
            } else {
                heuristicRecommend(prompt)
            }
        }
    }

    private suspend fun heuristicRecommend(prompt: String): Reply {
        val parsed = parse(prompt)

        // 1) If we found seed titles, use TMDB recommendations for the first one.
        val seedResults = mutableListOf<Title>()
        for (seed in parsed.seedTitles) {
            val found = runCatching { tmdb.searchMoviesOnly(seed) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) {
                seedResults.addAll(runCatching { tmdb.becauseYouLiked(found.first()) }.getOrDefault(emptyList()))
                break
            }
        }

        // 2) Vibe-based discover queries, mixed in.
        val vibePicks = mutableListOf<Title>()
        val vibes = parsed.vibes.ifEmpty { listOf(this.vibes.random()) }
        for (v in vibes.take(2)) {
            val page = (1..4).random()
            val results = runCatching {
                tmdb.discoverByVibe(
                    genreIds = v.genreIds,
                    keywordIds = v.keywordIds,
                    page = page,
                )
            }.getOrDefault(emptyList())
            vibePicks += results
        }

        // Interleave seed recs with vibe picks; fill from popular if thin.
        val picked = (seedResults + vibePicks).distinctBy { it.id }.take(12)
        val filler = if (picked.size < 6) {
            runCatching { tmdb.popular() }.getOrDefault(emptyList())
        } else emptyList()
        val final = (picked + filler).distinctBy { it.id }.take(8)

        val intro = when {
            parsed.seedTitles.isNotEmpty() && parsed.vibes.isNotEmpty() ->
                "If you liked ${parsed.seedTitles.first()} but want it ${parsed.vibes.first().words.firstOrNull() ?: "different"}, try these:"
            parsed.seedTitles.isNotEmpty() ->
                "Fans of ${parsed.seedTitles.first()} usually love these:"
            else -> "Here's a batch of picks for \"${prompt.take(60)}\":"
        }
        return Reply(intro, final.map { Suggestion(it, it.name, true) })
    }

    // ---------- LLM path ----------

    private data class LlmResponse(val choices: List<Choice> = emptyList()) {
        data class Choice(val message: Message? = null)
        data class Message(val role: String? = null, val content: String? = null)
    }

    private suspend fun llmRecommend(prompt: String): Reply = withContext(Dispatchers.IO) {
        val system = """
            You are Moovie, a movie recommendation host. Given a user's vibe request,
            reply with ONLY a JSON object: {"intro": "<one playful sentence>",
            "titles": ["Movie Title", "..."]} with 6-8 real movie or TV titles that
            best match the request. No markdown, no extra text.
        """.trimIndent()

        val body = gson.toJson(mapOf(
            "model" to "gpt-4o-mini",
            "temperature" to 0.8,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to prompt),
            ),
        ))

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val json = resp.body?.string() ?: error("Empty response")
            val parsed = gson.fromJson(json, LlmResponse::class.java)
            val content = parsed.choices.firstOrNull()?.message?.content ?: error("No content")
            val clean = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = gson.fromJson(clean, Map::class.java)
            val intro = obj["intro"]?.toString() ?: "Here's what I've got:"
            @Suppress("UNCHECKED_CAST")
            val names = (obj["titles"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            val suggestions = names.map { raw ->
                val found = runCatching { tmdb.searchMoviesOnly(raw) }.getOrNull()
                Suggestion(found?.firstOrNull(), raw, found?.isNotEmpty() == true)
            }
            Reply(intro, suggestions)
        }
    }
}
