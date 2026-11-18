package com.padanam.psc.data.repo

import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.local.SetEntity
import com.padanam.psc.data.model.AiOption
import com.padanam.psc.data.model.AiQuestion
import com.padanam.psc.data.model.AiScanResult
import com.padanam.psc.util.AppJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class LibraryRepository(private val dao: AppDao) {

    /**
     * Saves a completed AI scan as a self-contained set. The set is stored
     * immediately, before the result screen appears, so losing network or
     * backgrounding the app can never lose an extraction.
     */
    suspend fun saveScan(
        result: AiScanResult,
        source: String,
        imagePaths: List<String>
    ): String {
        val now = System.currentTimeMillis()
        val existingTitles = dao.setsOnce().map { it.title.lowercase() }.toSet()
        val title = uniqueTitle(result.title.ifBlank { "Scanned set" }, existingTitles)
        val setId = "set_${UUID.randomUUID()}"

        val set = SetEntity(
            id = setId,
            title = title,
            subject = result.subject.ifBlank { "General" },
            createdAt = now,
            updatedAt = now,
            source = source,
            imageUrisJson = if (imagePaths.isEmpty()) {
                null
            } else {
                AppJson.encodeToString(ListSerializer(String.serializer()), imagePaths)
            }
        )

        val questions = result.questions.mapIndexed { index, question ->
            question.toEntity(
                setId = setId,
                order = index,
                createdAt = now + index,
                confidenceFallback = 80
            )
        }

        dao.insertSetWithQuestions(set, questions)
        return setId
    }

    suspend fun renameSet(setId: String, newTitle: String) {
        val set = dao.getSet(setId) ?: return
        val clean = newTitle.trim().take(80)
        if (clean.isBlank() || clean == set.title) return
        // Keep the set newer than the cloud copy so the next sync pushes it.
        dao.insertSet(
            set.copy(
                title = clean,
                updatedAt = System.currentTimeMillis(),
                synced = false
            )
        )
    }

    suspend fun deleteSet(setId: String) = dao.deleteSetWithQuestions(setId)

    suspend fun seedDemo(): String {
        dao.setsOnce().firstOrNull { it.source == DEMO_SOURCE }?.let { return it.id }
        val result = AiScanResult(
            title = "Kerala PSC · Starter 10",
            subject = "General Studies",
            questions = demoQuestions()
        )
        return saveScan(result, source = DEMO_SOURCE, imagePaths = emptyList())
    }

    suspend fun exportJson(): String {
        val sets = dao.setsOnce()
        val questions = dao.allQuestionsOnce()
        val attempts = dao.allAttemptsOnce()
        val questionsBySet = questions.groupBy { it.setId }
        val attemptsBySet = attempts.groupBy { it.setId }

        val root = buildJsonObject {
            put("app", "Padanam")
            put("version", 1)
            put("exportedAt", Instant.now().toString())
            put(
                "sets",
                buildJsonArray {
                    sets.forEach { set ->
                        add(
                            buildJsonObject {
                                put("id", set.id)
                                put("title", set.title)
                                put("subject", set.subject)
                                put("createdAt", set.createdAt)
                                put("source", set.source)
                                put("imageUris", set.imageUrisJson ?: "")
                                put(
                                    "questions",
                                    buildJsonArray {
                                        questionsBySet[set.id].orEmpty().sortedBy { it.order }.forEach { q ->
                                            add(q.toJson())
                                        }
                                    }
                                )
                                put(
                                    "attempts",
                                    buildJsonArray {
                                        attemptsBySet[set.id].orEmpty().forEach { a ->
                                            add(
                                                buildJsonObject {
                                                    put("id", a.id)
                                                    put("mode", a.mode)
                                                    put("total", a.total)
                                                    put("correct", a.correct)
                                                    put("wrong", a.wrong)
                                                    put("skipped", a.skipped)
                                                    put("scorePercent", a.scorePercent)
                                                    put("durationSeconds", a.durationSeconds)
                                                    put("startedAt", a.startedAt)
                                                    put("completedAt", a.completedAt)
                                                    put("answers", AppJson.parseToJsonElement(a.answersJson))
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
            put(
                "orphanAttempts",
                buildJsonArray {
                    attempts.filter { it.setId == null }.forEach { a ->
                        add(
                            buildJsonObject {
                                put("id", a.id)
                                put("title", a.setTitle)
                                put("subject", a.subject)
                                put("mode", a.mode)
                                put("scorePercent", a.scorePercent)
                                put("completedAt", a.completedAt)
                                put("answers", AppJson.parseToJsonElement(a.answersJson))
                            }
                        )
                    }
                }
            )
        }
        return AppJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }

    private fun uniqueTitle(base: String, existing: Set<String>): String {
        val clean = base.trim().ifBlank { "Scanned set" }
        if (clean.lowercase() !in existing) return clean
        var index = 2
        while ("$clean ($index)".lowercase() in existing && index < 99) index += 1
        return "$clean ($index)"
    }

    private fun AiQuestion.toEntity(
        setId: String,
        order: Int,
        createdAt: Long,
        confidenceFallback: Int
    ): QuestionEntity {
        val safeOptions = options.take(4)
        return QuestionEntity(
            id = "q_${UUID.randomUUID()}",
            setId = setId,
            order = order,
            questionEn = questionEn.ifBlank { questionMl },
            questionMl = questionMl,
            optionAEn = safeOptions.textAt(0, true),
            optionBEn = safeOptions.textAt(1, true),
            optionCEn = safeOptions.textAt(2, true),
            optionDEn = safeOptions.textAt(3, true),
            optionAMl = safeOptions.textAt(0, false),
            optionBMl = safeOptions.textAt(1, false),
            optionCMl = safeOptions.textAt(2, false),
            optionDMl = safeOptions.textAt(3, false),
            correctOption = correctOption.uppercase().take(1).takeIf { it in setOf("A", "B", "C", "D") } ?: "A",
            explanationEn = explanationEn.ifBlank { explanationMl },
            explanationMl = explanationMl,
            topic = topic.ifBlank { "General" },
            difficulty = difficulty,
            confidence = confidence.coerceIn(0, 100).takeIf { it > 0 } ?: confidenceFallback,
            createdAt = createdAt
        )
    }

    private fun List<AiOption>.textAt(index: Int, english: Boolean): String {
        val option = getOrNull(index) ?: return ""
        return if (english) {
            option.textEn.ifBlank { option.textMl }
        } else {
            option.textMl
        }
    }

    private fun QuestionEntity.toJson() = buildJsonObject {
        put("id", id)
        put("order", order + 1)
        put("questionEn", questionEn)
        put("questionMl", questionMl)
        put("optionsEn", JsonArray(listOf(optionAEn, optionBEn, optionCEn, optionDEn).map { kotlinx.serialization.json.JsonPrimitive(it) }))
        put("optionsMl", JsonArray(listOf(optionAMl, optionBMl, optionCMl, optionDMl).map { kotlinx.serialization.json.JsonPrimitive(it) }))
        put("correctOption", correctOption)
        put("explanationEn", explanationEn)
        put("explanationMl", explanationMl)
        put("topic", topic)
        put("difficulty", difficulty)
        put("confidence", confidence)
        put("createdAt", createdAt)
    }

    private fun demoQuestions(): List<AiQuestion> = listOf(
        AiQuestion(
            questionEn = "Which article of the Indian Constitution guarantees the Right to Education?",
            questionMl = "ഇന്ത്യൻ ഭരണഘടനയുടെ ഏത് അനുച്ഛേദമാണ് വിദ്യാഭ്യാസ അവകാശം ഉറപ്പ് നൽകുന്നത്?",
            options = demoOptions(
                "Article 14", "Article 19", "Article 21A", "Article 32",
                "അനുച്ഛേദം 14", "അനുച്ഛേദം 19", "അനുച്ഛേദം 21A", "അനുച്ഛേദം 32"
            ),
            correctOption = "C",
            explanationEn = "Article 21A was inserted by the 86th Constitutional Amendment in 2002 and makes free education a fundamental right for children aged 6 to 14.",
            explanationMl = "2002-ലെ 86-ാം ഭരണഘടനാ ഭേദഗതിയിലൂടെയാണ് അനുച്ഛേദം 21A ചേർത്തത്. 6 മുതൽ 14 വയസ്സുവരെയുള്ള കുട്ടികൾക്ക് സൗജന്യ വിദ്യാഭ്യാസം അടിസ്ഥാന അവകാശമായി ഇത് ഉറപ്പ് നൽകുന്നു.",
            topic = "Constitution",
            difficulty = "medium",
            confidence = 99
        ),
        AiQuestion(
            questionEn = "Which is the largest gland in the human body?",
            questionMl = "മനുഷ്യശരീരത്തിലെ ഏറ്റവും വലിയ ഗ്രന്ഥിയേതാണ്?",
            options = demoOptions(
                "Pancreas", "Liver", "Thyroid", "Salivary gland",
                "പാൻക്രിയാസ്", "കരൾ", "തൈറോയ്ഡ്", "ഉമിനീർ ഗ്രന്ഥി"
            ),
            correctOption = "B",
            explanationEn = "The liver is the largest internal gland. It produces bile and plays a central role in metabolism.",
            explanationMl = "ശരീരത്തിനുള്ളിലെ ഏറ്റവും വലിയ ഗ്രന്ഥിയാണ് കരൾ. പിത്തരസം ഉൽപ്പാദിപ്പിക്കുകയും ഉപാപചയ പ്രവർത്തനങ്ങളിൽ പ്രധാന പങ്ക് വഹിക്കുകയും ചെയ്യുന്നു.",
            topic = "Human body",
            difficulty = "easy",
            confidence = 99
        ),
        AiQuestion(
            questionEn = "What is the SI unit of electric current?",
            questionMl = "വൈദ്യുത പ്രവാഹത്തിന്റെ SI യൂണിറ്റ് ഏതാണ്?",
            options = demoOptions(
                "Volt", "Watt", "Ampere", "Ohm",
                "വോൾട്ട്", "വാട്ട്", "ആമ്പിയർ", "ഓം"
            ),
            correctOption = "C",
            explanationEn = "The ampere (A) is the SI base unit of electric current, named after André-Marie Ampère.",
            explanationMl = "വൈദ്യുത പ്രവാഹത്തിന്റെ SI അടിസ്ഥാന യൂണിറ്റാണ് ആമ്പിയർ (A). ആന്ദ്രേ-മാരി ആമ്പിയറിന്റെ പേരിലാണ് ഇത് അറിയപ്പെടുന്നത്.",
            topic = "Electricity",
            difficulty = "easy",
            confidence = 99
        ),
        AiQuestion(
            questionEn = "Which was the first Malayalam newspaper?",
            questionMl = "ആദ്യത്തെ മലയാളം പത്രം ഏതായിരുന്നു?",
            options = demoOptions(
                "Rajyasamacharam", "Swadeshabhimani", "Malayala Manorama", "Mathrubhumi",
                "രാജ്യസമാചാരം", "സ്വദേശാഭിമാനി", "മലയാള മനോരമ", "മാതൃഭൂമി"
            ),
            correctOption = "A",
            explanationEn = "Rajyasamacharam was launched in 1847 by Hermann Gundert, making it the first Malayalam newspaper.",
            explanationMl = "1847-ൽ ഹെർമൻ ഗുണ്ടർട്ടാണ് രാജ്യസമാചാരം പ്രസിദ്ധീകരിച്ചത്. ആദ്യത്തെ മലയാളം പത്രമായി ഇത് കണക്കാക്കപ്പെടുന്നു.",
            topic = "Kerala history",
            difficulty = "medium",
            confidence = 98
        ),
        AiQuestion(
            questionEn = "Silent Valley National Park is located in which district of Kerala?",
            questionMl = "സൈലന്റ് വാലി ദേശീയോദ്യാനം കേരളത്തിലെ ഏത് ജില്ലയിലാണ്?",
            options = demoOptions(
                "Wayanad", "Idukki", "Palakkad", "Pathanamthitta",
                "വയനാട്", "ഇടുക്കി", "പാലക്കാട്", "പത്തനംതിട്ട"
            ),
            correctOption = "C",
            explanationEn = "Silent Valley is in the Nilgiri Biosphere Reserve, inside Palakkad district. It is known for the lion-tailed macaque.",
            explanationMl = "നീലഗിരി ജീവമണ്ഡല സംരക്ഷണ മേഖലയിൽ, പാലക്കാട് ജില്ലയിലാണ് സൈലന്റ് വാലി സ്ഥിതി ചെയ്യുന്നത്. സിംഹവാലൻ കുരങ്ങിന് ഇത് പ്രശസ്തമാണ്.",
            topic = "Kerala geography",
            difficulty = "medium",
            confidence = 99
        ),
        AiQuestion(
            questionEn = "What is 15% of 240?",
            questionMl = "240-ന്റെ 15% എത്രയാണ്?",
            options = demoOptions("24", "30", "36", "42", "24", "30", "36", "42"),
            correctOption = "C",
            explanationEn = "15% of 240 = 0.15 × 240 = 36.",
            explanationMl = "240-ന്റെ 15% = 0.15 × 240 = 36.",
            topic = "Arithmetic",
            difficulty = "easy",
            confidence = 99
        ),
        AiQuestion(
            questionEn = "Which planet is known as the Red Planet?",
            questionMl = "ചെമ്പൻ ഗ്രഹം എന്ന് അറിയപ്പെടുന്ന ഗ്രഹം ഏതാണ്?",
            options = demoOptions(
                "Venus", "Mars", "Jupiter", "Mercury",
                "ശുക്രൻ", "ചൊവ്വ", "വ്യാഴം", "ബുധൻ"
            ),
            correctOption = "B",
            explanationEn = "Mars appears red because iron oxide dust covers much of its surface.",
            explanationMl = "ചൊവ്വയുടെ ഉപരിതലത്തിൽ ഇരുമ്പ് ഓക്സൈഡ് പൊടി ധാരാളമായി കാണുന്നതിനാൽ അത് ചുവപ്പ് നിറത്തിൽ കാണപ്പെടുന്നു.",
            topic = "Astronomy",
            difficulty = "easy",
            confidence = 99
        ),
        AiQuestion(
            questionEn = "Who wrote the Malayalam novel 'Agnisakshi'?",
            questionMl = "‘അഗ്നിസാക്ഷി’ എന്ന മലയാള നോവൽ രചിച്ചത് ആരാണ്?",
            options = demoOptions(
                "Lalithambika Antharjanam", "K. Saraswathi Amma", "Sarah Joseph", "Kamala Das",
                "ലളിതാംബിക അന്തർജനം", "കെ. സരസ്വതി അമ്മ", "സാറാ ജോസഫ്", "കമലാ ദാസ്"
            ),
            correctOption = "A",
            explanationEn = "Agnisakshi (1976) is by Lalithambika Antharjanam. It won the Kendra Sahitya Akademi Award.",
            explanationMl = "1976-ൽ പ്രസിദ്ധീകരിച്ച അഗ്നിസാക്ഷി ലളിതാംബിക അന്തർജനത്തിന്റെ രചനയാണ്. ഈ നോവലിന് കേന്ദ്ര സാഹിത്യ അക്കാദമി പുരസ്കാരം ലഭിച്ചിട്ടുണ്ട്.",
            topic = "Malayalam literature",
            difficulty = "medium",
            confidence = 97
        ),
        AiQuestion(
            questionEn = "Which is the highest peak in Kerala?",
            questionMl = "കേരളത്തിലെ ഏറ്റവും ഉയരമുള്ള കൊടുമുടി ഏതാണ്?",
            options = demoOptions(
                "Agasthyamala", "Anamudi", "Chembra", "Vellarimala",
                "അഗസ്ത്യമല", "ആനമുടി", "ചെമ്പ്ര", "വെള്ളരിമല"
            ),
            correctOption = "B",
            explanationEn = "Anamudi, in Idukki district, rises to 2,695 metres and is the highest peak in Kerala and South India.",
            explanationMl = "ഇടുക്കി ജില്ലയിലെ ആനമുടി 2,695 മീറ്റർ ഉയരമുള്ളതും കേരളത്തിലെയും ദക്ഷിണേന്ത്യയിലെയും ഏറ്റവും ഉയരമുള്ള കൊടുമുടിയുമാണ്.",
            topic = "Kerala geography",
            difficulty = "easy",
            confidence = 99
        ),
        AiQuestion(
            questionEn = "The study of fossils is known as:",
            questionMl = "ഫോസിലുകളെക്കുറിച്ചുള്ള പഠനം എന്ന് അറിയപ്പെടുന്നു:",
            options = demoOptions(
                "Palaeontology", "Ornithology", "Etymology", "Seismology",
                "പാലിയന്റോളജി", "ഓർണിത്തോളജി", "എറ്റിമോളജി", "സീസ്മോളജി"
            ),
            correctOption = "A",
            explanationEn = "Palaeontology is the branch of science that studies fossils and ancient life forms.",
            explanationMl = "ഫോസിലുകളെയും പ്രാചീന ജീവികളെയും പഠിക്കുന്ന ശാസ്ത്രശാഖയാണ് പാലിയന്റോളജി.",
            topic = "Geology",
            difficulty = "easy",
            confidence = 99
        )
    )

    private fun demoOptions(
        aEn: String, bEn: String, cEn: String, dEn: String,
        aMl: String, bMl: String, cMl: String, dMl: String
    ): List<AiOption> = listOf(
        AiOption("A", aEn, aMl),
        AiOption("B", bEn, bMl),
        AiOption("C", cEn, cMl),
        AiOption("D", dEn, dMl)
    )

    companion object {
        const val DEMO_SOURCE = "demo"
        const val SOURCE_SCAN = "scan"
        const val SOURCE_IMPORT = "import"
    }
}
