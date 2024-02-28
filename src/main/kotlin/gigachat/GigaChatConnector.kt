package gigachat

import com.mlp.sdk.utils.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/*
 * Константы для аутентификации GigaChat
 */
private const val URL_GIGA_CHAT_COMPLETION = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"
private val MEDIA_TYPE_JSON = "application/json".toMediaType()
private const val TOKEN_EXPIRATION_DURATION = 30 * 60 * 1000
var CERT_PATH = ""

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
    private var tokenExpirationTime: Long = 0L
    private var bearerToken: String = ""

    private val client = configureSSLClient()
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

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }
        return JSON.parse(response.body!!.string(), GigaChatResponse::class.java)
    }

    /*
     * Обновление BearerToken
     */
    private fun updateBearerToken() {
        if (tokenExpirationTime >= System.currentTimeMillis() || bearerToken.isEmpty()) {
            val newToken = getNewBearerToken()
            bearerToken = newToken
            tokenExpirationTime = System.currentTimeMillis() + TOKEN_EXPIRATION_DURATION
        }
    }

    /*
     * Получение нового BearerToken
     */
    private fun getNewBearerToken(): String {
        val formBody = FormBody.Builder()
            .add("scope", initConfig.scope)
            .build()

        val request = Request.Builder()
            .url(initConfig.baseUri)
            .post(formBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Accept", "application/json")
            .addHeader("RqUID", UUID.randomUUID().toString())
            .addHeader("Authorization", "Basic ${initConfig.clientSecret}")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }
        val responseData = response.body!!.string()
        return JSON.parse(responseData)["access_token"]!!.asText()
    }

    /*
     *  Настройка клиента OkHttpClient
     * для установки SSL-соединения с сервером
     */
    private fun configureSSLClient(): OkHttpClient {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            FileInputStream(CERT_PATH).use { inputStream ->
                setCertificateEntry(
                    "certificateAlias",
                    CertificateFactory.getInstance("X.509").generateCertificate(inputStream)
                )
            }
        }
        trustManagerFactory.init(trustStore)

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }

        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManagerFactory.trustManagers.first() as X509TrustManager)
            .build()
        return client
    }

    fun sendMessageToGigaChatAsync(gigaReq: GigaChatRequest): Flow<GigaChatResponse> = flow {
        updateBearerToken()

        val client = configureSSLClient()

        val request = Request.Builder()
            .url(URL_GIGA_CHAT_COMPLETION)
            .header("Authorization", "Bearer $bearerToken")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(JSON.stringify(gigaReq).toRequestBody(MEDIA_TYPE_JSON))
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }
        val gigaChatResponse = JSON.parse<GigaChatResponse>(response.body!!.string())
        emit(gigaChatResponse)
    }.flowOn(Dispatchers.IO)
}

