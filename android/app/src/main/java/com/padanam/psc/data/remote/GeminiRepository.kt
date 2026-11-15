package com.padanam.psc.data.remote

import com.padanam.psc.data.model.AiOption
import com.padanam.psc.data.model.AiQuestion
import com.padanam.psc.data.model.AiScanResult
import com.padanam.psc.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** A scan failure with a message that is safe to show to the user. */
class ScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ScanImage(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg"
)

/**
 * Talks to the Gemini generateContent endpoint. The response is constrained
 * with a JSON schema, then normalised locally so the rest of the app always
 * receives a clean four-option question regardless of small model quirks.
 */
class GeminiRepository(
    private val api: GeminiApi = Network.geminiApi
) {

    suspend fun extractQuestions(
        apiKey: String,
        model: String,
        images: List<ScanImage>
    ): AiScanResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ScanException("Add your Gemini API key in Settings to use AI capture.")
        if (images.isEmpty()) throw ScanException("Add at least one photograph to scan.")
        if (images.size > MAX_IMAGES) throw ScanException("You can analyse up to $MAX_IMAGES photographs at once.")

        val parts = buildList {
            add(
                GeminiPart(
                    text = "Extract every multiple-choice question visible in these " +
                        "${images.size} image${if (images.size == 1) "" else "s"}. " +
                        "Return only JSON that matches the schema. If two photographs overlap, " +
                        "return each question once."
                )
            )
            images.forEach { image ->
                add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = image.mimeType,
                            data = android.util.Base64.encodeToString(image.bytes, android.util.Base64.NO_WRAP)
                        )
                    )
                )
            }
        }

        val request = GeminiRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = SYSTEM_PROMPT))),
            contents = listOf(GeminiContent(role = "user", parts = parts)),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.15,
                maxOutputTokens = 16384,
                responseMimeType = "application/json",
                responseSchema = scanSchema()
            )
        )

        val response = try {
            api.generateContent(
                model = model.ifBlank { DEFAULT_MODEL }.removePrefix("models/"),
                apiKey = apiKey,
                request = request
            )
        } catch (error: HttpException) {
            throw ScanException(friendlyHttpMessage(error, model), error)
        } catch (error: IOException) {
            throw ScanException(
                "Couldn't reach Gemini. Check your internet connection and try again.",
                error
            )
        }

        response.promptFeedback?.blockReason?.let { reason ->
            throw ScanException("Gemini blocked this image ($reason). Try a clearer photo of the question sheet.")
        }

        val candidate = response.candidates.firstOrNull()
            ?: throw ScanException("Gemini returned no result. Try the scan again.")

        if (candidate.finishReason == "SAFETY") {
            throw ScanException("The image was blocked by Gemini's safety filter. Try another photo.")
        }
        if (candidate.finishReason == "MAX_TOKENS") {
            throw ScanException("That page had too many questions for one request. Scan fewer questions at a time.")
        }

        val raw = candidate.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString(separator = "")
            ?.trim()
            .orEmpty()

        if (raw.isBlank()) {
            throw ScanException("No text came back from Gemini. Take a sharper photo and try again.")
        }

        val json = stripCodeFence(raw)
        val decoded = try {
            AppJson.decodeFromString(AiScanResult.serializer(), json)
        } catch (error: SerializationException) {
            throw ScanException("Gemini's answer wasn't valid question JSON. Please scan again.", error)
        } catch (error: IllegalArgumentException) {
            throw ScanException("Gemini's answer wasn't valid question JSON. Please scan again.", error)
        }

        validate(decoded)
    }

    suspend fun testKey(apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ScanException("Enter a Gemini API key first.")
        try {
            val models = api.listModels(apiKey)
            if (models.models.isEmpty()) {
                "Key accepted, but no models were returned."
            } else {
                val wanted = model.ifBlank { DEFAULT_MODEL }.removePrefix("models/")
                val match = models.models.any { it.name.removePrefix("models/") == wanted }
                if (match) {
                    "Connected · $wanted is available."
                } else {
                    "Key accepted · ${models.models.size} models available."
                }
            }
        } catch (error: HttpException) {
            throw ScanException(friendlyHttpMessage(error, model), error)
        } catch (error: IOException) {
            throw ScanException("Couldn't reach Gemini. Check your connection.", error)
        }
    }

    private fun validate(raw: AiScanResult): AiScanResult {
        val cleaned = raw.questions.mapNotNull { question ->
            val stemEn = question.questionEn.clean()
            val stemMl = question.questionMl.clean()
            if (stemEn.isBlank() && stemMl.isBlank()) return@mapNotNull null

            val options = question.options
                .filter { it.textEn.isNotBlank() || it.textMl.isNotBlank() }
                .take(4)
            if (options.size != 4) return@mapNotNull null

            val normalisedOptions = options.mapIndexed { index, option ->
                AiOption(
                    letter = LETTERS[index],
                    textEn = option.textEn.clean().ifBlank { option.textMl.clean() },
                    textMl = option.textMl.clean()
                )
            }

            val letter = question.correctOption
                .trim()
                .uppercase()
                .firstOrNull()
                ?.takeIf { it in LETTERS }
                ?.toString()
                ?: "A"

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
                "No complete questions were found. Make sure the four options are visible and the photo is in focus."
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

    private fun friendlyHttpMessage(error: HttpException, model: String): String {
        val raw = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
        val apiError = raw?.let {
            runCatching { AppJson.decodeFromString(GeminiErrorEnvelope.serializer(), it) }.getOrNull()
        }?.error
        val detail = apiError?.message?.takeIf { it.isNotBlank() }
        return when (error.code()) {
            400 -> detail ?: "Gemini rejected the request. Check the model name and try again."
            401, 403 -> "Gemini refused the API key. Paste a valid key from Google AI Studio."
            404 -> "Model \"${model.ifBlank { DEFAULT_MODEL }}\" wasn't found. Use gemini-2.5-flash or a model available to your key."
            429 -> "Gemini rate limit reached. Wait a moment, then try again."
            in 500..599 -> "Gemini is having trouble right now. Try again in a minute."
            else -> detail ?: "Gemini error ${error.code()}. Try again."
        }
    }

    private fun scanSchema(): GeminiSchema {
        val optionSchema = GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "letter" to GeminiSchema("STRING", "Option letter: A, B, C or D"),
                "text_en" to GeminiSchema("STRING", "Option text in English"),
                "text_ml" to GeminiSchema("STRING", "Option text in Malayalam script")
            ),
            required = listOf("letter", "text_en", "text_ml")
        )

        val questionSchema = GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "question_en" to GeminiSchema("STRING", "Question stem in English"),
                "question_ml" to GeminiSchema("STRING", "Question stem in Malayalam script"),
                "options" to GeminiSchema(
                    type = "ARRAY",
                    description = "Exactly four options in printed order",
                    items = optionSchema
                ),
                "correct_option" to GeminiSchema(
                    type = "STRING",
                    description = "Letter of the correct answer (A-D)",
                    enum = LETTERS
                ),
                "explanation_en" to GeminiSchema("STRING", "One or two sentence explanation in English"),
                "explanation_ml" to GeminiSchema("STRING", "One or two sentence explanation in Malayalam"),
                "topic" to GeminiSchema("STRING", "Specific topic, e.g. Mitosis"),
                "difficulty" to GeminiSchema(
                    "STRING",
                    "Question difficulty",
                    enum = listOf("easy", "medium", "hard")
                ),
                "confidence" to GeminiSchema("INTEGER", "Extraction confidence from 0 to 100")
            ),
            required = listOf(
                "question_en", "question_ml", "options", "correct_option",
                "explanation_en", "explanation_ml", "topic", "difficulty", "confidence"
            )
        )

        return GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "title" to GeminiSchema("STRING", "Short title for this page or set"),
                "subject" to GeminiSchema("STRING", "Broad subject, e.g. Biology"),
                "questions" to GeminiSchema(
                    type = "ARRAY",
                    description = "Every legible multiple-choice question",
                    items = questionSchema
                )
            ),
            required = listOf("title", "subject", "questions")
        )
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val MAX_IMAGES = 8
        private val LETTERS = listOf("A", "B", "C", "D")

        private val SYSTEM_PROMPT = """
            You are Padanam's exam question extraction service for Kerala PSC aspirants.
            You read photographs of printed question papers and return every visible
            multiple-choice question exactly once.

            Extraction rules:
            1. Group each question stem with its four options A-D in printed order.
            2. Provide both question_en and question_ml. If the printed question is in
               English, translate it naturally into Malayalam. If it is in Malayalam,
               put the original in question_ml and translate it into English. Never
               leave both empty. Use Malayalam script, not transliteration.
            3. Provide exactly four options. text_en and text_ml follow the same rule.
               Option letters must align between languages.
            4. correct_option is the letter A-D of the correct option. Infer it from an
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
