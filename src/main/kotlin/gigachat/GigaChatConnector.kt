package gigachat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

/*
 * Константы для аутентификации GigaChat
 */
private const val URL_GIGA_CHAT_COMPLETION = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
private val MEDIA_TYPE_JSON = "application/json".toMediaType()
private const val TOKEN_EXPIRATION_DURATION = 12 * 60 * 60 * 1000

/*
 * Дата классы запроса в GigaChat
 */
data class GigaChatRequest(
    val model: String,
    val messages: List<GigaChatMessage>,
    val temperature: Float,
    val top_p: Float,
    val n: Int,
    val stream: Boolean,
    val maxTokens: Int,
    val repetition_penalty: Float,
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
    val usage: Usage ,
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
    private var  token: String = ""

    fun sendMessageToGigaChat(gigaReq: GigaChatRequest) : GigaChatResponse{
        updateToken()

        val request =
    }
}