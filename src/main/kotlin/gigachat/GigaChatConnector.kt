package gigachat

import com.mlp.sdk.utils.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import io.ktor.client.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.accept
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json




/*
 * Константы для аутентификации GigaChat
 */
private const val URL_GIGA_CHAT_COMPLETION = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"
private val MEDIA_TYPE_JSON = "application/json".toMediaType()
private const val TOKEN_EXPIRATION_DURATION = 30 * 60 * 1000
var CERT_PATH = ""
var isFirstMessage = false
var isLastMessage = false

/*
 * Дата класс для получения ответа от Athina
 */
data class AthinaApiResponse(
    val status: String?,
    val data: Data?
)

data class Data(
    val prompt_run_id: String
)

/*
 * Дата класс для запроса логирования Athina синхронный
 */
data class AthinaApiRequestSync(
    val language_model_id: String,
    val prompt: Prompt,
    val response: GigaChatResponse
)

data class Prompt(
    val role: String,
    val content: String
)

/*
 * Дата класс для запроса логирования Athina Асинхронный
 */
data class AthinaApiRequestAsync(
    val language_model_id: String,
    val prompt: Prompt,
    val response: GigaChatResponseAsync
)

/*
 Дата классы для подсчета токенов при запросе
 */
data class GigaChatTokenCountRequest(
    val model: String,
    val input: String
)

data class GigaChatTokenCountResponse(
    val tokens: Int,
    val `object`: String,
    val characters: Int
)

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
 * Дата классы ответа от GigaChat синхронные (stream = false)
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

/*
 * Дата классы запроса в GigaChat асинхронные (stream=true)
 */
data class GigaChatResponseAsync(
    val choices: List<Choices>,
    val created: Long,
    val model: String,
    val `object`: String

)

data class Delta(
    val content: String,
    val role: String?
)

data class Choices(
    val delta: Delta,
    val index: Int
)


class GigaChatConnector(val initConfig: InitConfig) {
    private var tokenExpirationTime: Long = 0L
    private var bearerToken: String = ""

    private val client = configureSSLClient()
    private val athinaClient = OkHttpClient()
    private var count: Int = 0

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

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManagerFactory.trustManagers.first() as X509TrustManager)
            .build()
    }


    /*
     * Отправление запроса на сервер GigaChat
     * Получение потокового ответа
     * Далее перенаправление в predict метод для обработки
     */
    suspend fun sendMessageToGigaChatAsync(
        gigaReq: GigaChatRequest,
        callback: (GigaChatResponseAsync) -> Unit
    ) {
        updateBearerToken()
        val emptyResponse = GigaChatResponseAsync(
            choices = listOf(
                Choices(
                    delta = Delta(
                        content = "",
                        role = "assistant"
                    ),
                    index = 0
                )
            ),
            model = gigaReq.model,
            `object` = "chat.completion",
            created = System.currentTimeMillis()
        )

        withContext(Dispatchers.IO) {
            val client = configureSSLClient()

            val request = Request.Builder()
                .url(URL_GIGA_CHAT_COMPLETION)
                .header("Authorization", "Bearer $bearerToken")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(JSON.stringify(gigaReq).toRequestBody(MEDIA_TYPE_JSON))
                .build()

            try {
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.invoke(emptyResponse)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val reader = BufferedReader(response.body!!.charStream())
                        var line: String?
                        try {
                            while (reader.readLine().also { line = it } != null) {
                                if (line!!.isNotEmpty() && !line.equals("data: [DONE]")) {
                                    val cleanResponseBody = line!!.replace("data:", "")
                                    val result = JSON.parse(cleanResponseBody, GigaChatResponseAsync::class.java)


                                    isFirstMessage = count == 0
                                    count++
                                    callback.invoke(result)

                                } else if (line!! == "data: [DONE]") {
                                    isLastMessage = true
                                    callback.invoke(emptyResponse)
                                }
                            }
                        } catch (e: IOException) {
                            throw e

                        } finally {
                            try {
                                reader.close()
                            } catch (e: IOException) {
                                throw e
                            }
                        }

                        isLastMessage = false
                        isFirstMessage = false
                    }
                })
            } catch (e: Exception) {
                callback.invoke(emptyResponse)
            }
        }
    }

    /*
     * Отправления сообщения для подсчета токенов
     */
    fun sendMessageToCountTokens(tokenCountRequest: GigaChatTokenCountRequest): GigaChatTokenCountResponse {
        updateBearerToken()

        val request = Request.Builder()
            .url("https://gigachat.devices.sberbank.ru/api/v1/tokens/count")
            .header("Authorization", "Bearer $bearerToken")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(JSON.stringify(tokenCountRequest).toRequestBody(MEDIA_TYPE_JSON))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }
        return JSON.parse(response.body!!.string(), GigaChatTokenCountResponse::class.java)
    }

    fun sendLogsInferenceToAthinaSync(
        gigaChatRequest: GigaChatRequest,
        gigaChatResponse: GigaChatResponse
    ): AthinaApiResponse {

        val body = AthinaApiRequestSync(
            language_model_id = "",
            prompt = Prompt(
                role = gigaChatRequest.messages.first().role,
                content = gigaChatRequest.messages.first().content
            ),
            response = gigaChatResponse
        )

        val request = Request.Builder()
            .url("https://log.athina.ai/api/v1/log/inference")
            .header("Content-Type", "application/json")
            .header("athina-api-key", "UMY16LUIYnMlnYKaEqhhBWs8HZiDdgA9")
            .post(JSON.stringify(body).toRequestBody(MEDIA_TYPE_JSON))
            .build()

        val response = athinaClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }
        return JSON.parse(response.body!!.string(), AthinaApiResponse::class.java)
    }


