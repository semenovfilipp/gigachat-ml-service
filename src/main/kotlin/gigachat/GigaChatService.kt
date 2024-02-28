package gigachat

import com.mlp.sdk.BillingUnitsThreadLocal
import com.mlp.sdk.MlpExecutionContext
import com.mlp.sdk.MlpPredictWithConfigServiceBase
import com.mlp.sdk.MlpServiceSDK
import com.mlp.sdk.datatypes.chatgpt.*
import com.mlp.sdk.utils.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path

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

    val model : String = "GigaChat",
            // GigaChat, GigaChat:latest, GigaChat-Plus, GigaChat-Pro

    val temperature: Double = 1.0,
    val top_p: Double = 0.1,
    val n: Int = 1,
    val stream: Boolean = false,
    val maxTokens: Int = 1024,
    val repetition_penalty: Double = 1.0,
    val update_interval: Int = 0
)


class GigaChatService(override val context: MlpExecutionContext) :
    MlpPredictWithConfigServiceBase<ChatCompletionRequest, PredictConfig, ChatCompletionResult>
        (REQUEST_EXAMPLE, PREDICT_CONFIG_EXAMPLE, RESPONSE_EXAMPLE) {

    private val initConfig = JSON.parse(System.getenv()["SERVICE_CONFIG"] ?: "{}", InitConfig::class.java)
    private val defaultPredictConfig = PredictConfig()

    private val connector = GigaChatConnector(initConfig)

    override fun predict(request: ChatCompletionRequest, config: PredictConfig?): ChatCompletionResult {
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
            temperature = request.temperature ?: config?.temperature ?:defaultPredictConfig.temperature,
            top_p = request.topP ?: config?.top_p ?: defaultPredictConfig.top_p,
            n = request.n ?: config?.n ?: defaultPredictConfig.n,
            stream = defaultPredictConfig.stream,
            maxTokens = request.maxTokens ?: config?.maxTokens ?: defaultPredictConfig.maxTokens,
            repetition_penalty = request.frequencyPenalty ?: config?.repetition_penalty ?: defaultPredictConfig.repetition_penalty,
            update_interval = request.presencePenalty?.toInt() ?: config?.update_interval ?: defaultPredictConfig.update_interval
        )

        val priceRubPerMillion =
                    if (request.model == "GigaChat-Pro") 1500       /*GigaChat Lite*/
                    else 200                                        /*GigaChat Pro*/

        val totalTokens = (resultResponse.usage.total_tokens).toLong()

        val priceInMicroRoubles = totalTokens * priceRubPerMillion
        val priceInNanoTokens = priceInMicroRoubles * 50 * 1000

        BillingUnitsThreadLocal.setUnits(priceInNanoTokens)
        return runBlocking {
            connector.sendMessageToGigaChatAsync(gigaChatRequest)
                .map { gigaChatResponse ->
                    val totalTokens = gigaChatResponse.usage.total_tokens.toLong()
                    val totalCost = (((totalTokens * 0.2 / 1000.0) * 100).toLong() / 100.0)
                    BillingUnitsThreadLocal.setUnits(totalCost.toLong())


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
            `object` = resultResponse.`object`,
            created = resultResponse.created,
            model = resultResponse.model,
            choices = choices,
            usage = usage
        )
                    val choices = gigaChatResponse.choices.map {
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
                        promptTokens = gigaChatResponse.usage.prompt_tokens.toLong(),
                        completionTokens = gigaChatResponse.usage.completion_tokens.toLong(),
                        totalTokens = gigaChatResponse.usage.total_tokens.toLong(),
                    )
                    ChatCompletionResult(
                        id = null,
                        `object` = gigaChatResponse.`object`,
                        created = gigaChatResponse.created,
                        model = gigaChatResponse.model,
                        choices = choices,
                        usage = usage
                    )
                }
                .toList()
                .first()
        }
    }


//        val resultResponse = connector.sendMessageToGigaChat(gigaChatRequest)
//
//        val totalTokens = (resultResponse.usage.total_tokens).toLong()
//        val totalCost = (((totalTokens * 0.2 / 1000.0) * 100).toLong() / 100.0)
//
//        BillingUnitsThreadLocal.setUnits(totalCost.toLong())
//
//
//        val choices = resultResponse.choices.map {
//            ChatCompletionChoice(
//                message = ChatMessage(
//                    role = ChatCompletionRole.assistant,
//                    content = it.message.content
//                ),
//                index = it.index,
//                finishReason = ChatCompletionChoiceFinishReason.stop
//            )
//        }
//        val usage = Usage(
//            promptTokens = resultResponse.usage.prompt_tokens.toLong(),
//            completionTokens = resultResponse.usage.completion_tokens.toLong(),
//            totalTokens = resultResponse.usage.total_tokens.toLong(),
//        )
//
//        return ChatCompletionResult(
//            id = null,
//            `object` = resultResponse.`object`,
//            created = resultResponse.created,
//            model = resultResponse.model,
//            choices = choices,
//            usage = usage
//        )
//    }

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
}
fun main() {
    val currentDir = System.getProperty("user.dir")
    CERT_PATH = Path("$currentDir/cert/russiantrustedca.pem").toString()

    val actionSDK = MlpServiceSDK({ GigaChatService(MlpExecutionContext.systemContext) })

    actionSDK.start()
    actionSDK.blockUntilShutdown()
}