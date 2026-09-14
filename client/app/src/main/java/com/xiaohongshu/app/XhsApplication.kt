package com.xiaohongshu.app

import android.app.Application
import com.xiaohongshu.app.di.AppContainer

/**
 * 应用入口。持有全局依赖容器。
 *
 * 注意：这里**不做**登录态恢复（I1）。恢复需要网络请求，放在 Application 里会拖慢冷启动首帧；
 * 改由 [MainActivity] 在首帧后调用 `sessionManager.restore()`，期间 UI 先用本地缓存的
 * 用户快照渲染登录态骨架。
 */
class XhsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