//    suspend fun sendLogsInferenceToAthinaAsync(
//        gigaChatRequest: GigaChatRequest,
//        gigaChatResponse: GigaChatResponseAsync
//    ): AthinaApiResponse {
//        val client = HttpClient(Apache)
//
//        try {
//            var body = buildJsonObject {
//                put("language_model_id", "")
//                putJsonObject("prompt") {
//                    put("role", JsonPrimitive(gigaChatRequest.messages.first().role))
//                    put("content", JsonPrimitive(gigaChatRequest.messages.first().content))
//                }
//                put("response", JsonObject(mapOf()))
//            }
//
//            val jsonString = JSON.stringify(body)
//            var response = client.post<String>("https://log.athina.ai/api/v1/log/inference") {
//                accept(ContentType.Application.Json)
//                contentType(ContentType.Application.Json)
//                header("athina-api-key", "UMY16LUIYnMlnYKaEqhhBWs8HZiDdgA9")
//                body = body.toRequestBody(MEDIA_TYPE_JSON)
////   .post(JSON.stringify(body).toRequestBody(MEDIA_TYPE_JSON))
//            }
//
//            val result = Json.decodeFromString<AthinaApiResponse>(response)
//            return result
//        } finally {
//            client.close()
//        }
//    }




//    fun sendLogsInferenceToAthinaAsync(
//        gigaChatRequest: GigaChatRequest,
//        gigaChatResponse: GigaChatResponseAsync
//    ): AthinaApiResponse {
//
//        val body = AthinaApiRequestAsync(
//            language_model_id = "",
//            prompt = Prompt(
//                role = gigaChatRequest.messages.first().role,
//                content = gigaChatRequest.messages.first().content
//            ),
//            response = gigaChatResponse
//        )
//
//        val request = Request.Builder()
//            .url("https://log.athina.ai/api/v1/log/inference")
//            .header("Content-Type", "application/json")
//            .header("athina-api-key", "UMY16LUIYnMlnYKaEqhhBWs8HZiDdgA9")
//            .post(JSON.stringify(body).toRequestBody(MEDIA_TYPE_JSON))
//            .build()
//
//        val response = athinaClient.newCall(request).execute()
//        if (!response.isSuccessful) {
//            throw IOException("Unexpected code ${response.code}")
//        }
//        return JSON.parse(response.body!!.string(), AthinaApiResponse::class.java)
//    }

