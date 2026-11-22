package com.padanam.psc.data.repo

import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.local.SetEntity
import com.padanam.psc.data.model.AiOption
import com.padanam.psc.data.model.AiQuestion
import com.padanam.psc.data.model.AiScanResult
import com.padanam.psc.data.model.ScanDestination
import com.padanam.psc.data.model.Topics
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.util.AppJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID

data class AppendScanResult(val added: Int, val title: String)

/**
 * Everything the admin editor can change about one question. Lists always
 * carry six slots so switching the visible option count is lossless.
 */
data class QuestionDraft(
    val setId: String,
    val questionId: String? = null,
    val questionEn: String = "",
    val questionMl: String = "",
    val optionsEn: List<String> = List(6) { "" },
    val optionsMl: List<String> = List(6) { "" },
    val optionCount: Int = 4,
    val correctOption: String = "A",
    val explanationEn: String = "",
    val explanationMl: String = "",
    val topic: String = "General",
    val difficulty: String = "medium"
) {
    val letters: List<Char> get() = ('A' until 'A' + optionCount.coerceIn(2, 6)).toList()

    val valid: Boolean get() = validationError() == null

    fun validationError(): String? {
        if (questionEn.isBlank() && questionMl.isBlank()) {
            return "Add the question text."
        }
        val count = optionCount.coerceIn(2, 6)
        val filled = (0 until count).count { index ->
            optionsEn.getOrElse(index) { "" }.isNotBlank() ||
                optionsMl.getOrElse(index) { "" }.isNotBlank()
        }
        if (filled < 2) return "Add at least two options."
        val letter = correctOption.uppercase().firstOrNull()
        if (letter == null || letter !in letters) {
            return "Choose which option is correct."
        }
        return null
    }
}

class LibraryRepository(private val dao: AppDao) {

