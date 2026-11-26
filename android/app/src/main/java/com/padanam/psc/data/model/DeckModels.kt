package com.padanam.psc.data.model

import com.padanam.psc.data.local.DeckProgressEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.local.SetEntity
import kotlinx.serialization.Serializable

/** Learned state for one card. Unknown cards are simply absent from the map. */
object DeckStatus {
    const val KNOWN = "known"
    const val SEEN = "seen"
    const val UNSEEN = "unseen"

    fun normalize(value: String?): String = when (value) {
        KNOWN, SEEN, UNSEEN -> value
        else -> UNSEEN
    }
}

object DeckResult {
    const val GOT_IT = "gotit"
    const val AGAIN = "again"

    fun normalize(value: String?): String = when (value) {
        GOT_IT, AGAIN -> value
        else -> ""
    }
}

/** The previous learned state captured with each swipe so undo survives a restart. */
@Serializable
data class DeckProgressSnapshot(
    val status: String = DeckStatus.UNSEEN,
    val lastResult: String = "",
    val seenAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
)

@Serializable
data class DeckAction(
    val key: String,
    val result: String,
    val atEpochMs: Long,
    val before: DeckProgressSnapshot? = null
)

/**
 * One local round of a deck. Only the card order and the swipe history live
 * here, so progress stays useful when the same deck is opened on the web.
 */
@Serializable
data class DeckSession(
    val id: String,
    val mainTopic: String,
    val subTopic: String = "",
    val scope: String = "subtopic",
    val keys: List<String> = emptyList(),
    val actions: List<DeckAction> = emptyList(),
    val round: Int = 1,
    val startedAtEpochMs: Long = 0L
)

/**
 * One card from the optional scanned-deck database. It is cached on the phone
 * so the deck library still opens offline, and its key (`scan:<deckId>:<cardId>`)
 * is what deck progress syncs under.
 */
@Serializable
data class RemoteDeckCard(
    val key: String,
    val deckId: String,
    val cardId: String,
    val deckTitle: String = "",
    val mainTopic: String = "",
    val subTopic: String = "",
    val language: String = "en",
    val stem: String = "",
    val stemMl: String = "",
    val answer: String = "",
    val answerMl: String = "",
    val explanation: String = "",
    val explanationMl: String = "",
    val updatedAt: String = ""
)

/** A question projected into its one-question/one-answer deck card. */
data class DeckCard(
    val key: String,
    val setId: String,
    val questionId: String,
    val mainTopic: String,
    val subTopic: String,
    val setTitle: String,
    val subject: String,
    private val stemEn: String,
    private val stemMl: String,
    private val optionsEn: List<String>,
    private val optionsMl: List<String>,
    private val answerIndex: Int,
    private val explanationEn: String,
    private val explanationMl: String
) {
    private val mlReady: Boolean
        get() = stemMl.isNotBlank() &&
            optionsMl.isNotEmpty() &&
            optionsMl.size == optionsEn.size &&
            optionsMl.all { it.isNotBlank() }

    /** Mirrors the web app: Malayalam is used only when the whole card is bilingual. */
    fun usesMalayalam(lang: String): Boolean = lang == Lang.ML && mlReady

    fun stem(lang: String): String =
        if (usesMalayalam(lang)) stemMl
        else stemEn.ifBlank { stemMl }

    fun options(lang: String): List<String> =
        if (usesMalayalam(lang)) optionsMl else optionsEn

    fun answer(lang: String): String {
        val list = options(lang)
        return list.getOrElse(answerIndex) { list.firstOrNull().orEmpty() }
    }

    fun answerLetter(): String = LETTERS.getOrElse(answerIndex) { "A" }

    fun explanation(lang: String): String {
        val primary = if (usesMalayalam(lang)) explanationMl else explanationEn
        return primary.ifBlank {
            if (usesMalayalam(lang)) explanationEn else explanationMl
        }
    }

    companion object {
        private val LETTERS = listOf("A", "B", "C", "D", "E", "F")

        fun indexOfOption(letter: String): Int =
            LETTERS.indexOf(letter.trim().uppercase()).takeIf { it >= 0 } ?: 0
    }
}

