## stream = true
```
{
  "model": "GigaChat",
  "messages": [
    {
      "role": "user",
      "content": "Покажи пример ответа потоком со stream=true"
    }
  ],
  "temperature": 1,
  "top_p": 0.1,
  "n": 1,
  "stream": true,
  "max_tokens": 512,
  "repetition_penalty": 1,
  "update_interval": 0
}
```

## stream = false
```
{
  "model": "GigaChat",
  "messages": [
    {
      "role": "user",
      "content": "Привет"
    }
  ],
  "temperature": 1,
  "top_p": 0.1,
  "n": 1,
  "stream": false,
  "max_tokens": 512,
  "repetition_penalty": 1,
  "update_interval": 0
}
```