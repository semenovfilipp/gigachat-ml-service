package gigachat

import com.mlp.sdk.datatypes.chatgpt.*

object examples{
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