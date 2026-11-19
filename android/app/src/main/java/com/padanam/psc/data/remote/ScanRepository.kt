package com.padanam.psc.data.remote

import com.padanam.psc.data.model.AiOption
import com.padanam.psc.data.model.AiQuestion
import com.padanam.psc.data.model.AiScanResult
import com.padanam.psc.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/** A scan failure with a message that is safe to show to the user. */
class ScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ScanImage(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg"
)

/**
 * Sends photographs to the deployed /api/scan function. The provider key lives
 * in Cloudflare Pages, so the APK only carries the signed-in session token.
 */
class ScanRepository(private val serverApi: ServerApi) {

    suspend fun extractQuestions(
        baseUrl: String,
        token: String,
        images: List<ScanImage>
    ): AiScanResult = withContext(Dispatchers.IO) {
        if (images.isEmpty()) throw ScanException("Add at least one photograph to scan.")
        if (images.size > MAX_IMAGES) {
            throw ScanException("You can analyse up to $MAX_IMAGES photographs at once.")
        }
        if (token.isBlank()) throw ScanException("Sign in again to scan questions.")

        val text = try {
            serverApi.scan(
                baseUrl = baseUrl,
                token = token,
                prompt = buildPrompt(images.size),
                images = images
            )
        } catch (error: ServerException) {
            throw ScanException(error.message ?: "The scanning service could not be reached.", error)
        }

        val json = stripCodeFence(text)
        val decoded = try {
            AppJson.decodeFromString(AiScanResult.serializer(), json)
        } catch (error: SerializationException) {
            throw ScanException("The scanner's answer wasn't valid question JSON. Please scan again.", error)
        } catch (error: IllegalArgumentException) {
            throw ScanException("The scanner's answer wasn't valid question JSON. Please scan again.", error)
        }

        validate(decoded)
    }

    private fun buildPrompt(count: Int): String = buildString {
        append(SYSTEM_PROMPT)
        append("\n\nExtract every multiple-choice question visible in these ")
        append(count)
        append(" image")
        if (count != 1) append("s")
        append(". If two photographs overlap, return each question once.\n\n")
        append("Return only a JSON object with this exact shape:\n")
        append("{\"title\":\"Short page title\",\"subject\":\"Broad subject\",\"questions\":[")
        append("{\"question_en\":\"...\",\"question_ml\":\"...\",\"options\":[")
        append("{\"letter\":\"A\",\"text_en\":\"...\",\"text_ml\":\"...\"},")
        append("{\"letter\":\"B\",\"text_en\":\"...\",\"text_ml\":\"...\"},")
        append("{\"letter\":\"C\",\"text_en\":\"...\",\"text_ml\":\"...\"},")
        append("{\"letter\":\"D\",\"text_en\":\"...\",\"text_ml\":\"...\"},")
        append("{\"letter\":\"E\",\"text_en\":\"...\",\"text_ml\":\"...\"},")
        append("{\"letter\":\"F\",\"text_en\":\"...\",\"text_ml\":\"...\"}],")
        append("\"correct_option\":\"A\",\"explanation_en\":\"...\",\"explanation_ml\":\"...\",")
        append("\"topic\":\"Specific topic\",\"difficulty\":\"easy\",")
        append("\"confidence\":80}]}\n")
        append("Use between two and six options per question. Omit E and F for four-option questions. Do not wrap the JSON in markdown.")
    }

