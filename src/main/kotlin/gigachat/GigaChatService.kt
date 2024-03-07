package gigachat

import com.mlp.gate.PartialPredictResponseProto
import com.mlp.gate.PayloadProto
import com.mlp.gate.ServiceDescriptorProto
import com.mlp.gate.ServiceToGateProto
import com.mlp.sdk.*
import com.mlp.sdk.datatypes.chatgpt.*
import com.mlp.sdk.datatypes.chatgpt.Usage
import com.mlp.sdk.utils.JSON
import kotlinx.coroutines.*
import org.slf4j.MDC
import kotlin.io.path.Path
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


/*
 * Сервисные конфигурации для доступа к GigaChat
 */

data class InitConfig(
    val baseUri: String,
    val clientSecret: String,
    val scope: String
)

/*
 * Опциональные конфигурации для настройки запроса к GigaChat
 */
data class PredictConfig(
    val systemPrompt: String? = null,

    // GigaChat, GigaChat:latest, GigaChat-Plus, GigaChat-Pro
    val model: String = "GigaChat",

    val temperature: Double = 1.0,
    val top_p: Double = 0.1,
    val n: Int = 1,
    val stream: Boolean = false,
    val maxTokens: Int = 1024,
    val repetition_penalty: Double = 1.0,
    val update_interval: Int = 0
)


class GigaChatService : MlpService() {

    private val initConfig = JSON.parse(System.getenv()["SERVICE_CONFIG"] ?: "{}", InitConfig::class.java)
    private val defaultPredictConfig = PredictConfig()
    private val connector = GigaChatConnector(initConfig)

    lateinit var sdk: MlpServiceSDK
    private var connectorId: Long? = null
    private var requestId: Long? = null
    private var priceInNanoTokens: Long = 0L
    private var messages: MutableList<String> = mutableListOf()


    override fun predict(req: Payload, conf: Payload?): MlpPartialBinaryResponse {
        val request = JSON.parse(req.data, ChatCompletionRequest::class.java)
        val config = conf?.data?.let { JSON.parse(it, PredictConfig::class.java) }
        val gigaChatRequest = createGigaChatRequest(request, config)

        requestId = MDC.get("gateRequestId").toLong()
        connectorId = MDC.get("connectorId").toLong()

        return if (gigaChatRequest.stream) {
            predictAsync(gigaChatRequest)

        } else {
            predictSync(gigaChatRequest)
        }
    }

    /*
     * Асинхронная и синхронная функции для отправки сообщений
     */
    private fun predictAsync(gigaChatRequest: GigaChatRequest): MlpPartialBinaryResponse {
        runBlocking {
            connector.sendMessageToGigaChatAsync(gigaChatRequest) { gigaChatReponse ->
                val message = gigaChatReponse.choices.first().delta.content
                messages.add(message)

                    val athina = connector.sendLogsInferenceToAthinaAsync(gigaChatRequest, gigaChatReponse){
                        athinaResponse ->
                        println()
                        println("______________________")
                        println("Отправляем логи в  Athina $athinaResponse")
                        println("______________________")
                        println()

                    }

//                val result = runBlocking {
//                    val athina = connector.sendLogsInferenceToAthinaAsync(gigaChatRequest, gigaChatReponse)
//                    println()
//                    println("__________________________")
//                    println(athina)
//                    println("__________________________")
//                    println()
//                }



                val chatCompletionResponse = createChatCompletionResponseAsync(gigaChatReponse)
                updatePrice(gigaChatRequest, gigaChatReponse, defaultPredictConfig.systemPrompt)

                val partitionProto = createPartialResponse(chatCompletionResponse)

                println()
                println("__________________________")
                println(partitionProto)
                println(athina)
                println("__________________________")
                println()

                launch {
                    sdk.send(connectorId!!, partitionProto)
                }
            }
        }
        return MlpPartialBinaryResponse()
    }


    private fun predictSync(gigaChatRequest: GigaChatRequest): MlpPartialBinaryResponse {
        val gigaChatResponse = connector.sendMessageToGigaChat(gigaChatRequest)
        calculateCostForSyncRequest(gigaChatRequest, gigaChatResponse)

        val athinaResponse = connector.sendLogsInferenceToAthinaSync(gigaChatRequest, gigaChatResponse)
        val chatCompletionResponse = createChatCompletionResponse(gigaChatResponse)
        isLastMessage = true
        val partitionProto = createPartialResponse(chatCompletionResponse)
        isLastMessage = false

        println()
        println("__________________________")
        println(partitionProto)
        println(athinaResponse)
        println("__________________________")
        println()

        GlobalScope.launch {
            sdk.send(connectorId!!, partitionProto)
            BillingUnitsThreadLocal.setUnits(priceInNanoTokens)
        }

        return MlpPartialBinaryResponse()
    }


    /*
     * Подсчет итоговой стоимости запроса
     */
    fun calculateCostForSyncRequest(request: GigaChatRequest, response: GigaChatResponse) {
        /*GigaChat Lite/latest = 200*/
        /*GigaChat Pro = 1500*/
        val priceRubPerMillion = if (request.model == "GigaChat-Pro") 1500 else 200
        val totalTokens = (response.usage.total_tokens).toLong()

        val priceInMicroRoubles = totalTokens * priceRubPerMillion
        priceInNanoTokens = priceInMicroRoubles * 50 * 1000
    }

