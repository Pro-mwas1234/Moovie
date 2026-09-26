package com.Moovie.app.trivia

import com.Moovie.app.data.model.Title
import kotlin.random.Random

/**
 * Trivia/Quiz questions. A handful of curated classics plus generated
 * questions from live TMDB titles (which year / higher rated).
 */
data class Question(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
)

object TriviaBank {

    private val curated = listOf(
        Question(
            "Which movie features the line \"I'll be back\"?",
            listOf("Terminator", "Rocky", "Die Hard", "Predator"), 0,
        ),
        Question(
            "In Inception, what do characters use to share dreams?",
            listOf("A machine", "A potion", "A spell", "A phone"), 0,
        ),
        Question(
            "Who directed Jurassic Park?",
            listOf("Steven Spielberg", "James Cameron", "Ridley Scott", "George Lucas"), 0,
        ),
        Question(
            "What is the highest-grossing movie of all time (unadjusted)?",
            listOf("Avatar", "Avengers: Endgame", "Titanic", "Star Wars: The Force Awakens"), 0,
        ),
        Question(
            "Which animated movie features a clownfish searching for his son?",
            listOf("Finding Nemo", "Shark Tale", "Moana", "Luca"), 0,
        ),
        Question(
            "The Netfix series Stranger Things is set in which decade?",
            listOf("1980s", "1970s", "1990s", "2000s"), 0,
        ),
        Question(
            "Who played Iron Man in the MCU?",
            listOf("Robert Downey Jr.", "Chris Evans", "Mark Ruffalo", "Chris Hemsworth"), 0,
        ),
        Question(
            "In The Matrix, which pill does Neo take?",
            listOf("Red", "Blue", "Green", "White"), 0,
        ),
    )

    private fun shuffleQuestion(q: Question, rnd: Random): Question {
        val correct = q.options[q.correctIndex]
        val shuffled = q.options.shuffled(rnd)
        return q.copy(options = shuffled, correctIndex = shuffled.indexOf(correct))
    }

    fun draw(roundSize: Int = 6, titles: List<Title> = emptyList(), rnd: Random = Random): List<Question> {
        val out = mutableListOf<Question>()
        curated.shuffled(rnd).take(roundSize / 2).forEach { out.add(shuffleQuestion(it, rnd)) }

        // Generated: "Which year did X come out?"
        titles.filter { it.year != null }.shuffled(rnd).take(roundSize - out.size).forEach { t ->
            val actual = t.year!!
            val distractors = (actual - 4..actual + 4).filter { it != actual }.shuffled(rnd).take(3)
            val opts = (distractors + actual).map { it.toString() }.shuffled(rnd)
            out.add(
                Question(
                    text = "Which year did \"${t.name}\" come out?",
                    options = opts,
                    correctIndex = opts.indexOf(actual.toString()),
                )
            )
        }
        while (out.size < roundSize) {
            out.add(shuffleQuestion(curated.random(rnd), rnd))
        }
        return out.take(roundSize)
    }
}
