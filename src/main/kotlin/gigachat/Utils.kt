package gigachat

import com.mlp.sdk.utils.JSON


inline fun <reified T> String.parse() = JSON.parse<T>(this)
inline fun <reified T> String?.parseOrNull() = this?.let { JSON.parse<T>(this) }

