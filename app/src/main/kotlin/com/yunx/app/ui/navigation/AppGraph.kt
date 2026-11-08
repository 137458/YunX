/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.ui.navigation

import android.content.Context
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.C139DeviceInfo
import com.yunx.app.data.network.GuangYaApi
import com.yunx.app.data.network.ILanzouApi
import com.yunx.app.data.network.LanzouApi
import com.yunx.app.data.network.Pan115Api
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.repository.BaiduAccountRepository
import com.yunx.app.data.repository.C139AccountRepository
import com.yunx.app.data.repository.GuangYaAccountRepository
import com.yunx.app.data.repository.ILanzouAccountRepository
import com.yunx.app.data.repository.LanzouAccountRepository
import com.yunx.app.data.repository.Pan115AccountRepository
import com.yunx.app.data.repository.Pan123AccountRepository
import com.yunx.app.data.repository.QuarkAccountRepository
import com.yunx.app.data.repository.UCAccountRepository
import com.yunx.app.data.repository.XunleiAccountRepository
import com.yunx.app.data.network.HttpClients

/**
 * 应用级依赖图。
 *
 * 全屏页迁成 NavHost destination 后，[com.yunx.app.ui.MainScreen] 不再是常驻组合：
 * 覆盖页在栈顶时主框架会离开组合，页面内的 `remember { ... }` 会被丢弃并重建。
 * 因此那些「必须全局唯一」的对象（账号仓库、下载管理器）上提到本类，由 [AppNavHost]
 * 在导航宿主生命周期内创建一次，主框架与各 destination 共享同一实例。
 */
class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val settings = SettingsRepository(appContext)
    val db = AppDatabase.get(appContext)

    val api = QuarkApi()
    val ucApi = UCApi()
    val xunleiApi = XunleiApi()
    val baiduApi = BaiduApi()
    // 139 的口令 XML 接口要带 x-DeviceInfo / x-yun-user-agent 两枚设备头，字段由系统 API 与
    // 本机安装 ID 动态生成（不读 IMEI / ANDROID_ID，见 C139DeviceInfo KDoc）；不注入就会退化成占位值。
    val c139Api = C139Api(deviceInfoProvider = { C139DeviceInfo.from(appContext) })
    val pan123Api = Pan123Api()
    val pan115Api = Pan115Api()
    val guangyaApi = GuangYaApi()
    val ilanzouApi = ILanzouApi()
    val lanzouApi = LanzouApi()

    val quarkRepository = QuarkAccountRepository(db.quarkAccountDao(), api)
    val ucRepository = UCAccountRepository(db.ucAccountDao(), ucApi)
    val xunleiRepository = XunleiAccountRepository(db.xunleiAccountDao(), xunleiApi)
    val baiduRepository = BaiduAccountRepository(db.baiduAccountDao(), baiduApi)
    val c139Repository = C139AccountRepository(db.c139AccountDao())
    val pan123Repository = Pan123AccountRepository(db.pan123AccountDao(), pan123Api)
    val pan115Repository = Pan115AccountRepository(db.pan115AccountDao(), pan115Api)
    val guangyaRepository = GuangYaAccountRepository(db.guangyaAccountDao(), guangyaApi)
    val ilanzouRepository = ILanzouAccountRepository(db.ilanzouAccountDao(), ilanzouApi)
    val lanzouRepository = LanzouAccountRepository(db.lanzouAccountDao(), lanzouApi)

    val backupManager = AuthBackupManager(
        db.quarkAccountDao(),
        db.ucAccountDao(),
        db.xunleiAccountDao(),
        db.baiduAccountDao(),
        db.c139AccountDao(),
        db.pan123AccountDao(),
        db.pan115AccountDao(),
        db.guangyaAccountDao(),
        db.ilanzouAccountDao(),
        db.lanzouAccountDao(),
        db.videoAccountDao()
    )

    // 下载管理器：OkHttp 分片下载器 + Room 任务持久化 + 可配置线程数（设置页动态生效）
    // 下载客户端由全局 HttpClients 统一管理（大 Dispatcher 保障分片并发，不锁死 CDN host；
    // 并支持隐藏菜单「忽略 SSL 证书」开关，抓包调试时即时生效，无需重启）
    val downloadManager = DownloadManager(
        context = appContext,
        dao = db.downloadTaskDao(),
        downloader = ChunkDownloader({ HttpClients.downloadClient() }),
        threadProvider = { platform -> settings.downloadThreadsFor(platform) },
        // 自定义下载保存目录（SAF tree Uri），设置页可选，动态生效
        saveDirProvider = { settings.downloadDirUri },
        // 网络与下载策略（设置页可调，动态生效）：并发任务数 / 全局限速 / 失败重试
        concurrencyProvider = { settings.maxConcurrentDownloads },
        speedLimitProvider = { settings.downloadSpeedLimit },
        retryCountProvider = { settings.downloadRetryCount },
        // 锁屏保持下载 / 通知栏速度开关
        keepWhenLockedProvider = { settings.keepDownloadWhenLocked },
        showSpeedProvider = { settings.notificationShowSpeed }
    )

    init {
        // 光鸭云盘设备标识：业务 API 的 did 头要同步取值，故由仓库缓存、API 惰性读取
        guangyaApi.deviceIdProvider = { guangyaRepository.cachedDeviceId() }
        // 迅雷 access_token 过期（401 unauthenticated）自动刷新：refresh_token 换新并持久化
        xunleiApi.refreshTokenProvider = { deviceId ->
            val acc = xunleiRepository.getAccount()
            if (acc == null || acc.refreshToken.isBlank()) null
            else xunleiApi.refreshToken(acc.refreshToken, deviceId)?.also { (at, nrt) ->
                xunleiRepository.updateTokens(at, nrt)
            }
        }
        // 网页登录的 captcha：本地 9 盐签名现换（快、后台可用），签名被拒才回退网页会话
        xunleiApi.webCaptchaRefresher = { deviceId, action ->
            xunleiRepository.refreshWebCaptcha(deviceId, action)
        }
    }
}