data class DeckSubtopic(
    val name: String,
    val cards: List<DeckCard>,
    val learned: Int,
    val total: Int,
    val pct: Int
)

data class DeckTopic(
    val name: String,
    val cards: List<DeckCard>,
    val subtopics: List<DeckSubtopic>,
    val learned: Int,
    val total: Int,
    val pct: Int
)

data class DeckOverview(
    val cards: Int = 0,
    val learned: Int = 0,
    val pct: Int = 0,
    val topics: List<DeckTopic> = emptyList()
) {
    val topicCount: Int get() = topics.size
}

data class DeckSummary(
    val session: DeckSession,
    val cards: List<DeckCard>,
    val total: Int,
    val sorted: Int,
    val current: DeckCard?,
    val position: Int,
    val gotIt: Int,
    val again: Int,
    val missed: List<DeckCard>,
    val recall: Int,
    val complete: Boolean,
    /** Result per card key, used by the progress segments at the top of a round. */
    val results: Map<String, String> = emptyMap()
) {
    val missedKeys: List<String> get() = missed.map { it.key }
}

data class DeckContinueInfo(
    val mainTopic: String,
    val subTopic: String,
    val title: String,
    val remaining: Int,
    val total: Int,
    val sorted: Int,
    val round: Int
)

/**
 * Pure deck projection used by the app view model and both sync paths. Keeping
 * it free of Android types makes the grouping rules easy to compare with
 * `web/js/deck.js`.
 */
object Decks {

    const val MAX_KEYS = 20_000
    const val MAX_ACTIONS = 20_000

    private val REMOTE_KEY = Regex("^scan:[a-z0-9][a-z0-9_-]{0,60}:[a-z0-9][a-z0-9_-]{0,60}$")

    fun isRemoteKey(key: String): Boolean = REMOTE_KEY.matches(key)

    fun keyFor(setId: String, questionId: String): String =
        "question:$setId:$questionId"

    fun buildCards(
        sets: List<SetEntity>,
        questions: List<QuestionEntity>,
        remoteCards: List<RemoteDeckCard> = emptyList()
    ): List<DeckCard> {
        val bySet = questions.groupBy { it.setId }
        val cards = ArrayList<DeckCard>(questions.size + remoteCards.size)
        val seen = HashSet<String>(questions.size + remoteCards.size)
        sets.forEach { set ->
            val mainTopic = Topics.normalize(set.mainTopic, set.title, set.subject)
            val subTopic = Topics.subTopic(set.subTopic, set.title)
            bySet[set.id].orEmpty()
                .sortedBy { it.order }
                .forEach { question ->
                    val key = keyFor(set.id, question.id)
                    if (!seen.add(key)) return@forEach
                    cards += DeckCard(
                        key = key,
                        setId = set.id,
                        questionId = question.id,
                        mainTopic = mainTopic,
                        subTopic = subTopic,
                        setTitle = set.title,
                        subject = set.subject,
                        stemEn = question.questionEn,
                        stemMl = question.questionMl,
                        optionsEn = question.optionsEn(),
                        optionsMl = question.optionsMl(),
                        answerIndex = DeckCard.indexOfOption(question.correctOption),
                        explanationEn = question.explanationEn,
                        explanationMl = question.explanationMl
                    )
                }
        }

        remoteCards.forEach { remote ->
            val card = remoteCard(remote) ?: return@forEach
            if (seen.add(card.key)) cards += card
        }
        return cards
    }

