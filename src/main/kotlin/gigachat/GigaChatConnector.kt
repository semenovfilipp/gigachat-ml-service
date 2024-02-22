package gigachat

import okhttp3.OkHttpClient

private const val SCOPE = "GIGACHAT_API_PERS"
private const val URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"

class GigaChatConnector(val initConfig: InitConfig) {
    private val httpClient = OkHttpClient()

    private var tokenExpirationTime: Long = 0L
    private var  token: String = ""

    fun sendMessageToGigaChat(gigaReq: GigaChatRequest) : GigaChatResponse{
        updateToken()

        val request =
    }
}