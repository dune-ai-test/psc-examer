package com.padanam.psc.data.repo

import com.padanam.psc.data.auth.AuthSession
import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.DeckProgressEntity
import com.padanam.psc.data.model.DeckStatus
import com.padanam.psc.data.model.SyncOutcome
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.prefs.SettingsRepository
import com.padanam.psc.data.remote.RemoteSnapshot
import com.padanam.psc.data.remote.ServerApi
import com.padanam.psc.data.remote.ServerException
import com.padanam.psc.data.remote.jsonDate
import com.padanam.psc.data.remote.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Two-way sync with the account workspace used by the web app. Neon remains
 * the cloud source of truth, but Room stays authoritative while offline.
 *
 * Push first? No: pull and merge first so an assigned library or a newer web
 * edit is visible, then push the union. Set timestamps decide the winner.
 */
class CloudSyncRepository(
    private val dao: AppDao,
    private val serverApi: ServerApi,
    private val settingsRepository: SettingsRepository
) {

    suspend fun sync(
        baseUrl: String,
        session: AuthSession,
        settings: AppSettings
    ): SyncOutcome {
        if (baseUrl.isBlank()) {
            return SyncOutcome(false, "Add the address of your Padanam site first.")
        }
        if (session.token.isBlank()) {
            return SyncOutcome(false, "Sign in to sync your account.")
        }

        return try {
            val remote = serverApi.pull(baseUrl, session.token)
            val pulled = applyRemote(remote)

            val current = settingsRepository.current()
            val sets = dao.setsOnce()
            val questionsBySet = dao.allQuestionsOnce().groupBy { it.setId }
            val attempts = dao.allAttemptsOnce()
            val deckProgress = dao.deckProgressOnce()
            val pushProfileAt = current.profileUpdatedAt.takeIf { it > 0L }
                ?: System.currentTimeMillis().also { stamp ->
                    settingsRepository.applyRemoteProfile(
                        name = current.name,
                        target = current.target,
                        examDate = current.examDate,
                        language = current.language,
                        dailyGoal = current.dailyGoal,
                        updatedAtMs = stamp
                    )
                }

            val payload = buildJsonObject {
                put("action", "push")
                put("sets", buildJsonArray {
                    sets.forEach { set ->
                        add(WebBackup.setToJson(set, questionsBySet[set.id].orEmpty()))
                    }
                })
                put("attempts", buildJsonArray {
                    attempts.forEach { attempt ->
                        add(WebBackup.attemptToJson(attempt, current.language))
                    }
                })
                put("deckProgress", buildJsonArray {
                    deckProgress.forEach { entry ->
                        add(WebBackup.deckProgressToJson(entry))
                    }
                })
                put("profile", buildJsonObject {
                    put("name", current.name)
                    put("target", current.target)
                    put("examDate", current.examDate)
                    put("dailyGoal", current.dailyGoal)
                    put("language", current.language)
                    put("reminders", current.reminders)
                })
                put("profileUpdatedAt", jsonDate(pushProfileAt))
                put("attemptsClearedAt", "")
                put("tombstones", buildJsonArray {})
            }

            val pushed = serverApi.push(baseUrl, session.token, payload)
            dao.markAllSetsSynced()
            dao.markAllQuestionsSynced()
            dao.markAllAttemptsSynced()
            dao.markAllDeckProgressSynced()
            val message = buildString {
                append("Synced ")
                append(sets.size)
                append(if (sets.size == 1) " set" else " sets")
                append(" · ")
                append(attempts.size)
                append(if (attempts.size == 1) " attempt" else " attempts")
                append(" · ")
                append(deckProgress.size)
                append(if (deckProgress.size == 1) " deck card" else " deck cards")
                if (pulled > 0) {
                    append(" · pulled ")
                    append(pulled)
                    append(" new")
                }
            }
            SyncOutcome(
                ok = true,
                message = message,
                pushed = pushed.sets + pushed.attempts + pushed.deckCards,
                pulled = pulled
            )
        } catch (error: ServerException) {
            SyncOutcome(false, error.message ?: "Cloud sync failed.")
        } catch (error: Exception) {
            SyncOutcome(false, error.message ?: "Cloud sync failed.")
        }
    }

    private suspend fun applyRemote(remote: RemoteSnapshot): Int {
        var pulled = 0
        val localSets = dao.setsOnce().associateBy { it.id }
        remote.sets.forEach { payload ->
            val parsed = WebBackup.parseSet(payload) ?: return@forEach
            val incoming = parsed.first
            val existing = localSets[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                dao.replaceSetWithQuestions(incoming, parsed.second)
                pulled += 1
            }
        }

        val knownAttempts = dao.allAttemptsOnce().map { it.id }.toHashSet()
        val incomingAttempts = remote.attempts.mapNotNull { WebBackup.parseAttempt(it) }
            .filter { knownAttempts.add(it.id) }
        if (incomingAttempts.isNotEmpty()) {
            dao.insertRemoteAttempts(incomingAttempts)
            pulled += incomingAttempts.size
        }

        pulled += mergeDeckProgress(remote.deckProgress)

        val current = settingsRepository.current()
        val profile = remote.profile
        if (remote.profileUpdatedMs > current.profileUpdatedAt && profile != null) {
            val goal = (profile["dailyGoal"] as? JsonPrimitive)?.intOrNull
            settingsRepository.applyRemoteProfile(
                name = profile.string("name").takeIf { it.isNotBlank() },
                target = profile.string("target").ifBlank { "Kerala PSC" },
                examDate = profile.string("examDate"),
                language = profile.string("language").takeIf { it.isNotBlank() },
                dailyGoal = goal,
                updatedAtMs = remote.profileUpdatedMs
            )
        }
        return pulled
    }

    /**
     * Deck progress merges by timestamp, never by arrival order. An `unseen`
     * marker wins an equal-timestamp tie so an undo on either device cannot be
     * resurrected by an older "known" payload.
     */
    private suspend fun mergeDeckProgress(remote: List<JsonObject>): Int {
        if (remote.isEmpty()) return 0
        val local = dao.deckProgressOnce().associateBy { it.cardKey }
        val incoming = ArrayList<DeckProgressEntity>()
        remote.forEach { payload ->
            val entry = WebBackup.parseDeckProgress(payload) ?: return@forEach
            val existing = local[entry.cardKey]
            if (existing == null) {
                incoming += entry
                return@forEach
            }
            val newer = entry.updatedAt > existing.updatedAt ||
                (entry.updatedAt == existing.updatedAt && entry.status == DeckStatus.UNSEEN)
            if (!newer) return@forEach
            if (existing.updatedAt == entry.updatedAt &&
                existing.status == entry.status &&
                existing.lastResult == entry.lastResult
            ) {
                return@forEach
            }
            incoming += entry
        }
        if (incoming.isEmpty()) return 0
        val distinct = incoming.associateBy { it.cardKey }.values.toList()
        dao.upsertDeckProgress(distinct)
        return distinct.size
    }
}