//    fun sendLogsInferenceToAthinaAsync(
//        gigaChatRequest: GigaChatRequest,
//        gigaChatResponseAsync: GigaChatResponseAsync,
//        callback: (AthinaApiResponse?) -> Unit
//    ) {
//
//        val body = AthinaApiRequestAsync(
//            language_model_id = "",
//            prompt = Prompt(
//                role = gigaChatRequest.messages.first().role,
//                content = gigaChatRequest.messages.first().content
//            ),
//            response = gigaChatResponseAsync
//        )
//        val emptyResponse = AthinaApiResponse(
//            status = "error",
//            Data(
//                prompt_run_id = "id = null"
//            )
//        )
//
//        val request = Request.Builder()
//            .url("https://log.athina.ai/api/v1/log/inference")
//            .header("Content-Type", "application/json")
//            .header("athina-api-key", "UMY16LUIYnMlnYKaEqhhBWs8HZiDdgA9")
//            .post(JSON.stringify(body).toRequestBody(MEDIA_TYPE_JSON))
//            .build()
//
//        athinaClient.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                println("ERROR AT ATHINA ON FAILURE")
//                callback(emptyResponse)
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                if (response.isSuccessful) {
//                    val result = JSON.parse(response.body!!.string(), AthinaApiResponse::class.java)
//                    callback(result)
//                } else {
//                    println("ОШИБКА В ПОЛУЧЕНИИ ON_RESPONSE ${response.code}")
//                    callback(emptyResponse)
//                }
//                response.close()
//            }
//        })
//    }
//

//    fun sendLogsInferenceToAthinaAsync(
//        gigaChatRequest: GigaChatRequest,
//        gigaChatResponseAsync: GigaChatResponseAsync
//    ): Deferred<AthinaApiResponse> {
//        val athinaClient = OkHttpClient()
//
//        val body = AthinaApiRequestAsync(
//            language_model_id = "",
//            prompt = Prompt(
//                role = gigaChatRequest.messages.first().role,
//                content = gigaChatRequest.messages.first().content
//            ),
//            response = gigaChatResponseAsync
//        )
//
//        val request = Request.Builder()
//            .url("https://log.athina.ai/api/v1/log/inference")
//            .header("Content-Type", "application/json")
//            .header("athina-api-key", "UMY16LUIYnMlnYKaEqhhBWs8HZiDdgA9")
//            .post(JSON.stringify(body).toRequestBody(MEDIA_TYPE_JSON))
//            .build()
//
//        val deferred = CompletableDeferred<AthinaApiResponse>()
//
//        athinaClient.newCall(request).enqueue(object : Callback {
//            override fun onResponse(call: Call, response: Response) {
//                try {
//                    if (response.isSuccessful) {
//                        val result = JSON.parse(response.body!!.string(), AthinaApiResponse::class.java)
//
//                        println()
//                        println("__________________________")
//                        println(result)
//                        println("__________________________")
//                        println()
//
//                        deferred.complete(result)
//                    } else {
//                        deferred.completeExceptionally(IOException("Unexpected code ${response.code}"))
//                    }
//                } finally {
//                    response.close()
//                }
//            }
//
//
//            override fun onFailure(call: Call, e: IOException) {
//                deferred.completeExceptionally(e)
//            }
//        })
//
//        return deferred
//    }


//     fun sendLogsInferenceToAthinaAsync(
//        gigaChatRequest: GigaChatRequest,
//        gigaChatResponseAsync: GigaChatResponseAsync
//    ): AthinaApiResponse  {
//        val athinaClient = OkHttpClient()
//
//        val body = AthinaApiRequestAsync(
//            language_model_id = "",
//            prompt = Prompt(
//                role = gigaChatRequest.messages.first().role ?: "",
//                content = gigaChatRequest.messages.first().content ?: ""
//            ),
//            response = gigaChatResponseAsync
//        )
//
//        val request = Request.Builder()
//            .url("https://log.athina.ai/api/v1/log/inference")
//            .header("Content-Type", "application/json")
//            .header("athina-api-key", "UMY16LUIYnMlnYKaEqhhBWs8HZiDdgA9")
//            .post(JSON.stringify(body).toRequestBody(MEDIA_TYPE_JSON))
//            .build()
//
//        try {
//            val response = athinaClient.newCall(request).execute()
//            if (!response.isSuccessful) {
//                throw IOException("Unexpected code ${response.code}")
//            }
//            return JSON.parse(response.body!!.string(), AthinaApiResponse::class.java)
//        } catch (e: IOException) {
//            println("ОШИБКА В ПОЛУЧЕНИИ ОТВЕТА ОТ ATHINA")
//
//            throw Exception("Error while making HTTP request: ${e.message}", e)
//        }
//    }



}