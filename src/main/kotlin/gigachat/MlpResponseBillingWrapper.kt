package gigachat

import com.mlp.sdk.MlpResponse

data class MlpResponseBillingWrapper(
    val response: MlpResponse,
    val customBilling: Long
)