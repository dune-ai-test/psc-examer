package com.padanam.psc.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {

    @Headers("Content-Type: application/json")
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String
    ): GeminiModelList
}
