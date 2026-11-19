package com.padanam.psc.ui.setdetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.repo.QuestionDraft
import com.padanam.psc.ui.components.AppTextField
import com.padanam.psc.ui.components.ChoiceChip
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.InkMuted
import com.padanam.psc.ui.theme.Navy
import com.padanam.psc.ui.theme.SurfaceWhite

/**
 * Administrator editor for one bilingual question. Existing questions keep
 * their id and order; new questions are appended by the repository. Six option
 * slots are always kept so changing the visible count never loses text.
 */
@Composable
fun QuestionEditorDialog(
    setId: String,
    question: QuestionEntity?,
    onDismiss: () -> Unit,
    onSave: (QuestionDraft) -> Unit,
    onDelete: (String) -> Unit
) {
    val key = question?.id
    var questionEn by remember(key) { mutableStateOf(question?.questionEn.orEmpty()) }
    var questionMl by remember(key) { mutableStateOf(question?.questionMl.orEmpty()) }
    var optionCount by remember(key) { mutableStateOf(question?.optionCount?.coerceIn(2, 6) ?: 4) }
    var correctOption by remember(key) { mutableStateOf(question?.correctOption?.uppercase() ?: "A") }
    var topic by remember(key) { mutableStateOf(question?.topic ?: "General") }
    var difficulty by remember(key) { mutableStateOf(question?.difficulty ?: "medium") }
    var explanationEn by remember(key) { mutableStateOf(question?.explanationEn.orEmpty()) }
    var explanationMl by remember(key) { mutableStateOf(question?.explanationMl.orEmpty()) }
    var optionsEn by remember(key) {
        val base = question?.optionsEn().orEmpty()
        mutableStateOf(List(6) { base.getOrElse(it) { "" } })
    }
    var optionsMl by remember(key) {
        val base = question?.optionsMl().orEmpty()
        mutableStateOf(List(6) { base.getOrElse(it) { "" } })
    }
    var error by remember(key) { mutableStateOf("") }

    fun updateOptionEn(index: Int, value: String) {
        optionsEn = optionsEn.toMutableList().also { it[index] = value }
    }

    fun updateOptionMl(index: Int, value: String) {
        optionsMl = optionsMl.toMutableList().also { it[index] = value }
    }

    val letters = ('A' until 'A' + optionCount).map { it.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (question == null) "Add question" else "Edit question",
                style = AppType.Question,
                color = Navy
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    label = "Question · English",
                    value = questionEn,
                    onValueChange = { questionEn = it },
                    placeholder = "Which organelle produces ATP?",
                    singleLine = false,
                    minLines = 2
                )
                AppTextField(
                    label = "Question · Malayalam",
                    value = questionMl,
                    onValueChange = { questionMl = it },
                    placeholder = "ഏത് അവയവമാണ് ATP ഉൽപ്പാദിപ്പിക്കുന്നത്?",
                    singleLine = false,
                    minLines = 2
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "OPTIONS",
                        style = AppType.MonoSmall,
                        color = InkMuted
                    )
                    (2..6).forEach { count ->
                        ChoiceChip(
                            text = "$count",
                            selected = optionCount == count,
                            onClick = {
                                optionCount = count
                                val current = correctOption.firstOrNull()
                                if (current == null || current !in ('A' until 'A' + count)) {
                                    correctOption = "A"
                                }
                            }
                        )
                    }
                }

                repeat(optionCount) { index ->
                    AppTextField(
                        label = "Option ${('A' + index)} · English",
                        value = optionsEn.getOrElse(index) { "" },
                        onValueChange = { updateOptionEn(index, it) },
                        singleLine = true
                    )
                    AppTextField(
                        label = "Option ${('A' + index)} · Malayalam",
                        value = optionsMl.getOrElse(index) { "" },
                        onValueChange = { updateOptionMl(index, it) },
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("CORRECT", style = AppType.MonoSmall, color = InkMuted)
                    letters.forEach { letter ->
                        ChoiceChip(
                            text = letter,
                            selected = correctOption == letter,
                            onClick = { correctOption = letter }
                        )
                    }
                }

                AppTextField(
                    label = "Topic",
                    value = topic,
                    onValueChange = { topic = it.take(60) },
                    placeholder = "General",
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("LEVEL", style = AppType.MonoSmall, color = InkMuted)
                    listOf("easy", "medium", "hard").forEach { level ->
                        ChoiceChip(
                            text = level,
                            selected = difficulty == level,
                            onClick = { difficulty = level }
                        )
                    }
                }

                AppTextField(
                    label = "Explanation · English",
                    value = explanationEn,
                    onValueChange = { explanationEn = it },
                    singleLine = false,
                    minLines = 2
                )
                AppTextField(
                    label = "Explanation · Malayalam",
                    value = explanationMl,
                    onValueChange = { explanationMl = it },
                    singleLine = false,
                    minLines = 2
                )

                if (error.isNotBlank()) {
                    Text(error, style = AppType.BodyTiny, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val draft = QuestionDraft(
                        setId = setId,
                        questionId = question?.id,
                        questionEn = questionEn,
                        questionMl = questionMl,
                        optionsEn = optionsEn,
                        optionsMl = optionsMl,
                        optionCount = optionCount,
                        correctOption = correctOption,
                        explanationEn = explanationEn,
                        explanationMl = explanationMl,
                        topic = topic,
                        difficulty = difficulty
                    )
                    val problem = draft.validationError()
                    if (problem != null) {
                        error = problem
                    } else {
                        error = ""
                        onSave(draft)
                    }
                }
            ) {
                Text("Save", style = AppType.ButtonSmall, color = Indigo)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (question != null) {
                    TextButton(onClick = { onDelete(question.id) }) {
                        Text("Delete", style = AppType.ButtonSmall, color = Danger)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = AppType.ButtonSmall, color = InkMuted)
                }
            }
        }
    )
}
