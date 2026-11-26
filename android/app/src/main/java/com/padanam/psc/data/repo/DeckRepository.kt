package com.padanam.psc.data.repo

import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.DeckProgressEntity
import com.padanam.psc.data.model.DeckAction
import com.padanam.psc.data.model.DeckCard
import com.padanam.psc.data.model.DeckProgressSnapshot
import com.padanam.psc.data.model.DeckResult
import com.padanam.psc.data.model.DeckSession
import com.padanam.psc.data.model.DeckStatus
import com.padanam.psc.data.model.Decks
import com.padanam.psc.data.prefs.DeckCardStore
import com.padanam.psc.data.prefs.DeckSessionStore
import kotlinx.coroutines.flow.first

/**
 * Deck rounds and the swipe actions that feed the account-synced card state.
 * Session position stays in DataStore; only the progress rows leave the phone.
 */
class DeckRepository(
    private val dao: AppDao,
    private val sessionStore: DeckSessionStore,
    private val cardStore: DeckCardStore
) {

    private suspend fun cardsOnce(): List<DeckCard> =
        Decks.buildCards(
            dao.setsOnce(),
            dao.allQuestionsOnce(),
            cardStore.cards.first()
        )

    private suspend fun progressOnce(): Map<String, DeckProgressEntity> =
        dao.deckProgressOnce().associateBy { it.cardKey }

    suspend fun startRound(
        mainTopic: String,
        subTopic: String,
        keys: List<String>? = null,
        shuffle: Boolean = false,
        round: Int = 0
    ): DeckSession? {
        val cards = cardsOnce()
        val session = Decks.tryStart(
            cards = cards,
            existing = sessionStore.session.first(),
            mainTopic = mainTopic,
            subTopic = subTopic,
            keys = keys,
            shuffle = shuffle,
            round = round
        ) ?: return null
        sessionStore.save(session)
        return session
    }

    /**
     * Keeps an unfinished or completed round for this topic/subtopic. A round
     * is only recreated when the deck is new or every card disappeared from
     * the library.
     */
    suspend fun ensureRound(mainTopic: String, subTopic: String): DeckSession? {
        val cards = cardsOnce()
        val existing = sessionStore.session.first()
        if (existing != null &&
            existing.mainTopic.equals(mainTopic.trim(), ignoreCase = true) &&
            existing.subTopic.equals(subTopic.trim(), ignoreCase = true)
        ) {
            val summary = Decks.summary(cards, progressOnce(), existing)
            if (summary != null && summary.total > 0) return existing
        }
        return startRound(mainTopic, subTopic)
    }

    /** Applies one swipe and returns the card that was sorted. */
    suspend fun sort(result: String): DeckCard? {
        if (result != DeckResult.GOT_IT && result != DeckResult.AGAIN) return null
        val session = sessionStore.session.first() ?: return null
        val cards = cardsOnce()
        val progress = progressOnce()
        val summary = Decks.summary(cards, progress, session) ?: return null
        if (summary.complete) return null
        val card = summary.current ?: return null

        val now = System.currentTimeMillis()
        val before = progress[card.key]?.toSnapshot()
        val status = if (result == DeckResult.GOT_IT) DeckStatus.KNOWN else DeckStatus.SEEN
        dao.upsertDeckProgress(
            DeckProgressEntity(
                cardKey = card.key,
                status = status,
                lastResult = result,
                seenAt = now,
                updatedAt = now,
                synced = false
            )
        )
        sessionStore.save(
            session.copy(
                actions = (session.actions + DeckAction(
                    key = card.key,
                    result = result,
                    atEpochMs = now,
                    before = before
                )).takeLast(Decks.MAX_ACTIONS)
            )
        )
        return card
    }

    /** Reverts the latest swipe, including the first-ever sort of a card. */
    suspend fun undo(): Boolean {
        val session = sessionStore.session.first() ?: return false
        val action = session.actions.lastOrNull() ?: return false
        val now = System.currentTimeMillis()
        val before = action.before
        val entry = if (before != null) {
            DeckProgressEntity(
                cardKey = action.key,
                status = before.status,
                lastResult = before.lastResult,
                seenAt = if (before.status == DeckStatus.UNSEEN) {
                    0L
                } else {
                    before.seenAtEpochMs.takeIf { it > 0L } ?: now
                },
                updatedAt = now,
                synced = false
            )
        } else {
            DeckProgressEntity(
                cardKey = action.key,
                status = DeckStatus.UNSEEN,
                lastResult = "",
                seenAt = 0L,
                updatedAt = now,
                synced = false
            )
        }
        dao.upsertDeckProgress(entry)
        sessionStore.save(session.copy(actions = session.actions.dropLast(1)))
        return true
    }

    suspend fun clearRound() = sessionStore.clear()
}

private fun DeckProgressEntity.toSnapshot(): DeckProgressSnapshot = DeckProgressSnapshot(
    status = status,
    lastResult = lastResult,
    seenAtEpochMs = seenAt,
    updatedAtEpochMs = updatedAt
)
