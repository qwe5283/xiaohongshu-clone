package com.xiaohongshu.app.core.net

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 统一响应信封：`{ "code": 200, "message": "...", "data": ..., "timestamp": ... }`
 * （见 client/docs/API契约-v2.md 通用约定）
 *
 * 后端失败响应的 `data` 为 null，故 [data] 可空；[unwrap] 负责拆包。
 */
@kotlinx.serialization.Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    val timestamp: Long = 0L,
)

/**
 * 调用结果。业务失败与网络失败分开，便于按线框 I2「三类反馈」分流：
 * Toast / 列表错误态+重试（B4-2）/ 表单错误条（A5-1、E4）。
 */
sealed interface ApiResult<out T> {

    data class Ok<T>(val data: T) : ApiResult<T>

    /** 业务失败（`code != 200`），HTTP 通常 400。[message] 可直接展示。 */
    data class Biz(val code: Int, val message: String) : ApiResult<Nothing>

    /** 登录态失效（HTTP 401 或 code ∈ {1005,1006,1007}）。全局统一处理为「清态 + 推入登录页」。 */
    data object Unauthorized : ApiResult<Nothing>

    /** 网络层失败（超时/DNS/连接断开）。 */
    data class Network(val cause: Throwable) : ApiResult<Nothing>

    /** 服务端 5xx 或无法解析的响应。 */
    data class Server(val status: Int, val message: String) : ApiResult<Nothing>
}

/** 仅保留成功值，失败返回 null。 */
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Ok)?.data

/** 成功则映射，失败原样透传。 */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(transform(data))
    is ApiResult.Biz -> this
    is ApiResult.Server -> this
    is ApiResult.Network -> this
    ApiResult.Unauthorized -> ApiResult.Unauthorized
}

/** 成功则执行副作用，返回自身。 */
inline fun <T> ApiResult<T>.onOk(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Ok) block(data)
    return this
}

/** 失败（任意原因）时执行副作用，返回自身。 */
inline fun <T> ApiResult<T>.onFailure(block: (ApiResult<Nothing>) -> Unit): ApiResult<T> {
    when (this) {
        is ApiResult.Ok -> Unit
        is ApiResult.Biz -> block(this)
        is ApiResult.Server -> block(this)
        is ApiResult.Network -> block(this)
        ApiResult.Unauthorized -> block(ApiResult.Unauthorized)
    }
    return this
}

val ApiResult<*>.isOk: Boolean get() = this is ApiResult.Ok

/** 统一的用户可读文案（I2）。业务失败透传后端 [ApiResult.Biz.message]。 */
fun ApiResult<*>.userMessage(): String = when (this) {
    is ApiResult.Ok -> ""
    is ApiResult.Biz -> message.ifBlank { "操作失败，请稍后重试" }
    is ApiResult.Server -> "服务异常，请稍后重试"
    is ApiResult.Network -> when (cause) {
        is SocketTimeoutException -> "请求超时，请检查网络"
        is UnknownHostException -> "网络连接失败，请检查网络"
        else -> "网络异常，请稍后重试"
    }
    ApiResult.Unauthorized -> "登录已过期，请重新登录"
}

/**
 * 把一次网络调用收敛为 [ApiResult]。
 *
 * - [T] 已是解包后的业务数据（由 [ApiCall] 负责拆信封）。
 * - 协程取消必须重新抛出，否则会吞掉 Composable 离开时的取消。
 */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ApiException) {
    when {
        e.code == CODE_NOT_LOGGED_IN || e.code == CODE_TOKEN_EXPIRED || e.code == CODE_TOKEN_INVALID ->
            ApiResult.Unauthorized
        else -> ApiResult.Biz(e.code, e.message ?: "操作失败")
    }
} catch (e: HttpException) {
    if (e.code() == 401) ApiResult.Unauthorized
    else ApiResult.Server(e.code(), e.message())
} catch (e: IOException) {
    ApiResult.Network(e)
} catch (e: Exception) {
    ApiResult.Server(-1, e.message ?: "未知错误")
}

/** 业务异常：信封 `code != 200`。 */
class ApiException(val code: Int, message: String) : Exception(message)

/** 错误码（client/docs/API契约-v2.md §9）。 */
const val CODE_SUCCESS = 200
const val CODE_NOT_LOGGED_IN = 1005
const val CODE_TOKEN_EXPIRED = 1006
const val CODE_TOKEN_INVALID = 1007
const val CODE_POST_NOT_FOUND = 2001
const val CODE_POST_DELETED = 2002
const val CODE_IMAGE_LIMIT = 2004
const val CODE_NEED_MEDIA = 2005
const val CODE_PARAM_ERROR = 5001

/** 命名化错误码，供业务分支判断（[CODE_*] 常量保留给拦截器与低层使用）。 */
object Code {
    const val SUCCESS = CODE_SUCCESS
    const val NOT_LOGGED_IN = CODE_NOT_LOGGED_IN
    const val TOKEN_EXPIRED = CODE_TOKEN_EXPIRED
    const val TOKEN_INVALID = CODE_TOKEN_INVALID
    const val USER_EXISTS = 1003
    const val POST_NOT_FOUND = CODE_POST_NOT_FOUND
    const val POST_DELETED = CODE_POST_DELETED
    const val NO_PERMISSION_POST = 2003
    const val IMAGE_LIMIT = CODE_IMAGE_LIMIT
    const val NEED_MEDIA = CODE_NEED_MEDIA
    const val COMMENT_NOT_FOUND = 3001
    const val COMMENT_DELETED = 3002
    const val UPLOAD_FAILED = 4001
    const val PARAM_ERROR = CODE_PARAM_ERROR
    const val PARAM_MISSING = 5002
    const val ALREADY_FOLLOWED = 6001
    const val NOT_FOLLOWED = 6002
    const val CANNOT_FOLLOW_SELF = 6003
}

/** 拆信封：成功返回 data，失败抛 [ApiException]，401 抛 HTTP 异常交给 [apiCall]。 */
fun <T> ApiEnvelope<T>.unwrap(): T {
    if (code == CODE_SUCCESS) {
        @Suppress("UNCHECKED_CAST")
        return data as T
    }
    if (code == CODE_NOT_LOGGED_IN || code == CODE_TOKEN_EXPIRED || code == CODE_TOKEN_INVALID) {
        throw ApiException(code, message)
    }
    throw ApiException(code, message.ifBlank { "操作失败" })
}