    fun calculateCostForAsyncRequest(model: String, totalTokens: Int) {
        /*GigaChat Lite = 200*/
        /*GigaChat Pro = 1500*/
        val priceRubPerMillion = if (model == "GigaChat-Pro") 1500 else 200
        val priceInMicroRoubles = (totalTokens).toLong() * priceRubPerMillion
        priceInNanoTokens = priceInMicroRoubles * 50 * 1000
    }

    private fun updatePrice(
        gigaChatRequest: GigaChatRequest,
        gigaChatResponse: GigaChatResponseAsync,
        systemPrompt: String?
    ) {
        if (isLastMessage) {
            val inputMessageFromRequest = gigaChatRequest.messages.first().content
            val systemPrompt = systemPrompt ?: ""
            val model = gigaChatResponse.model
            val tokenCountRequest = GigaChatTokenCountRequest(
                model = model,
                input = messages.joinToString(separator = "") + inputMessageFromRequest + systemPrompt
            )
            val gigaChatTokenCountResponse = connector.sendMessageToCountTokens(tokenCountRequest)
            val totalTokens = gigaChatTokenCountResponse.tokens

            calculateCostForAsyncRequest(model, totalTokens)
            messages.clear()
        } else {
            priceInNanoTokens = 0L
        }
    }


    fun createPartialResponse(response: Any): ServiceToGateProto {

        return ServiceToGateProto.newBuilder()
            .setRequestId(requestId!!)
            .setPartialPredict(
                PartialPredictResponseProto.newBuilder()
                    .setStart(isFirstMessage)
                    .setFinish(isLastMessage)
                    .setData(
                        PayloadProto.newBuilder()
                            .setJson(JSON.stringify(response))
                            .setDataType("json")
                    )
            )
            .putHeaders("Z-custom-billing", priceInNanoTokens.toString())
            .build()
    }

    /*
    * Преобразователи из GigaChatResponse в ChatCompletionResult
    */
    private fun createChatCompletionResponseAsync(gigaChatResponse: GigaChatResponseAsync): ChatCompletionResult {
        val choices = gigaChatResponse.choices.map {
            ChatCompletionChoice(
                message = ChatMessage(
                    role = ChatCompletionRole.assistant,
                    content = it.delta.content
                ),
                index = it.index,
            )
        }

        return ChatCompletionResult(
            id = null,
            `object` = gigaChatResponse.`object`,
            created = gigaChatResponse.created,
            model = gigaChatResponse.model,
            choices = choices,
        )
    }


    private fun createChatCompletionResponse(resultResponse: GigaChatResponse): ChatCompletionResult {
        val choices = resultResponse.choices.map {
            ChatCompletionChoice(
                message = ChatMessage(
                    role = ChatCompletionRole.assistant,
                    content = it.message.content
                ),
                index = it.index,
                finishReason = ChatCompletionChoiceFinishReason.stop
            )
        }
        val usage = Usage(
            promptTokens = resultResponse.usage.prompt_tokens.toLong(),
            completionTokens = resultResponse.usage.completion_tokens.toLong(),
            totalTokens = resultResponse.usage.total_tokens.toLong(),
        )

        return ChatCompletionResult(
            id = null,
            `object` = resultResponse.`object`,
            created = resultResponse.created,
            model = resultResponse.model,
            choices = choices,
            usage = usage
        )
    }


    /*
     * Создание запроса для GigaChat
     */
    private fun createGigaChatRequest(
        request: ChatCompletionRequest,
        config: PredictConfig?
    ): GigaChatRequest {
        val messages = mutableListOf<GigaChatMessage>()
        if (request.messages.find { it.role == ChatCompletionRole.system } == null) {
            config?.systemPrompt?.let { systemPrompt ->
                messages.add(
                    GigaChatMessage(
                        role = "system",
                        content = systemPrompt
                    )
                )
            }
        }
        request.messages.forEach { message ->
            messages.add(
                GigaChatMessage(
                    role = message.role.toString(),
                    content = message.content
                )
            )
        }

        return GigaChatRequest(
            model = request.model ?: config?.model ?: defaultPredictConfig.model,
            messages = messages,
            temperature = request.temperature ?: config?.temperature ?: defaultPredictConfig.temperature,
            top_p = request.topP ?: config?.top_p ?: defaultPredictConfig.top_p,
            n = request.n ?: config?.n ?: defaultPredictConfig.n,
            stream = request.stream ?: config?.stream ?: defaultPredictConfig.stream,
            maxTokens = request.maxTokens ?: config?.maxTokens ?: defaultPredictConfig.maxTokens,
            repetition_penalty = request.frequencyPenalty ?: config?.repetition_penalty
            ?: defaultPredictConfig.repetition_penalty,
            update_interval = request.presencePenalty?.toInt() ?: config?.update_interval
            ?: defaultPredictConfig.update_interval
        )
    }


    /*
     * Необходим для успешного запуска приложения
     */
    override fun getDescriptor(): ServiceDescriptorProto {

        return ServiceDescriptorProto.newBuilder()
            .setName("GigaChat")
            .build()
    }
}


fun main() {
    val currentDir = System.getProperty("user.dir")
    CERT_PATH = Path("$currentDir/cert/russiantrustedca.pem").toString()

    val service = GigaChatService()
    val mlp = MlpServiceSDK(service)
    service.sdk = mlp

    mlp.start()
    mlp.blockUntilShutdown()
}