    /** Projects one scanned card from the second database, or null when unusable. */
    fun remoteCard(remote: RemoteDeckCard): DeckCard? {
        if (!REMOTE_KEY.matches(remote.key)) return null
        if (remote.key != "scan:${remote.deckId}:${remote.cardId}") return null
        val title = remote.deckTitle.trim().ifBlank { "Scanned deck" }
        val stemEn = remote.stem.trim().ifBlank { remote.stemMl.trim() }
        val stemMl = remote.stemMl.trim()
        val answerEn = remote.answer.trim().ifBlank { remote.answerMl.trim() }
        val answerMl = remote.answerMl.trim()
        if (stemEn.isBlank() || answerEn.isBlank()) return null

        return DeckCard(
            key = remote.key,
            setId = remote.deckId,
            questionId = remote.cardId,
            mainTopic = Topics.normalize(remote.mainTopic, title, "Scanned deck"),
            subTopic = remote.subTopic.trim().ifBlank { title },
            setTitle = title,
            subject = "Scanned deck",
            stemEn = stemEn,
            stemMl = stemMl,
            optionsEn = listOf(answerEn),
            optionsMl = listOf(answerMl),
            answerIndex = 0,
            explanationEn = remote.explanation,
            explanationMl = remote.explanationMl
        )
    }

    fun isKnown(entry: DeckProgressEntity?): Boolean =
        entry != null && entry.status == DeckStatus.KNOWN

    fun learnedCount(
        cards: List<DeckCard>,
        progress: Map<String, DeckProgressEntity>
    ): Int = cards.count { isKnown(progress[it.key]) }

    fun percent(learned: Int, total: Int): Int =
        if (total <= 0) 0 else Math.round(learned * 100f / total)

    fun topics(
        cards: List<DeckCard>,
        progress: Map<String, DeckProgressEntity>
    ): List<DeckTopic> {
        val groups = LinkedHashMap<String, MutableList<DeckCard>>()
        cards.forEach { card ->
            groups.getOrPut(card.mainTopic) { mutableListOf() } += card
        }

        return groups.map { (name, topicCards) ->
            val subGroups = LinkedHashMap<String, MutableList<DeckCard>>()
            topicCards.forEach { card ->
                subGroups.getOrPut(card.subTopic) { mutableListOf() } += card
            }
            val subtopics = subGroups.map { (subName, subCards) ->
                val learned = learnedCount(subCards, progress)
                DeckSubtopic(
                    name = subName,
                    cards = subCards,
                    learned = learned,
                    total = subCards.size,
                    pct = percent(learned, subCards.size)
                )
            }.sortedBy { it.name.lowercase() }
            val learned = learnedCount(topicCards, progress)
            DeckTopic(
                name = name,
                cards = topicCards,
                subtopics = subtopics,
                learned = learned,
                total = topicCards.size,
                pct = percent(learned, topicCards.size)
            )
        }.sortedWith(
            compareBy<DeckTopic> { Topics.rank(it.name) }.thenBy { it.name.lowercase() }
        )
    }

    fun overview(
        cards: List<DeckCard>,
        progress: Map<String, DeckProgressEntity>
    ): DeckOverview {
        val learned = learnedCount(cards, progress)
        return DeckOverview(
            cards = cards.size,
            learned = learned,
            pct = percent(learned, cards.size),
            topics = topics(cards, progress)
        )
    }

    fun topic(
        cards: List<DeckCard>,
        progress: Map<String, DeckProgressEntity>,
        name: String
    ): DeckTopic? = topics(cards, progress).firstOrNull {
        it.name.equals(name.trim(), ignoreCase = true)
    }

    fun cardsFor(
        cards: List<DeckCard>,
        mainTopic: String,
        subTopic: String = ""
    ): List<DeckCard> {
        val topic = mainTopic.trim()
        val sub = subTopic.trim()
        return cards.filter { card ->
            card.mainTopic.equals(topic, ignoreCase = true) &&
                (sub.isBlank() || card.subTopic.equals(sub, ignoreCase = true))
        }
    }

