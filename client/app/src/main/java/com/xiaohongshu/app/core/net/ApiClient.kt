package com.xiaohongshu.app.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络装配。所有接口共享一个 [OkHttpClient]（连接池/磁盘缓存复用），
 * 仅 AI 对话使用长超时客户端（契约：AI 70s，其余 15s）。
 */
object ApiClient {

    /** 统一超时 15s（线框全局规范「反馈」）。 */
    private const val TIMEOUT_SECONDS = 15L

    /** AI 对话超时 70s（契约 §10.1）。 */
    private const val AI_TIMEOUT_SECONDS = 70L

    /** 宽松解析：后端会新增字段，客户端不应因此崩溃。 */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private val contentType = "application/json; charset=utf-8".toMediaType()

    private fun converterFactory() = json.asConverterFactory(contentType)

    /**
     * 注入 Bearer token。
     * 契约：`Authorization: Bearer <token>`。未登录时不带该头（访客可读接口）。
     */
    private class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = tokenProvider.token()
            val request = if (token.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
            return chain.proceed(request)
        }
    }

    /**
     * 捕获登录态失效。
     *
     * HTTP 401 直接触发；HTTP 200/400 但信封 `code ∈ {1005,1006,1007}` 也触发。
     * 用 [Response.peekBody] 读取（不消耗响应体，业务层仍能正常解析）。
     */
    private class SessionExpiryInterceptor(
        private val notifier: SessionExpiryNotifier,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                notifier.notifyExpired()
                return response
            }
            val bodyCode = runCatching {
                val text = response.peekBody(8192).string()
                if (text.isBlank()) return@runCatching null
                val obj = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching null
                obj["code"]?.jsonPrimitive?.content?.toIntOrNull()
            }.getOrNull()
            if (bodyCode != null && bodyCode in EXPIRY_CODES) {
                notifier.notifyExpired()
            }
            return response
        }
    }

    private val EXPIRY_CODES = setOf(CODE_NOT_LOGGED_IN, CODE_TOKEN_EXPIRED, CODE_TOKEN_INVALID)

    /**
     * 按 baseUrl 构建 Retrofit 集合。用函数而非单例字段，便于测试或多环境切换。
     */
    fun create(
        baseUrl: String,
        tokenProvider: TokenProvider,
        sessionExpiryNotifier: SessionExpiryNotifier,
        debugLogging: Boolean,
    ): Apis {
        val logging = HttpLoggingInterceptor().apply {
            level = if (debugLogging) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        fun buildClient(timeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(SessionExpiryInterceptor(sessionExpiryNotifier))
            .addInterceptor(logging)
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds + 5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun buildRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(converterFactory())
            .build()

        val default = buildRetrofit(buildClient(TIMEOUT_SECONDS))
        val longRunning = buildRetrofit(buildClient(AI_TIMEOUT_SECONDS))

        return Apis(
            user = default.create(com.xiaohongshu.app.data.api.UserApi::class.java),
            post = default.create(com.xiaohongshu.app.data.api.PostApi::class.java),
            comment = default.create(com.xiaohongshu.app.data.api.CommentApi::class.java),
            like = default.create(com.xiaohongshu.app.data.api.LikeApi::class.java),
            collect = default.create(com.xiaohongshu.app.data.api.CollectApi::class.java),
            follow = default.create(com.xiaohongshu.app.data.api.FollowApi::class.java),
            notification = default.create(com.xiaohongshu.app.data.api.NotificationApi::class.java),
            upload = default.create(com.xiaohongshu.app.data.api.UploadApi::class.java),
            ai = longRunning.create(com.xiaohongshu.app.data.api.AiApi::class.java),
        )
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}

/** 所有 Retrofit 接口的聚合，由 [AppContainer] 持有并注入各 Repository。 */
data class Apis(
    val user: com.xiaohongshu.app.data.api.UserApi,
    val post: com.xiaohongshu.app.data.api.PostApi,
    val comment: com.xiaohongshu.app.data.api.CommentApi,
    val like: com.xiaohongshu.app.data.api.LikeApi,
    val collect: com.xiaohongshu.app.data.api.CollectApi,
    val follow: com.xiaohongshu.app.data.api.FollowApi,
    val notification: com.xiaohongshu.app.data.api.NotificationApi,
    val upload: com.xiaohongshu.app.data.api.UploadApi,
    val ai: com.xiaohongshu.app.data.api.AiApi,
)
