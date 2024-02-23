package gigachat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

/*
 * Константы для аутентификации YandexGPT
 */
private const val URL_GIGA_CHAT_COMPLETION = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
private val MEDIA_TYPE_JSON = "application/json".toMediaType()
private const val TOKEN_EXPIRATION_DURATION = 12 * 60 * 60 * 1000

class GigaChatConnector(val initConfig: InitConfig) {
    private val httpClient = OkHttpClient()

    private var tokenExpirationTime: Long = 0L
    private var  token: String = ""

    fun sendMessageToGigaChat(gigaReq: GigaChatRequest) : GigaChatResponse{
        updateToken()

        val request =
    }
}