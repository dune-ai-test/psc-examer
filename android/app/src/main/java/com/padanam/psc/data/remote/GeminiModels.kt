package com.padanam.psc.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent> = emptyList(),
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList()
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = 0.15,
    val maxOutputTokens: Int? = 16384,
    val responseMimeType: String? = null,
    val responseSchema: GeminiSchema? = null
)

@Serializable
data class GeminiSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null,
    val items: GeminiSchema? = null,
    val enum: List<String>? = null,
    val format: String? = null,
    val nullable: Boolean? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null
)

@Serializable
data class GeminiErrorEnvelope(
    val error: GeminiApiError? = null
)

@Serializable
data class GeminiApiError(
    @SerialName("code") val code: Int? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("status") val status: String? = null
)

@Serializable
data class GeminiModelList(
    val models: List<GeminiModel> = emptyList()
)

@Serializable
data class GeminiModel(
    val name: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("supportedGenerationMethods") val methods: List<String> = emptyList()
)
