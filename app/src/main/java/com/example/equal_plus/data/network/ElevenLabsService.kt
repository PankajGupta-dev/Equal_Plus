package com.example.equal_plus.data.network

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

data class VoiceSettings(
    @SerializedName("stability") val stability: Float = 0.5f,
    @SerializedName("similarity_boost") val similarityBoost: Float = 0.8f,
    @SerializedName("style") val style: Float = 0.0f,
    @SerializedName("use_speaker_boost") val useSpeakerBoost: Boolean = true
)

data class ElevenLabsTtsRequest(
    @SerializedName("text") val text: String,
    @SerializedName("model_id") val modelId: String = ElevenLabsConfig.DEFAULT_MODEL_ID,
    @SerializedName("voice_settings") val voiceSettings: VoiceSettings = VoiceSettings()
)

interface ElevenLabsService {

    /**
     * Synthesize text to speech audio stream using ElevenLabs API.
     */
    @Streaming
    @Headers("Content-Type: application/json", "Accept: audio/mpeg")
    @POST("v1/text-to-speech/{voice_id}/stream")
    suspend fun textToSpeechStream(
        @Path("voice_id") voiceId: String = ElevenLabsConfig.DEFAULT_VOICE_ID,
        @Header(ElevenLabsConfig.HEADER_API_KEY) apiKey: String = ElevenLabsConfig.API_KEY,
        @Body request: ElevenLabsTtsRequest
    ): Response<ResponseBody>

    companion object {
        fun create(apiKey: String = ElevenLabsConfig.API_KEY): ElevenLabsService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header(ElevenLabsConfig.HEADER_API_KEY, apiKey)
                        .build()
                    chain.proceed(req)
                }
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(ElevenLabsConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ElevenLabsService::class.java)
        }
    }
}
