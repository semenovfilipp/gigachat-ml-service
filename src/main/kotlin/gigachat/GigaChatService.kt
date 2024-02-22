package gigachat

import com.mlp.sdk.MlpExecutionContext
import com.mlp.sdk.MlpPredictWithConfigServiceBase
import com.mlp.sdk.MlpServiceSDK
import com.mlp.sdk.datatypes.chatgpt.*
import com.mlp.sdk.utils.JSON

/*
 * Сервисные конфигурации для доступа к YandexGPT
 */

data class InitConfig(val clientId: String,
                      val clientSecret: String,
                      val RqUID: String)

/*
 * Опциональные конфигурации для настройки запроса к YandexGPT
 */
data class PredictConfig(
    val systemPrompt: String? = null,

    val maxTokens: Int = 2000,
    val temperature: Double = 0.6,
    val stream: Boolean = false
)



class GigaChatService(override val context: MlpExecutionContext)
    : MlpPredictWithConfigServiceBase<ChatCompletionRequest,PredictConfig,ChatCompletionResult>
    (REQUEST_EXAMPLE, PREDICT_CONFIG_EXAMPLE, RESPONSE_EXAMPLE)
{
    private val initConfig = JSON.parse(System.getenv()["SERVICE_CONFIG"] ?: "{}", InitConfig::class.java)
    private val defaultPredictConfig = PredictConfig()

    private val connector = GigaChatConnector(initConfig)

    override fun predict(request: ChatCompletionRequest, config: PredictConfig?): ChatCompletionResult {
        TODO("Not yet implemented")
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


fun main(){
    val actionSDK = MlpServiceSDK()

    actionSDK.st
}