    /**
     * Saves a reviewed AI extraction as a self-contained set. The set is
     * written when the admin presses Save, so closing the review loses nothing
     * that was committed and changing the destination is free.
     */
    suspend fun saveScan(
        result: AiScanResult,
        source: String,
        imagePaths: List<String>
    ): String {
        val now = System.currentTimeMillis()
        val existingTitles = dao.setsOnce().map { it.title.lowercase() }.toSet()
        val title = uniqueTitle(result.title.ifBlank { "Scanned set" }, existingTitles)
        val subject = result.subject.ifBlank { "General" }
        val setId = "set_${UUID.randomUUID()}"

        val set = SetEntity(
            id = setId,
            title = title,
            subject = subject,
            mainTopic = Topics.normalize(result.mainTopic, title, subject),
            subTopic = Topics.subTopic(result.subTopic, title),
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

    /**
     * Appends a reviewed extraction to an existing set. The target keeps its
     * title, subject and questions; only `updatedAt` moves so the next sync
     * replaces the cloud copy.
     */
    suspend fun appendScan(setId: String, result: AiScanResult): AppendScanResult {
        val set = dao.getSet(setId) ?: return AppendScanResult(0, "")
        if (result.questions.isEmpty()) return AppendScanResult(0, set.title)

        val existing = dao.questionsOnce(setId)
        val startOrder = (existing.maxOfOrNull { it.order } ?: -1) + 1
        val now = System.currentTimeMillis()
        val questions = result.questions.mapIndexed { index, question ->
            question.toEntity(
                setId = setId,
                order = startOrder + index,
                createdAt = now + index,
                confidenceFallback = 80
            )
        }

        dao.appendQuestionsToSet(set.copy(updatedAt = now, synced = false), questions)
        return AppendScanResult(questions.size, set.title)
    }

    /** Sets offered as append destinations on the scan review screen. */
    suspend fun scanDestinations(): List<ScanDestination> =
        dao.setSummariesOnce().map { summary ->
            ScanDestination(
                id = summary.id,
                title = summary.title,
                subject = summary.subject,
                mainTopic = summary.mainTopic,
                subTopic = summary.subTopic,
                questionCount = summary.questionCount
            )
        }

    /** Existing subtopic labels, reused when the administrator edits a set. */
    suspend fun subTopics(): List<String> =
        dao.setsOnce()
            .map { Topics.subTopic(it.subTopic, it.title) }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }

    suspend fun titleOf(setId: String): String = dao.getSet(setId)?.title.orEmpty()

    suspend fun renameSet(setId: String, newTitle: String, newTitleMl: String) {
        val set = dao.getSet(setId) ?: return
        updateSetDetails(
            setId = setId,
            title = newTitle,
            titleMl = newTitleMl,
            subject = set.subject,
            mainTopic = set.mainTopic,
            subTopic = set.subTopic
        )
    }

    /** Creates an empty administrator-authored set ready for questions. */
    suspend fun createSet(
        title: String,
        titleMl: String,
        subject: String,
        mainTopic: String = "",
        subTopic: String = ""
    ): String {
        val now = System.currentTimeMillis()
        val existingTitles = dao.setsOnce().map { it.title.lowercase() }.toSet()
        val cleanTitle = uniqueTitle(title.ifBlank { "Untitled set" }, existingTitles)
        val cleanSubject = subject.trim().take(60).ifBlank { "General" }
        val set = SetEntity(
            id = "set_${UUID.randomUUID()}",
            title = cleanTitle,
            titleMl = titleMl.trim().take(120),
            subject = cleanSubject,
            mainTopic = Topics.normalize(mainTopic, cleanTitle, cleanSubject),
            subTopic = Topics.subTopic(subTopic, cleanTitle),
            createdAt = now,
            updatedAt = now,
            source = SOURCE_MANUAL
        )
        dao.insertSet(set)
        return set.id
    }

    suspend fun updateSetDetails(
        setId: String,
        title: String,
        titleMl: String,
        subject: String,
        mainTopic: String? = null,
        subTopic: String? = null
    ) {
        val set = dao.getSet(setId) ?: return
        val cleanTitle = title.trim().take(80).ifBlank { set.title }
        val cleanMl = titleMl.trim().take(120)
        val cleanSubject = subject.trim().take(60).ifBlank { "General" }
        val cleanMain = Topics.normalize(mainTopic ?: set.mainTopic, cleanTitle, cleanSubject)
        val cleanSub = Topics.subTopic(subTopic ?: set.subTopic, cleanTitle)
        if (cleanTitle == set.title &&
            cleanMl == set.titleMl &&
            cleanSubject == set.subject &&
            cleanMain == set.mainTopic &&
            cleanSub == set.subTopic
        ) {
            return
        }
        dao.updateSet(
            set.copy(
                title = cleanTitle,
                titleMl = cleanMl,
                subject = cleanSubject,
                mainTopic = cleanMain,
                subTopic = cleanSub,
                updatedAt = System.currentTimeMillis(),
                synced = false
            )
        )
    }

    /** Inserts a new question or replaces an existing one, keeping its order. */
    suspend fun saveQuestion(draft: QuestionDraft): String {
        draft.validationError()?.let { throw IllegalArgumentException(it) }
        val set = dao.getSet(draft.setId)
            ?: throw IllegalArgumentException("This set is no longer on this phone.")
        val existing = draft.questionId?.let { dao.getQuestion(it) }
        val now = System.currentTimeMillis()
        val optionsEn = normalizeOptions(draft.optionsEn)
        val optionsMl = normalizeOptions(draft.optionsMl)
        val count = draft.optionCount.coerceIn(2, 6)
        val letters = ('A' until 'A' + count).toList()
        val correct = draft.correctOption.uppercase().take(1)
            .takeIf { it.isNotEmpty() && it.first() in letters }
            ?: "A"

        val question = QuestionEntity(
            id = existing?.id ?: "q_${UUID.randomUUID()}",
            setId = draft.setId,
            order = existing?.order
                ?: ((dao.questionsOnce(draft.setId).maxOfOrNull { it.order } ?: -1) + 1),
            questionEn = draft.questionEn.trim().ifBlank { draft.questionMl.trim() },
            questionMl = draft.questionMl.trim(),
            optionAEn = optionsEn[0],
            optionBEn = optionsEn[1],
            optionCEn = optionsEn[2],
            optionDEn = optionsEn[3],
            optionEEn = optionsEn[4],
            optionFEn = optionsEn[5],
            optionAMl = optionsMl[0],
            optionBMl = optionsMl[1],
            optionCMl = optionsMl[2],
            optionDMl = optionsMl[3],
            optionEMl = optionsMl[4],
            optionFMl = optionsMl[5],
            optionCount = count,
            correctOption = correct,
            explanationEn = draft.explanationEn.trim().ifBlank { draft.explanationMl.trim() },
            explanationMl = draft.explanationMl.trim(),
            topic = draft.topic.trim().take(60).ifBlank { "General" },
            difficulty = draft.difficulty.takeIf { it in setOf("easy", "medium", "hard") }
                ?: "medium",
            confidence = existing?.confidence ?: 100,
            createdAt = existing?.createdAt ?: now,
            synced = false
        )
        dao.saveQuestionAndTouchSet(
            set.copy(updatedAt = now, synced = false),
            question
        )
        return question.id
    }

    suspend fun deleteQuestion(setId: String, questionId: String) {
        val set = dao.getSet(setId) ?: return
        dao.deleteQuestionAndTouchSet(
            set.copy(updatedAt = System.currentTimeMillis(), synced = false),
            questionId
        )
    }

    /** Re-adds the built-in starter set when an admin asks for it. */
    suspend fun restoreDemos(): String = seedDemo()

    private fun normalizeOptions(values: List<String>): List<String> =
        List(6) { index -> values.getOrElse(index) { "" }.trim() }

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

    suspend fun exportJson(profile: AppSettings): String {
        return WebBackup.encode(
            sets = dao.setsOnce(),
            questions = dao.allQuestionsOnce(),
            attempts = dao.allAttemptsOnce(),
            profile = profile
        )
    }

    /**
     * Replaces the offline library with a web backup or an older Android
     * export. Imported rows are marked unsynced so the next cloud sync pushes
     * the restored library to the account workspace.
     */
    suspend fun replaceWith(backup: BackupImport) {
        dao.replaceLibrary(
            sets = backup.sets.map { it.first.copy(synced = false) },
            questions = backup.sets.flatMap { it.second }.map { it.copy(synced = false) },
            attempts = backup.attempts.map { it.copy(synced = false) }
        )
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
        val safeOptions = options.take(6)
        val optionCount = safeOptions.size.coerceIn(2, 6)
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
            optionEEn = safeOptions.textAt(4, true),
            optionFEn = safeOptions.textAt(5, true),
            optionAMl = safeOptions.textAt(0, false),
            optionBMl = safeOptions.textAt(1, false),
            optionCMl = safeOptions.textAt(2, false),
            optionDMl = safeOptions.textAt(3, false),
            optionEMl = safeOptions.textAt(4, false),
            optionFMl = safeOptions.textAt(5, false),
            optionCount = optionCount,
            correctOption = correctOption.uppercase().take(1)
                .takeIf { it in setOf("A", "B", "C", "D", "E", "F") } ?: "A",
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
        const val SOURCE_MANUAL = "manual"
    }
}
