package gigachat

import com.mlp.sdk.utils.JSON
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/*
 * Константы для аутентификации GigaChat
 */
private const val URL_GIGA_CHAT_COMPLETION = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions\n"
private val MEDIA_TYPE_JSON = "application/json".toMediaType()
private const val TOKEN_EXPIRATION_DURATION = 30 * 60 * 1000

/*
 * Дата классы запроса в GigaChat
 */
data class GigaChatRequest(
    val model: String,
    val messages: List<GigaChatMessage>,
    val temperature: Double,
    val top_p: Double,
    val n: Int,
    val stream: Boolean,
    val maxTokens: Int,
    val repetition_penalty: Double,
    val update_interval: Int
)

data class GigaChatMessage(
    val role: String,
    val content: String
)

/*
 * Дата классы ответа от GigaChat
 */
data class GigaChatResponse(
    val choices: List<Choice>,
    val created: Long,
    val model: String,
    val usage: Usage,
    val `object`: String
)

data class Choice(
    val message: Message,
    val index: Int,
    val finish_reason: String
)

data class Message(
    val role: String,
    val content: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
    val system_tokens: Int
)


class GigaChatConnector(val initConfig: InitConfig) {
    private val httpClient = OkHttpClient()

    private var tokenExpirationTime: Long = 0L
    private var bearerToken: String = ""

    /*
     * Отправление запроса на сервер GigaChat
     */
    fun sendMessageToGigaChat(gigaReq: GigaChatRequest): GigaChatResponse {
        updateBearerToken()

        val request = Request.Builder()
            .url(URL_GIGA_CHAT_COMPLETION)
            .header("Authorization", "Bearer $bearerToken")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(JSON.stringify(gigaReq).toRequestBody(MEDIA_TYPE_JSON))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }
        return JSON.parse(response.body!!.toString(), GigaChatResponse::class.java)
    }

    /*
     * Обновление BearerToken
     */
    private fun updateBearerToken() {
        if (tokenExpirationTime >= System.currentTimeMillis() || bearerToken.isNullOrEmpty()) {
            val newToken = getNewBearerToken()
            bearerToken = newToken
            tokenExpirationTime = System.currentTimeMillis() + TOKEN_EXPIRATION_DURATION
        }
    }

    /*
     * Получение нового BearerToken
     */
    private fun getNewBearerToken(): String {
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val body = initConfig.scope.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(initConfig.baseUri)
            .method("POST", body)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .header("Authorization", "Basic ${initConfig.clientSecret}")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }
        val responseData = response.body!!.toString()
        return JSON.parse(responseData)["access_token"]!!.asText()
    }

}