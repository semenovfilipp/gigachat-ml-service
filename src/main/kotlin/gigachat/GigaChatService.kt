package gigachat

import com.fasterxml.jackson.databind.ObjectMapper
import com.mlp.gate.PartialPredictResponseProto
import com.mlp.gate.PayloadProto
import com.mlp.gate.ServiceDescriptorProto
import com.mlp.gate.ServiceToGateProto
import com.mlp.sdk.MlpPartialBinaryResponse
import com.mlp.sdk.MlpService
import com.mlp.sdk.MlpServiceSDK
import com.mlp.sdk.Payload
import com.mlp.sdk.datatypes.chatgpt.*
import com.mlp.sdk.datatypes.chatgpt.Usage
import com.mlp.sdk.utils.JSON
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.MDC
import kotlin.io.path.Path
import java.util.concurrent.Executors




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

    val model: String = "GigaChat",
    // GigaChat, GigaChat:latest, GigaChat-Plus, GigaChat-Pro

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

    private val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    lateinit var sdk: MlpServiceSDK
    var connectorId: Long? = null
    var requestId: Long? = null


    override fun predict(req: Payload, conf: Payload?): MlpPartialBinaryResponse {
        val request = JSON.parse(req.data, ChatCompletionRequest::class.java)
        val config = conf?.data?.let { JSON.parse(it, PredictConfig::class.java) }
        val gigaChatRequest = createGigaChatRequest(request, config)

        requestId = MDC.get("gateRequestId").toLong()
        connectorId = MDC.get("connectorId").toLong()

        runBlocking {
            connector.sendMessageToGigaChatAsync(gigaChatRequest) { gigaChatReponse ->
                val chatCompletionResponse = createChatCompletionResponseAsync(gigaChatReponse)
                val partitionProto = createPartialResponse(chatCompletionResponse)

                println()
                println("__________________________")
                println(partitionProto)
                println("__________________________")
                println()

                launch {
                    sdk.send(connectorId!!, partitionProto)
                }
            }
        }
        return MlpPartialBinaryResponse()
    }


//        val responseWrapper = runBlocking(dispatcher + MDCContext()) {
//            {
//                doPredict(req, conf)
//            }
//        }
//        BillingUnitsThreadLocal.setUnits(responseWrapper.customBilling)
//        return responseWrapper.response


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
            .putHeaders("Z-custom-billing", "priceInNanoToken")
            .build()
    }


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

        val gigaChatRequest = GigaChatRequest(
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
        return gigaChatRequest
    }


    companion object {
        val REQUEST_EXAMPLE = ChatCompletionRequest(
            messages = listOf(
                ChatMessage(ChatCompletionRole.user, "Hello")

            )
        )
        val RESPONSE_EXAMPLE = ChatCompletionResult(
            model = "GigaChat",
            choices = listOf(
                ChatCompletionChoice(
                    message = ChatMessage(
                        role = ChatCompletionRole.assistant,
                        content = "Hll"
                    ),
                    index = 11
                )
            ),
        )
        val PREDICT_CONFIG_EXAMPLE = PredictConfig(
            systemPrompt = "Верни ответ без гласных",
            maxTokens = 2000,
            temperature = 0.7,
            stream = false,
        )
    }

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
/*
 * Итоговый подсчет
 */
//
//        val priceRubPerMillion =
//                    if (request.model == "GigaChat-Pro") 1500       /*GigaChat Lite*/
//                    else 200                                        /*GigaChat Pro*/
//
//        val totalTokens = (resultResponse.usage.total_tokens).toLong()
//
//        val priceInMicroRoubles = totalTokens * priceRubPerMillion
//        val priceInNanoTokens = priceInMicroRoubles * 50 * 1000
//
//        BillingUnitsThreadLocal.setUnits(priceInNanoTokens)