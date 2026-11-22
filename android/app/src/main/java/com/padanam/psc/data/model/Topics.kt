package com.padanam.psc.data.model

/**
 * The fixed main-topic taxonomy shared with the web app, the Android app and
 * the Cloudflare scanner. Keep this list in sync with `web/js/data.js` and
 * `functions/api/scan.js`.
 *
 * Every set also has a free-form subtopic. Unlike the main topic, a subtopic
 * is a reusable grouping label chosen by the administrator, so sets that cover
 * the same paper family can be moved together.
 */
object Topics {

    val MAIN: List<String> = listOf(
        "Biology & Public Health",
        "Constitution",
        "Current Affairs",
        "Economics",
        "English",
        "Indian History",
        "Information Technology",
        "Kerala Governance",
        "Kerala - World Geography",
        "Kerala - World History",
        "Malayalam",
        "Mathematics & Mental Ability",
        "Physical Science",
        "Indian Geography"
    )

    /** Returns a canonical main topic, falling back to a title/subject guess. */
    fun normalize(value: String?, title: String? = null, subject: String? = null): String {
        val clean = value?.trim().orEmpty()
        if (clean.isNotEmpty()) {
            MAIN.firstOrNull { it.equals(clean, ignoreCase = true) }?.let { return it }
            val lower = clean.lowercase()
            val partial = MAIN.filter { topic ->
                val candidate = topic.lowercase()
                candidate.contains(lower) || lower.contains(candidate)
            }
            if (partial.size == 1) return partial[0]
        }
        return infer(title, subject)
    }

    fun infer(title: String?, subject: String?): String {
        val text = "${title.orEmpty()} ${subject.orEmpty()}".lowercase()
        return when {
            text.contains("geograph") ->
                if (text.contains("kerala")) "Kerala - World Geography" else "Indian Geography"
            Regex("renaissance|world history|kerala history|history of kerala").containsMatchIn(text) ->
                "Kerala - World History"
            Regex("indian history|history").containsMatchIn(text) -> "Indian History"
            Regex("constitution|polity|civics").containsMatchIn(text) -> "Constitution"
            text.contains("econom") -> "Economics"
            text.contains("english") -> "English"
            text.contains("malayalam") -> "Malayalam"
            Regex("math|mental ability|reasoning|aptitude|numerical").containsMatchIn(text) ->
                "Mathematics & Mental Ability"
            Regex("computer|information technology|\\bict\\b|internet|software").containsMatchIn(text) ->
                "Information Technology"
            Regex("biology|botany|zoology|public health|medical|nursing|health").containsMatchIn(text) ->
                "Biology & Public Health"
            Regex("physics|chemistry|science").containsMatchIn(text) -> "Physical Science"
            Regex("governance|administration|kerala").containsMatchIn(text) -> "Kerala Governance"
            else -> "Current Affairs"
        }
    }

    /** An empty subtopic falls back to the set title, then to "General". */
    fun subTopic(value: String?, title: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?: title?.trim()?.takeIf { it.isNotEmpty() }
            ?: "General"

    /** Library sort order follows the published taxonomy order. */
    fun rank(topic: String?): Int {
        val index = MAIN.indexOfFirst { it.equals(topic?.trim(), ignoreCase = true) }
        return if (index >= 0) index else MAIN.size
    }
}
