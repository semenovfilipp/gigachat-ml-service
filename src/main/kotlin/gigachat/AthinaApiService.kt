//package gigachat
//
//import retrofit2.http.Body
//import retrofit2.http.Header
//import retrofit2.http.POST
//
//interface AthinaApiService {
//    @POST("log/inference")
//    suspend fun sendLogsInferenceAsync(
//        @Header("athina-api-key") apiKey: String,
//        @Body request: AthinaApiRequestAsync
//    ): AthinaApiResponse
//}