    fun summary(
        cards: List<DeckCard>,
        progress: Map<String, DeckProgressEntity>,
        session: DeckSession?
    ): DeckSummary? {
        if (session == null) return null
        val byKey = cards.associateBy { it.key }
        val live = ArrayList<DeckCard>(session.keys.size)
        val seen = HashSet<String>()
        session.keys.forEach { key ->
            val card = byKey[key] ?: return@forEach
            if (seen.add(key)) live += card
        }
        if (live.isEmpty()) return null

        val last = HashMap<String, String>()
        session.actions.forEach { action ->
            if (seen.contains(action.key)) last[action.key] = action.result
        }
        val pending = live.filter { last[it.key].isNullOrBlank() }
        val gotIt = live.count { last[it.key] == DeckResult.GOT_IT }
        val missed = live.filter { last[it.key] == DeckResult.AGAIN }
        val total = live.size
        val sorted = total - pending.size
        return DeckSummary(
            session = session,
            cards = live,
            total = total,
            sorted = sorted,
            current = pending.firstOrNull(),
            position = if (pending.isEmpty()) total else minOf(total, sorted + 1),
            gotIt = gotIt,
            again = missed.size,
            missed = missed,
            recall = if (total == 0) 0 else Math.round(gotIt * 100f / total),
            complete = pending.isEmpty(),
            results = last
        )
    }

    fun continueInfo(
        cards: List<DeckCard>,
        progress: Map<String, DeckProgressEntity>,
        session: DeckSession?
    ): DeckContinueInfo? {
        val summary = summary(cards, progress, session) ?: return null
        if (summary.complete || summary.current == null) return null
        return DeckContinueInfo(
            mainTopic = summary.session.mainTopic,
            subTopic = summary.session.subTopic,
            title = summary.session.subTopic.ifBlank { summary.session.mainTopic },
            remaining = summary.total - summary.sorted,
            total = summary.total,
            sorted = summary.sorted,
            round = summary.session.round
        )
    }

    /**
     * Returns the keys for a new round in the requested scope. When [keys] is
     * supplied (the missed pile), only keys still present in the deck survive.
     */
    fun roundKeys(
        cards: List<DeckCard>,
        mainTopic: String,
        subTopic: String,
        keys: List<String>? = null,
        shuffle: Boolean = false
    ): List<String> {
        val scope = cardsFor(cards, mainTopic, subTopic)
        val available = scope.map { it.key }
        val availableSet = available.toHashSet()
        var selected = keys
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && availableSet.contains(it) }
            ?.distinct()
            .orEmpty()
        if (selected.isEmpty()) selected = available
        if (shuffle) selected = selected.shuffled()
        return selected.take(MAX_KEYS)
    }

    fun tryStart(
        cards: List<DeckCard>,
        existing: DeckSession?,
        mainTopic: String,
        subTopic: String,
        keys: List<String>? = null,
        shuffle: Boolean = false,
        round: Int = 0
    ): DeckSession? {
        val topic = mainTopic.trim()
        val sub = subTopic.trim()
        if (topic.isBlank()) return null
        val keysOut = roundKeys(cards, topic, sub, keys, shuffle)
        if (keysOut.isEmpty()) return null

        val canonicalTopic = cards.firstOrNull {
            it.mainTopic.equals(topic, ignoreCase = true) &&
                (sub.isBlank() || it.subTopic.equals(sub, ignoreCase = true))
        }?.mainTopic ?: topic
        val canonicalSub = if (sub.isBlank()) {
            ""
        } else {
            cards.firstOrNull {
                it.mainTopic.equals(topic, ignoreCase = true) &&
                    it.subTopic.equals(sub, ignoreCase = true)
            }?.subTopic ?: sub
        }
        val sameScope = existing != null &&
            existing.mainTopic.equals(canonicalTopic, ignoreCase = true) &&
            existing.subTopic.equals(canonicalSub, ignoreCase = true)
        val nextRound = if (round > 0) {
            round
        } else if (sameScope && existing != null) {
            existing.round + 1
        } else {
            1
        }
        return DeckSession(
            id = "deck_${java.util.UUID.randomUUID()}",
            mainTopic = canonicalTopic,
            subTopic = canonicalSub,
            scope = if (canonicalSub.isBlank()) "topic" else "subtopic",
            keys = keysOut,
            actions = emptyList(),
            round = nextRound,
            startedAtEpochMs = System.currentTimeMillis()
        )
    }
}