    private fun validate(raw: AiScanResult): AiScanResult {
        val cleaned = raw.questions.mapNotNull { question ->
            val stemEn = question.questionEn.clean()
            val stemMl = question.questionMl.clean()
            if (stemEn.isBlank() && stemMl.isBlank()) return@mapNotNull null

            val options = question.options
                .take(MAX_OPTIONS)
                .mapIndexedNotNull { index, option ->
                    if (option.textEn.isBlank() && option.textMl.isBlank()) {
                        return@mapIndexedNotNull null
                    }
                    val printed = option.letter.trim().uppercase().take(1)
                    val original = printed.takeIf { it in LETTERS }
                        ?: LETTERS.getOrElse(index) { "A" }
                    original to option
                }
            if (options.size < 2) return@mapNotNull null

            val normalisedOptions = options.mapIndexed { position, item ->
                val option = item.second
                AiOption(
                    letter = LETTERS[position],
                    textEn = option.textEn.clean().ifBlank { option.textMl.clean() },
                    textMl = option.textMl.clean()
                )
            }

            /* Keep the printed answer letter aligned after any blank option
               has been dropped from the list. */
            val requested = question.correctOption.trim().uppercase().take(1)
            val keptIndex = options.indexOfFirst { it.first == requested }
            val letter = LETTERS.getOrNull(keptIndex) ?: "A"

            AiQuestion(
                questionEn = stemEn.ifBlank { stemMl },
                questionMl = stemMl,
                options = normalisedOptions,
                correctOption = letter,
                explanationEn = question.explanationEn.clean().ifBlank { question.explanationMl.clean() },
                explanationMl = question.explanationMl.clean(),
                topic = question.topic.clean().ifBlank { "General" }.take(60),
                difficulty = question.difficulty.clean().lowercase()
                    .takeIf { it in setOf("easy", "medium", "hard") } ?: "medium",
                confidence = question.confidence.coerceIn(0, 100)
            )
        }

        if (cleaned.isEmpty()) {
            throw ScanException(
                "No complete questions were found. Make sure every option is visible and the photo is in focus."
            )
        }

        return AiScanResult(
            title = raw.title.clean().ifBlank { "Scanned set" }.take(80),
            subject = raw.subject.clean().ifBlank { "General" }.take(40),
            questions = cleaned
        )
    }

    private fun String.clean(): String = trim()
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .replace(Regex("[ \\t]+"), " ")
        .trim()

    private fun stripCodeFence(value: String): String {
        var text = value.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter('\n', text)
            val end = text.lastIndexOf("```")
            if (end >= 0) text = text.substring(0, end)
        }
        return text.trim()
    }

    companion object {
        private const val MAX_IMAGES = 8
        private const val MAX_OPTIONS = 6
        private val LETTERS = listOf("A", "B", "C", "D", "E", "F")

        private val SYSTEM_PROMPT = """
            You are Padanam's exam question extraction service for Kerala PSC aspirants.
            You read photographs of printed question papers and return every visible
            multiple-choice question exactly once.

            Extraction rules:
            1. Group each question stem with its options in printed order. Most papers
               have four options, but some have two, three, five or six. Use exactly
               the options printed, labelled A-F.
            2. Provide both question_en and question_ml. If the printed question is in
               English, translate it naturally into Malayalam. If it is in Malayalam,
               put the original in question_ml and translate it into English. Never
               leave both empty. Use Malayalam script, not transliteration.
            3. Provide the printed options only. text_en and text_ml follow the same
               rule. Option letters must align between languages.
            4. correct_option is the letter A-F of the correct option. Infer it from an
               answer key, underline or mark when visible. When no key is visible, use
               your subject knowledge and lower the confidence. Never invent an option.
            5. explanation_en and explanation_ml explain why the answer is correct in
               one or two short sentences, in both languages.
            6. subject is the broad subject such as Biology, Physics, Chemistry,
               Mathematics, History, Geography, Malayalam or General Knowledge.
               topic is the specific topic. Keep both short.
            7. difficulty is easy, medium or hard. confidence is 0-100 and describes how
               sure you are about THIS question's text, options and answer.
            8. title is a short human title for the page, for example
               "Cell Division — Set 3". Reuse a printed header when present.
            9. Skip questions that are cut off or unreadable. Ignore page numbers,
               instructions and standalone answer explanations.
            10. If nothing is legible, return an empty questions array. Never make up
                questions that are not in the image.
        """.trimIndent()
    }
}
