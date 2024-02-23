package gigachat

import com.mlp.sdk.BillingUnitsThreadLocal
import com.mlp.sdk.MlpExecutionContext
import com.mlp.sdk.MlpPredictWithConfigServiceBase
import com.mlp.sdk.MlpServiceSDK
import com.mlp.sdk.datatypes.chatgpt.*
import com.mlp.sdk.utils.JSON
import java.nio.file.Paths
import kotlin.io.path.Path

/*
 * Сервисные конфигурации для доступа к GigaChat
 */

data class InitConfig(
    val baseUri: String,
//    val clientId: String,
    val clientSecret: String,
//    val RqUID: String,
    val scope: String
)

/*
 * Опциональные конфигурации для настройки запроса к GigaChat
 */
data class PredictConfig(
    val systemPrompt: String? = null,

    val model : String = "GigaChat",
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
        config?.systemPrompt?.let { systemPrompt ->
            messages.add(
                GigaChatMessage(
                    role = "system",
                    content = systemPrompt
                )
            )
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
            model = request.model ?: defaultPredictConfig.model,
            messages = messages,
            temperature = request.temperature ?: defaultPredictConfig.temperature,
            top_p = request.topP ?: defaultPredictConfig.top_p,
            n = request.n ?: defaultPredictConfig.n,
            stream = defaultPredictConfig.stream,
            maxTokens = request.maxTokens ?: defaultPredictConfig.maxTokens,
            repetition_penalty = request.frequencyPenalty ?: defaultPredictConfig.repetition_penalty,
            update_interval = request.presencePenalty?.toInt() ?: defaultPredictConfig.update_interval
        )
        val resultResponse = connector.sendMessageToGigaChat(gigaChatRequest)

        val totalTokens = resultResponse.usage.total_tokens
        val totalCost = (totalTokens * (0.2 / 1000)).toLong()
        BillingUnitsThreadLocal.setUnits(totalCost)


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

    companion object {
        val REQUEST_EXAMPLE = ChatCompletionRequest(
            messages = listOf(
                ChatMessage(ChatCompletionRole.user, "What is Kotlin")

            )
        )
        val RESPONSE_EXAMPLE = ChatCompletionResult(
            model = "yandex-gpt-lite",
            choices = listOf(
                ChatCompletionChoice(
                    message = ChatMessage(
                        role = ChatCompletionRole.assistant,
                        content = "Kotlin is an island"
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
    val actionSDK = MlpServiceSDK({ GigaChatService(MlpExecutionContext.systemContext) })

    val currentDir = System.getProperty("user.dir")
    val certRootPath = Path("$currentDir/cert/russian_trusted_root_ca_pem.crt")
    val certSubPath = Path("$currentDir/cert/russian_trusted_sub_ca_pem.crt")

    System.setProperty("javax.net.ssl.trustStore", certRootPath.toString())


    actionSDK.start()
    actionSDK.blockUntilShutdown()
}