# GigaChat ML Service

### Оглавление
1. [Введение](#введение)
2. [Принцип работы](#принцип-работы)
3. [Создание проекта](#создание-проекта)
4. [Создаем точку входа нашего приложения](#создаем-точку-входа-нашего-приложения)
5. [Соединяем IDE с сервисом Caila](#соединяем-iDE-с-сервисом-caila)
6. [InitConfig](#initconfig)
7. [Сертификат Минцифры](#сертификат-минцифры)
8. [GigaChatConnector](#gigachatconnector)

## Введение

Это руководство представляет собой пошаговую инструкцию по созданию и настройке сервиса для взаимодействия с платформой Caila.io и использования модели GigaChat от Сбербанка для генерации текста.

### Функциональность сервиса:

- Отправка запроса из платформы Caila.io в наше приложение.
- Передача запроса из нашего приложения в GigaChat.
- Получение ответа от GigaChat в наше приложение.
- Отправка ответа из нашего приложения в Caila.io.

## Создание сервиса в Caila

- Зарегистрируйтесь или войдите в аккаунт на сервисе Caila.io.
- Перейдите в раздел "Мое пространство" в правом верхнем углу.
- Нажмите "Создать сервис".
- Выберите образ "fit-action-example-image".
- Дайте имя сервису и нажмите "Создать".
- Затем перейдите в только что созданный сервис.
- В разделе "Настройки" выберите "Хостинг" -> "Отладочное подключение" -> "Активировать".
- Сохраните переменные окружения, предоставленные в разделе "env-переменные". Мы будем использовать их позже.

---
## Принцип работы

1. Из сервиса Caila формируется JSON.
2. Caila связывается с нашим сервисом при помощи переменных окружения (env).
3. JSON отправляется в наш сервис.
4. Запрос приходит в виде объекта `ChatCompletionRequest`.
5. Мы извлекаем поля из этого объекта и передаем их в GigaChat.
6. Если нет опциональных полей, мы добавляем их из `PredictConfig`.
7. Перед запросом мы проверяем, действителен ли у нас BearerToken.
8. Если BearerToken не действителен, мы его обновляем.
9. Для формирования запроса на обновление обращаемся к сертификату Минцифры.
10. Отправляем запрос на обновление BearerToken.
11. Затем формируем запрос в GigaChat.
12. Прикрепляем сертификат Минцифры.
13. Получаем ответ от GigaChat.
14. Перекладываем поля из ответа в `ChatCompletionResponse`.
15. Отправляем ответ обратно в Caila.


____

## Создание проекта

- Создайте проект в вашей IDE. В качестве сборщика можно выбрать Gradle или Maven.

### Пример шаблона проекта на Maven

[Ссылка](https://github.com/just-ai/mlp-java-service-template) на шаблон проекта

(Вы также можете найти пример по истории коммитов и на Gradle)

- Подключите зависимость.

#### Для Maven

```xml
<dependency>
    <groupId>com.mlp</groupId>
    <artifactId>mlp-java-sdk</artifactId>
    <version>release-SNAPSHOT</version>
</dependency>
```

#### Для Gradle

```kotlin
dependencies {
    implementation 'com.mlp:mlp-java-sdk:release-SNAPSHOT'
}
```

Чтобы зависимость скачалась, добавьте репозиторий для скачивания этой зависимости.

#### Для Maven

```xml
<repositories>
    <repository>
        <id>nexus-public</id>
        <url>https://nexus-open.just-ai.com/repository/maven-public/</url>
    </repository>
</repositories>
```

#### Для Gradle

```kotlin
repositories {
    maven {
        url 'https://nexus-open.just-ai.com/repository/maven-public/'
    }
}
```

---

## Создаем точку входа нашего приложения

- Создайте класс `GigaChatService.kt`.

В этом классе мы будем наследоваться от класса `MlpPredictWithConfigServiceBase` из `mlp-sdk`.

```kotlin
class Main : MlpPredictWithConfigServiceBase<ChatCompletionRequest, PredictConfig, ChatCompletionResult>()
```

Это даст нам доступ к методу `predict`.

```kotlin
override fun predict(req: ChatCompletionRequest, config: PredictConfig?): ChatCompletionResult 
```

Метод `predict` будет получать объект `ChatCompletionRequest`, опциональный `PredictConfig`, и возвращать `ChatCompletionResult`.

**ChatCompletionRequest** - объект, который приходит в наше приложение через `Mlp SDK`. В этот объект мы передаем значения для отправки в GigaChat. Пример полей:

 ```kotlin
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
```

### Описание полей

### model

Тип: строка
Обязательное поле

Название модели. Допустимые значения: `GigaChat`, `GigaChat:latest`, `GigaChat-Plus`, `GigaChat-Pro`.
Смотрите раздел "Модели GigaChat" для описания доступных моделей.

### messages

Тип: массив объектов
Обязательное поле

Массив сообщений, которыми пользователь обменивался с моделью.

**Объект сообщения:**

- **role**: строка
    - Допустимые значения: `system`, `user`, `assistant`, `search_result`.
    - Роль автора сообщения:
        - `system`: системный промпт, который задает роль модели, например, должна модель отвечать как академик или как
          школьник.
        - `user`: сообщение пользователя.
        - `assistant`: ответ модели.
        - `search_result`: позволяет передать модели документ, который она должна использовать для генерации ответов.
          Используется для поддержки RAG.
- **content**: строка
    - Текст сообщения.

### temperature

Тип: число с плавающей точкой
Значение по умолчанию зависит от выбранной модели и может изменяться с обновлениями модели.

Температура выборки. Значение температуры должно быть не меньше ноля. Чем выше значение, тем более случайным будет ответ
модели. При значениях температуры больше двух, набор токенов в ответе модели может отличаться избыточной случайностью.

### top_p

Тип: число с плавающей точкой
Допустимые значения: `>= 0` и `<= 1`.
Значение по умолчанию зависит от

выбранной модели и может изменяться с обновлениями модели.

Параметр используется как альтернатива температуре. Задает вероятностную массу токенов, которые должна учитывать модель.
Так, если передать значение `0.1`, модель будет учитывать только токены, чья вероятностная масса входит в верхние 10%.

### n

Тип: целое число
Допустимые значения: `>= 1` и `<= 4`
Значение по умолчанию: `1`

Количество вариантов ответов, которые нужно сгенерировать для каждого входного сообщения.

### stream

Тип: логическое значение
Значение по умолчанию: `false`

Указывает, что сообщения надо передавать по частям в потоке.

**ChatCompletionResult** - объект, в который мы будем размещать полученные данные из JSON от GigaChat. Пример полей:

```kotlin
return ChatCompletionResult(
    id = null,
    `object` = resultResponse.`object`,
    created = resultResponse.created,
    model = resultResponse.model,
    choices = choices,
    usage = usage
)
```

- **choices**: Массив ответов модели.
    - **message**: Объект сообщения.
        - **index**: Индекс сообщения в массиве, начиная с нуля.
        - **finish_reason**: Причина завершения гипотезы. Возможные значения: "stop", "length", "blacklist".
    - **created**: Дата и время создания ответа в формате Unix time.
    - **model**: Название модели. Допустимые значения: "GigaChat", "GigaChat:latest", "GigaChat-Plus", "GigaChat-Pro".
- **usage**: Данные об использовании модели.
    - **prompt_tokens**: Количество токенов во входящем сообщении (роль user).
    - **completion_tokens**: Количество токенов, сгенерированных моделью (роль assistant).
    - **total_tokens**: Общее количество токенов.
    - **system_tokens**: Количество токенов в системном промпте (роль system).
    - **object**: Название вызываемого метода.

**PredictConfig?** - опциональные параметры, которые мы задаем по умолчанию в нашем приложении, если из Caila не будут
поступать эти поля.
```
   val model : String = "GigaChat",
    val temperature: Double = 1.0,
    val top_p: Double = 0.1,
    val n: Int = 1,
    val stream: Boolean = false,
    val maxTokens: Int = 1024,
    val repetition_penalty: Double = 1.0,
    val update_interval: Int = 0
```


---

##  Соединяем IDE с сервисом Caila

Для того чтобы наш сервис в Caila.io имел соединение с нашей средой разработки (IDE), нам нужно добавить в наше окружение (контекст) переменные окружения, которые мы брали ранее на этапе "[Создание сервиса в Caila](#cоздание-сервиса-в-caila)".

1. В IntelliJ IDEA (или другом продукте JetBrains) сверху рядом с кнопками "Run", "Debug", "Stop" нажмите на кнопку Main.kt (название вашего класса). Если такого класса нет, то запустите проект.
2. Далее нажмите на "Edit Configurations" и в классе Main в разделе "Environment variables" вставьте полученные ранее из Caila.io переменные окружения.

   Переменные должны быть разделены знаком ';'.

   **Пример:**
   Было:
   ```
   MLP_ACCOUNT_ID=234567
   MLP_MODEL_ID=3456
   MLP_INSTANCE_ID=9898
   ```
   Стало:
   ```kotlin
   MLP_ACCOUNT_ID=234567;
   MLP_MODEL_ID=3456;
   MLP_INSTANCE_ID=9898;
   ```

3. Далее укажем в нашем приложении, что мы будем брать эти переменные (env) из окружения IDE (контекста).

   Это делается следующим образом:

```kotlin
class Main(
    override val context: MlpExecutionContext
) : MlpPredictServiceBase<ChatCompletionRequest, ChatCompletionResult>() 
```

Здесь мы присваиваем переменной `context` типа `MlpExecutionContext` значения из окружения при помощи поля `systemContext` (будет далее), то есть значения, которые мы берем из контекста нашей IDE.



---
## InitConfig

Мы также можем добавить инициализационные конфигурации к нашим переменным среды. Для этого мы будем использовать data class `InitConfig`.

Чтобы сделать это в нашем приложении, добавьте следующий код:

```kotlin
data class InitConfig(
    val baseUri: String,
    val clientSecret: String,
    val RqUID: String,
    val scope: String
)
```

В переменных среды добавьте следующие значения:

```json
SERVICE_CONFIG={
  "baseUri": "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
  "clientSecret": "NGFGHJKBVYUIOKPTYtN2EwYjNiZThKJBLKLKNTYtNDU5Yi1hODE1LTg3Mzk4MTA2MzM3Nw==",
  "scope": "GIGACHAT_API_PERS",
  "RqUID": "1242435-b027-46fc-a9d1-a2eb705dc892"
}
```

Эти переменные необходимы для получения Bearer токена.

**RqUID**: Уникальный идентификатор запроса. Должен соответствовать формату uuid4. Этот параметр используется для журналирования входящих вызовов и разбора инцидентов.

**scope**: Версия API. Возможные значения:

- `GIGACHAT_API_PERS`: доступ для физических лиц.
- `GIGACHAT_API_CORP`: доступ для юридических лиц.

**baseUri**: Адрес, по которому мы запрашиваем токен.

**clientSecret**: Ключ для получения токена. Этот ключ формируется из ClientID и SecretKey. Инструкции по получению ClientId и ClientSecret описаны [здесь](https://developers.sber.ru/docs/ru/gigachat/individuals-quickstart).




______
## Сертификат Минцифры

Для получения Bearer токена нам необходимо использовать сертификат Минцифры.

Вы можете загрузить его по следующему адресу: [ссылка на сертификат](https://www.gosuslugi.ru/crt)

Инструкции по использованию сертификата описаны в [этой документации](https://developers.sber.ru/docs/ru/gigachat/certificates).

Для того чтобы использовать сертификат, нам нужно получить его путь:

```kotlin
val currentDir = System.getProperty("user.dir")
val CERT_PATH = Path("$currentDir/cert/russiantrustedca.pem").toString()
```

Здесь:

- `System.getProperty("user.dir")` - это директория проекта.
- `/cert/` - это папка, в которой находится сертификат (опционально).
- `russiantrustedca.pem` - это название сертификата.

______
## GigaChatConnector

Класс GigaChatConnector создает подключение к GigaChat API.

Класс `GigaChatConnector` предоставляет функциональность для отправки запросов на сервер GigaChat и получения ответов.

### Методы

#### sendMessageToGigaChat

Отправляет запрос на сервер GigaChat с заданными данными и возвращает ответ.

```kotlin
fun sendMessageToGigaChat(gigaReq: GigaChatRequest): GigaChatResponse
```

**Параметры:**
- `gigaReq`: Данные запроса для отправки на сервер GigaChat.

**Возвращаемое значение:**
- `GigaChatResponse`: Ответ от сервера GigaChat.

**Исключения:**
- `IOException`: Возникает, если при отправке запроса или получении ответа произошла ошибка.

#### updateBearerToken

Обновляет Bearer токен, если текущий токен истек или отсутствует.

```kotlin
private fun updateBearerToken()
```

#### getNewBearerToken

Получает новый Bearer токен от сервера аутентификации.

```kotlin
private fun getNewBearerToken(): String
```

**Возвращаемое значение:**
- `String`: Новый Bearer токен.

**Исключения:**
- `IOException`: Возникает, если при отправке запроса или получении ответа произошла ошибка.

#### configureSSLClient

Настраивает клиент OkHttpClient для установки SSL-соединения с сервером.

```kotlin
private fun configureSSLClient(): OkHttpClient
```

Более подробно можно ознакомится [здесь](#https://github.com/semenovfilipp/gigachat-ml-service/blob/master/src/main/kotlin/gigachat/GigaChatConnector.kt)