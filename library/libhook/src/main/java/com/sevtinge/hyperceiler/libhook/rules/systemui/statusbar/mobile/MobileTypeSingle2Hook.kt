/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */

package com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.core.view.isVisible
import com.sevtinge.hyperceiler.common.log.XposedLog
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar.mobile.support.MobileTypeRenderStateStore
import com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar.mobile.support.MobileTypeViewRenderer
import com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar.mobile.support.MobileTypeVisibilityResolver
import com.sevtinge.hyperceiler.libhook.utils.api.DeviceHelper.System.isMoreAndroidVersion
import com.sevtinge.hyperceiler.libhook.utils.api.DisplayUtils.dp2px
import com.sevtinge.hyperceiler.libhook.utils.hookapi.StateFlowHelper.getStateFlowValue
import com.sevtinge.hyperceiler.libhook.utils.hookapi.StateFlowHelper.newReadonlyStateFlow
import com.sevtinge.hyperceiler.libhook.utils.hookapi.StateFlowHelper.setStateFlowValue
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.DataSimFlowProxy
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.KotlinJob
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MiuiStub
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobileClass.mOperatorConfig
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobileClass.miuiCellularIconVM
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobileClass.miuiMobileIconBinder
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobileClass.mobileUiAdapter
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobileClass.modernStatusBarMobileView
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.bold
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.fontSize
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.getLocation
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.hideIndicator
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.isEnableDouble
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.leftMargin
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.mobileNetworkType
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.rightMargin
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.showMobileType
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobilePrefs.verticalOffset
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.MobileViewHelper
import com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.findViewByIdName
import io.github.lingqiqi5211.ezhooktool.core.callMethodAs
import io.github.lingqiqi5211.ezhooktool.core.callMethodOrNull
import io.github.lingqiqi5211.ezhooktool.core.callStaticMethod
import io.github.lingqiqi5211.ezhooktool.core.findAllMethods
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.java.Constructors
import io.github.lingqiqi5211.ezhooktool.core.loadClass
import io.github.lingqiqi5211.ezhooktool.xposed.EzXposed
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createAfterHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createInterceptHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getAdditionalInstanceFieldAs
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getBooleanField
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getIntField
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getObjectField
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getObjectFieldAs
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.setAdditionalInstanceField
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.setObjectField
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

object MobileTypeSingle2Hook : BaseHook() {
    private const val LAST_BOUND_VIEW_MODEL_KEY = "mobile_type_single2_last_bound_vm"
    private const val DATA_SIM_CONTEXT_KEY = "MobileTypeSingle2Hook.dataSimContext"

    /**
     * 每个 VM 复用同一个可见性 flow。
     *
     * 视图在 bind 时会订阅 VM 里的 flow 对象，之后模块写的值只有落在**同一个对象**上才会被视图收到。
     * 旧实现每次绑定都 `newReadonlyStateFlow(...)` 造一个新对象装进字段，老视图订阅的还是旧对象
     * → 模块的判定永远送不到它，只有重新 bind 过的视图（如下拉通知栏那份）才跟得上。
     */
    private const val VISIBILITY_FLOW_KEY = "mobile_type_single2_visibility_flow"
    private const val DEBUG_LOG = true

    private val showNameFlowProxy = DataSimFlowProxy("")
    private val inOutVisibleProxy = DataSimFlowProxy(false)
    private val inOutResIdProxy = DataSimFlowProxy(0)
    private val mobileTypeSingleVisibleProxy = DataSimFlowProxy(false)
    private val mobileTypeVisibleProxy = DataSimFlowProxy(false)

    @Volatile
    private var broadcastRegistered = false

    @Volatile
    private var dataChangedCollectorSource: Any? = null

    @Volatile
    private var dataChangedCollectorJob: KotlinJob? = null

    @Volatile
    private var defaultConnectionsCollectorSource: Any? = null

    @Volatile
    private var defaultConnectionsCollectorJob: KotlinJob? = null

    @Volatile
    private var isWifiDefaultConnection: Boolean? = null

    @Volatile
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null

    /** 每个 subId 对应的 defaultConnections StateFlow，用于刷新时现读实时值。 */
    private val defaultConnectionsBySubId = ConcurrentHashMap<Int, Any>()

    private val boundViews = ConcurrentHashMap<Int, MutableSet<ViewGroup>>()

    /** rootView -> 该视图对应的 cellProvider VM，用于把判定结果回写进它订阅的 flow */
    private val boundViewModels = ConcurrentHashMap<ViewGroup, Any>()

    /** 只在判定结果变化时打日志，避免刷屏 */
    private val lastLoggedVisible = ConcurrentHashMap<ViewGroup, Boolean>()

    @Volatile
    private var unresolvedViewModelLogCount = 0

    private val renderStateStore = MobileTypeRenderStateStore()
    private val visibilityResolver = MobileTypeVisibilityResolver(
        showMobileType = showMobileType,
        mobileNetworkType = mobileNetworkType,
        isEnableDouble = isEnableDouble,
        isSingleSimMode = MobileViewHelper::isSingleSimMode
    )
    private val viewRenderer = MobileTypeViewRenderer(
        showMobileType = showMobileType,
        hideIndicator = hideIndicator,
        mobileNetworkType = mobileNetworkType,
        visibilityResolver = visibilityResolver,
        updateMobileTypeDrawable = ::updateMobileTypeDrawable
    )

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    @SuppressLint("MissingPermission")
    private val refreshBoundViewsRunnable = Runnable { refreshBoundViewsNow() }

    /**
     * WiFi 连/断之后的"补算"。
     *
     * 系统在切换 WiFi 时，默认上网网络（activeNetwork）不会立刻更新：NetworkCallback 触发的那
     * 一刻往往还能读到旧网络。此时马上重算就会把过渡态写进视图，把图标状态固化成错的
     * （典型现象：断开 WiFi 后仍判定"当前是 WiFi 上网"→ 大 5G 图标一直不显示）。
     * 所以在过渡窗口之后再补算两次，用稳态值覆盖掉过渡态结果。
     */
    @SuppressLint("MissingPermission")
    private val wifiSettleRefreshNearRunnable = Runnable { refreshBoundViewsNow() }

    @SuppressLint("MissingPermission")
    private val wifiSettleRefreshFarRunnable = Runnable { refreshBoundViewsNow() }

    /** WiFi 变化后补算的延迟：先近后远，覆盖系统默认网络的切换窗口 */
    private const val WIFI_SETTLE_NEAR_DELAY_MS = 800L
    private const val WIFI_SETTLE_FAR_DELAY_MS = 2500L

    override fun init() {
        BaseHook.registerHandlerHotReloadCleanup(mainHandler)
        logVisibility(
            "init",
            "showMobileType=$showMobileType mobileNetworkType=$mobileNetworkType dual=$isEnableDouble " +
                "sdk=${android.os.Build.VERSION.SDK_INT} driveLarge=${shouldDriveLargeVisibility()}"
        )
        if (isEnableDouble) {
            getHotReloadRuntimeState(DATA_SIM_CONTEXT_KEY, Context::class.java)
                ?.let(::registerDataSimBroadcast)
        }
        if (mobileNetworkType == 0 || mobileNetworkType == 2 || mobileNetworkType == 4) {
            registerConnectivityWatcher()
        }
        hookMobileViewAndVM()
    }

    private fun logVisibility(stage: String, message: String) {
        if (!DEBUG_LOG) return
        XposedLog.i(TAG, lpparam.packageName, "[$stage] $message")
    }

    /** 只有「大 5G 图标由模块判定显隐」时才接管可见性 flow，其余显示逻辑一律不碰 */
    private fun shouldDriveLargeVisibility(): Boolean {
        return showMobileType && (mobileNetworkType == 0 || mobileNetworkType == 2)
    }

    /** 取（或首次创建）该 VM 专用的稳定可见性 flow */
    private fun visibilityFlowOf(viewModel: Any): Any {
        runCatching { viewModel.getAdditionalInstanceFieldAs<Any?>(VISIBILITY_FLOW_KEY) }
            .getOrNull()
            ?.let { return it }
        val flow = newReadonlyStateFlow(false)
        viewModel.setAdditionalInstanceField(VISIBILITY_FLOW_KEY, flow)
        return flow
    }

    /**
     * 把稳定 flow 装进 VM 的 `mobileTypeSingleVisible` 字段。
     *
     * **必须在视图 bind 之前调用**：视图 bind 时读到哪个 flow 对象，之后就只认那个对象。
     * OS4 上 binder 直接从 MiuiMobileIconVMImpl（holder）取字段，所以 holder 也一并写入。
     */
    private fun prepareVisibilityFlow(viewModel: Any, holder: Any?) {
        if (!shouldDriveLargeVisibility()) return
        val flow = visibilityFlowOf(viewModel)
        val current = runCatching { viewModel.getObjectField("mobileTypeSingleVisible") }.getOrNull()
        if (current !== flow) {
            runCatching { viewModel.setObjectField("mobileTypeSingleVisible", flow) }
                .onSuccess { logVisibility("flow", "install -> ${viewModel.javaClass.simpleName}") }
        }
        if (holder != null && holder !== viewModel) {
            val holderCurrent = runCatching { holder.getObjectField("mobileTypeSingleVisible") }.getOrNull()
            if (holderCurrent != null && holderCurrent !== flow) {
                runCatching { holder.setObjectField("mobileTypeSingleVisible", flow) }
                    .onSuccess { logVisibility("flow", "holder patched -> ${holder.javaClass.simpleName}") }
            }
        }
    }

    /** 把当前判定结果写回该视图所属 VM 的稳定 flow，保证之后任何一次 rebind 都拿到同一结论 */
    private fun syncLargeVisibilityFlow(rootView: ViewGroup, visible: Boolean) {
        val viewModel = boundViewModels[rootView] ?: return
        runCatching { setStateFlowValue(visibilityFlowOf(viewModel), visible) }
    }

    @SuppressLint("MissingPermission")
    private fun hookMobileViewAndVM() {
        if (showMobileType || mobileNetworkType == 3) {
            if (isMoreAndroidVersion(36)) {
                loadClass("com.miui.systemui.statusbar.views.MobileTypeDrawable")
            } else {
                loadClass("com.android.systemui.statusbar.views.MobileTypeDrawable")
            }.findMethod { name("measure") }.createHook {
                returnConstant(null)
            }
        }

        if (isMoreAndroidVersion(37)) {
            // OS4: MiuiCellularIconVM 无参构造，字段在 bind 时才就绪；
            // binder 的 args[2] 即 MiuiMobileIconVMImpl，直接取其 cellProvider 与 interactor
            miuiMobileIconBinder.findMethod { name("bind") }
                .createAfterHook { param ->
                    val vmImpl = param.args[2] ?: return@createAfterHook
                    val viewModel = runCatching {
                        vmImpl.callMethodAs<Any>("getCellProvider")
                    }.getOrNull() ?: return@createAfterHook
                    val interactor = runCatching {
                        vmImpl.getObjectFieldAs<Any>("iconInteractor")
                    }.getOrNull() ?: return@createAfterHook
                    val subId = runCatching {
                        interactor.getObjectFieldAs<Int>("subId")
                    }.getOrNull() ?: return@createAfterHook

                    // bind 会对同一 VM 多次调用，已处理过则跳过
                    if (viewModel.getAdditionalInstanceFieldAs<Any?>("interactor") === interactor) {
                        return@createAfterHook
                    }
                    viewModel.setAdditionalInstanceField("interactor", interactor)
                    viewModel.setObjectField(
                        "wifiAvailable",
                        interactor.getObjectField("wifiAvailable")
                    )
                    applyViewModelState(viewModel, interactor, subId)
                    // applyViewModelState 会把字段换成双排代理 flow，这里再覆盖回模块的稳定 flow
                    prepareVisibilityFlow(viewModel, vmImpl)
                }
        } else {
            Constructors.find(miuiCellularIconVM).first().createAfterHook { param ->
                val viewModel = param.thisObject
                val interactor = param.args[1]
                val miuiInteractor = param.args[2]

                viewModel.setAdditionalInstanceField("interactor", interactor)
                viewModel.setObjectField(
                    "wifiAvailable",
                    miuiInteractor?.getObjectField("wifiAvailable")
                )

                val subId = runCatching {
                    miuiInteractor?.getObjectFieldAs<Int>("subId")
                }.getOrNull() ?: runCatching {
                    interactor?.getObjectFieldAs<Int>("subId")
                }.getOrNull() ?: runCatching {
                    viewModel.getObjectFieldAs<Int>("subId")
                }.getOrNull() ?: return@createAfterHook

                applyViewModelState(viewModel, interactor, subId)
                prepareVisibilityFlow(viewModel, null)
            }
        }

        modernStatusBarMobileView.findAllMethods { name("constructAndBind") }
            .forEach { method ->
                method.createInterceptHook { chain ->
                    // 视图在 bind 时读到哪个 flow 对象，之后就只认那个对象，
                    // 所以必须在 proceed 之前把模块的稳定 flow 装进 VM，否则视图订阅的是 MIUI 自己的 flow。
                    val pendingViewModel = resolveConstructAndBindViewModel(chain.args.toList())
                    if (pendingViewModel != null) {
                        prepareVisibilityFlow(pendingViewModel, null)
                    } else if (unresolvedViewModelLogCount < 3) {
                        unresolvedViewModelLogCount++
                        logVisibility(
                            "bind",
                            "constructAndBind 未解析出 VM: args=" +
                                chain.args.joinToString { it?.javaClass?.simpleName ?: "null" }
                        )
                    }
                    val result = chain.proceed()
                    val rootView = result as? ViewGroup ?: return@createInterceptHook result
                    val viewModel = pendingViewModel
                        ?: resolveConstructAndBindViewModel(chain.args.toList())
                        ?: return@createInterceptHook result
                    bindConstructedMobileViewIfNeeded(rootView, viewModel)
                    result
                }
            }

        if (!showMobileType && mobileNetworkType == 4) {
            Constructors.find(mobileUiAdapter).first().createAfterHook {
                setOnDataChangedListener(it.thisObject)
            }
        }

        if (showMobileType && isEnableDouble && (mobileNetworkType == 0 || mobileNetworkType == 2)) {
            Constructors.find(mobileUiAdapter).first().createAfterHook {
                setOnDefaultConnectionsListener(it.thisObject)
            }
        }

        if (showMobileType && mobileNetworkType == 4) {
            showMobileTypeSingle()
        }
    }

    @SuppressLint("MissingPermission")
    private fun applyViewModelState(viewModel: Any, interactor: Any?, subId: Int) {
        val slotIndex = SubscriptionManager.getSlotIndex(subId)
        if (isEnableDouble) {
            viewModel.getObjectField("showName")?.let { originalFlow ->
                showNameFlowProxy.setupForSlot(
                    slotIndex,
                    subId,
                    originalFlow,
                    MobileViewHelper::isSingleSimMode
                )
                if (slotIndex == 0) {
                    viewModel.setObjectField("showName", showNameFlowProxy.proxy!!)
                }
            }
            if (!hideIndicator) {
                viewModel.getObjectField("inOutVisible")?.let { originalFlow ->
                    inOutVisibleProxy.setupForSlot(
                        slotIndex,
                        subId,
                        originalFlow,
                        MobileViewHelper::isSingleSimMode
                    )
                    if (slotIndex == 0) {
                        viewModel.setObjectField("inOutVisible", inOutVisibleProxy.proxy!!)
                    }
                }
                viewModel.getObjectField("inOutResId")?.let { originalFlow ->
                    inOutResIdProxy.setupForSlot(
                        slotIndex,
                        subId,
                        originalFlow,
                        MobileViewHelper::isSingleSimMode
                    )
                    if (slotIndex == 0) {
                        viewModel.setObjectField("inOutResId", inOutResIdProxy.proxy!!)
                    }
                }
            }
            viewModel.getObjectField("mobileTypeSingleVisible")?.let { originalFlow ->
                mobileTypeSingleVisibleProxy.setupForSlot(
                    slotIndex,
                    subId,
                    originalFlow,
                    MobileViewHelper::isSingleSimMode
                )
                if (slotIndex == 0) {
                    viewModel.setObjectField(
                        "mobileTypeSingleVisible",
                        mobileTypeSingleVisibleProxy.proxy!!
                    )
                }
            }
            viewModel.getObjectField("mobileTypeVisible")?.let { originalFlow ->
                mobileTypeVisibleProxy.setupForSlot(
                    slotIndex,
                    subId,
                    originalFlow,
                    MobileViewHelper::isSingleSimMode
                )
                if (slotIndex == 0) {
                    viewModel.setObjectField("mobileTypeVisible", mobileTypeVisibleProxy.proxy!!)
                }
            }
        }

        registerMobileStateCollectors(
            viewModel,
            interactor,
            subId,
            slotIndex
        )

        if (isEnableDouble) {
            syncDataSimProxiesNow()
            registerDataSimBroadcast()
        }
        scheduleRefreshBoundViews()
    }

    private fun showMobileTypeSingle() {
        mOperatorConfig.constructors[0].createAfterHook {
            it.thisObject.setObjectField("showMobileDataTypeSingle", true)
        }
    }

    private fun unwrapCellProviderViewModel(viewModel: Any?): Any? {
        if (viewModel == null) return null
        return if (viewModel.javaClass.simpleName == "MiuiMobileIconVMImpl") {
            viewModel.callMethodAs("getCellProvider")
        } else {
            viewModel
        }
    }

    private fun resolveConstructAndBindViewModel(args: List<Any?>): Any? {
        args.asReversed().forEach { arg ->
            val candidate = unwrapCellProviderViewModel(arg) ?: return@forEach
            val simpleName = candidate.javaClass.simpleName
            if (simpleName.contains("MobileIconVM", ignoreCase = true) ||
                simpleName.contains("CellularIcon", ignoreCase = true)
            ) {
                return candidate
            }
            val hasShowName = runCatching { candidate.getObjectField("showName") != null }.getOrDefault(false)
            val hasLargeVisible = runCatching { candidate.getObjectField("mobileTypeSingleVisible") != null }.getOrDefault(false)
            val hasSmallVisible = runCatching { candidate.getObjectField("mobileTypeVisible") != null }.getOrDefault(false)
            if (hasShowName && (hasLargeVisible || hasSmallVisible)) {
                return candidate
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun safeIsWifiConnected(): Boolean? {
        return runCatching { MobileViewHelper.isWifiConnected() }.getOrNull()
    }

    /**
     * 从 defaultConnections 的 StateFlow 里现读"当前上网方式是否为 WiFi"。
     *
     * 现读而不是只依赖订阅回调，是因为 MIUI 的 flow 不保证在 WiFi 连/断的瞬间就推送，
     * 只等推送就会"慢半拍"。
     */
    private fun readIsWifiDefaultConnection(defaultConnectionsFlow: Any?): Boolean? {
        if (defaultConnectionsFlow == null) return null
        val snapshot = runCatching { getStateFlowValue(defaultConnectionsFlow) }.getOrNull()
        return readIsWifiDefaultFromSnapshot(snapshot)
    }

    /** 从 defaultConnections 的快照值里读"当前上网方式是否为 WiFi"。 */
    private fun readIsWifiDefaultFromSnapshot(snapshot: Any?): Boolean? {
        if (snapshot == null) return null
        return runCatching {
            snapshot.getObjectField("wifi")?.getBooleanField("isDefault")
        }.getOrNull()
    }

    /**
     * 统一"当前上网方式是否为 WiFi"。
     *
     * MIUI 的 defaultConnections flow 不保证在 WiFi 连/断的瞬间更新，所以以实时探测到的
     * WiFi 连接状态为准，只在探测失败时才退回缓存值：
     *  - 实时已连上 WiFi → 一定以上网 WiFi 计（缓存说 false 说明它还没跟上）
     *  - 实时没连上、缓存却说 true → 缓存已过期
     * 旧实现只纠正了后一种，所以"连上 WiFi 后大 5G 图标不消失 / 消失很慢"。
     */
    private fun normalizeWifiDefaultConnection(rawIsWifiDefault: Boolean?, wifiConnectedNow: Boolean?): Boolean? {
        return when {
            wifiConnectedNow == true -> true
            rawIsWifiDefault == true -> false
            rawIsWifiDefault != null -> rawIsWifiDefault
            else -> wifiConnectedNow
        }
    }

    /**
     * WiFi 一连一断就立刻重算可见性。
     *
     * 模块是在 MIUI 绑完视图之后才替换掉可见性 flow 的，MIUI 的 view 早已订阅原 flow，
     * 所以图标最终显隐靠的是模块自己重算后写 isVisible。而重算入口
     * [scheduleRefreshBoundViews] 原来只有 flow 推送/SIM 广播/视图绑定三个触发点，
     * 没有任何一个是 WiFi 连断本身——这就是显隐滞后的直接原因。
     */
    @SuppressLint("MissingPermission")
    private fun registerConnectivityWatcher() {
        if (wifiNetworkCallback != null) return
        val manager = runCatching {
            EzXposed.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull() ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun onWifiChanged(tag: String) {
                logVisibility("wifi", "$tag -> wifiConnectedNow=${safeIsWifiConnected()}")
                // 立即刷一次让 UI 尽快跟上
                scheduleRefreshBoundViews()
                // 再在过渡窗口之后补算，覆盖"默认网络还没切换完"时算出的过渡态结果
                mainHandler.removeCallbacks(wifiSettleRefreshNearRunnable)
                mainHandler.removeCallbacks(wifiSettleRefreshFarRunnable)
                mainHandler.postDelayed(wifiSettleRefreshNearRunnable, WIFI_SETTLE_NEAR_DELAY_MS)
                mainHandler.postDelayed(wifiSettleRefreshFarRunnable, WIFI_SETTLE_FAR_DELAY_MS)
            }

            override fun onAvailable(network: Network) = onWifiChanged("onAvailable")
            override fun onLost(network: Network) = onWifiChanged("onLost")
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = onWifiChanged("onCapabilitiesChanged")
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onSuccess {
                wifiNetworkCallback = callback
                BaseHook.registerNetworkCallbackHotReloadCleanup(manager, callback)
                logVisibility("wifi", "NetworkCallback 已注册")
            }
            .onFailure {
                logVisibility("wifi", "registerConnectivityWatcher failed: ${it.message}")
            }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun bindConstructedMobileViewIfNeeded(rootView: ViewGroup, viewModel: Any) {
        val lastBoundViewModel = runCatching {
            rootView.getAdditionalInstanceFieldAs<Any>(LAST_BOUND_VIEW_MODEL_KEY)
        }.getOrNull()
        if (lastBoundViewModel === viewModel) return
        rootView.setAdditionalInstanceField(LAST_BOUND_VIEW_MODEL_KEY, viewModel)
        bindConstructedMobileView(rootView, viewModel)
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun bindConstructedMobileView(rootView: ViewGroup, viewModel: Any) {
        val interactor = viewModel.getAdditionalInstanceFieldAs<Any>("interactor")
        val subId = rootView.getIntField("subId")
        val slotIndex = SubscriptionManager.getSlotIndex(subId)
        if (slotIndex == -1) return
        cacheBoundView(subId, rootView)
        boundViewModels[rootView] = viewModel

        val mobileGroup = rootView.findViewByIdName("mobile_group") as LinearLayout
        val containerLeft = mobileGroup.findViewByIdName("mobile_signal_container") as ViewGroup

        // 大 5G 样式：移除小 5G ImageView，配置大 5G TextView
        if (showMobileType) {
            containerLeft.findViewByIdName("mobile_type")?.let { containerLeft.removeView(it) }
            val textView = mobileGroup.findViewByIdName("mobile_type_single") as TextView
            if (!getLocation) {
                mobileGroup.removeView(textView)
                mobileGroup.addView(textView)
            }
            if (fontSize != 27) textView.textSize = fontSize * 0.5f
            if (bold) textView.typeface = Typeface.DEFAULT_BOLD
            textView.setPadding(
                dp2px(leftMargin * 0.5f),
                if (verticalOffset != 40) dp2px((verticalOffset - 40) * 0.1f) else 0,
                dp2px(rightMargin * 0.5f),
                0
            )
        }

        if (showMobileType) {
            // ===== 大 5G 可见性 =====
            // 双排模式下 slot 1+ 整个视图已被 MobilePublicHookV 隐藏，跳过避免无效 Flow 订阅
            if (isEnableDouble && slotIndex != 0) {
                return
            }
            if ((mobileNetworkType == 0 || mobileNetworkType == 2) && isEnableDouble) {
                syncWifiDefaultConnectionSnapshot(interactor)
            }

            when (mobileNetworkType) {
                0, 2 -> {
                    // 复用已装好的稳定 flow（兜底），不再 new 新对象，否则已绑定视图会永久失联
                    prepareVisibilityFlow(viewModel, null)

                    val defaultConnections = runCatching {
                        interactor?.getObjectFieldAs<Any>("connectRepo")
                            ?.getObjectFieldAs<Any>("defaultConnections")
                    }.getOrNull()

                    if (defaultConnections != null) {
                        bindMobileTypeSingleVisibilityWithDefaultConnections(
                            viewModel = viewModel,
                            defaultConnections = defaultConnections,
                            subId = subId
                        )
                    } else {
                        bindMobileTypeSingleVisibilityWithWifiFlow(
                            viewModel = viewModel,
                            wifiFlow = viewModel.getObjectFieldAs("wifiAvailable"),
                            subId = subId
                        )
                    }
                }
                1 -> viewModel.setObjectField("mobileTypeSingleVisible", newReadonlyStateFlow(true))
                3 -> viewModel.setObjectField("mobileTypeSingleVisible", newReadonlyStateFlow(false))
                else -> Unit
            }
            applyBoundViewState(rootView)
            return
        }

        // ===== 小 5G 可见性 =====
        // 双排的 slot 1+ 整个视图已被 MobilePublicHookV 隐藏
        if (isEnableDouble && slotIndex != 0) {
            return
        }

        when (mobileNetworkType) {
            2 -> {
                val wifiFlow = viewModel.getObjectFieldAs<Any>("wifiAvailable")
                val initWifiOn = runCatching { getStateFlowValue(wifiFlow) as Boolean }
                    .getOrElse { safeIsWifiConnected() ?: false }
                val flow = newReadonlyStateFlow(!initWifiOn)
                viewModel.setObjectField("mobileTypeVisible", flow)
                MiuiStub.javaAdapter.alwaysCollectFlow(
                    wifiFlow,
                    Consumer<Boolean> { wifiOn -> setStateFlowValue(flow, !wifiOn) }
                )
            }
            1 -> viewModel.setObjectField("mobileTypeVisible", newReadonlyStateFlow(true))
            3 -> viewModel.setObjectField("mobileTypeVisible", newReadonlyStateFlow(false))
            4 -> {
                val wifiFlow = viewModel.getObjectFieldAs<Any>("wifiAvailable")
                val dataConnectedFlow = interactor?.getObjectFieldAs<Any>("isDataConnected")

                // 先读当前值
                val initWifiOn = runCatching { getStateFlowValue(wifiFlow) as Boolean }.getOrDefault(false)
                val initDataConnected = runCatching { getStateFlowValue(dataConnectedFlow) as Boolean }.getOrDefault(false)

                val flow = newReadonlyStateFlow(!initWifiOn && initDataConnected)
                viewModel.setObjectField("mobileTypeVisible", flow)

                var wifiOn = initWifiOn
                var dataConnected = initDataConnected

                MiuiStub.javaAdapter.alwaysCollectFlow(
                    wifiFlow,
                    Consumer<Boolean> { on ->
                        wifiOn = on
                        setStateFlowValue(flow, !wifiOn && dataConnected)
                    }
                )

                dataConnectedFlow?.let {
                    MiuiStub.javaAdapter.alwaysCollectFlow(
                        it,
                        Consumer<Boolean> { connected ->
                            dataConnected = connected
                            setStateFlowValue(flow, !wifiOn && dataConnected)
                        }
                    )
                }
            }
            else -> Unit
        }

        applyBoundViewState(rootView)
    }

    private fun bindMobileTypeSingleVisibilityWithWifiFlow(viewModel: Any, wifiFlow: Any, subId: Int) {
        val visibleFlow = viewModel.getObjectField("mobileTypeSingleVisible")
        val wifiFromFlow = runCatching { getStateFlowValue(wifiFlow) as Boolean }.getOrNull()
        val wifiConnectedNow = safeIsWifiConnected() ?: wifiFromFlow ?: false
        val initialVisible = !wifiConnectedNow
        setStateFlowValue(visibleFlow, initialVisible)
        renderStateStore.updateMobileTypeSingleVisible(subId, initialVisible)
        MiuiStub.javaAdapter.alwaysCollectFlow(
            wifiFlow,
            Consumer<Boolean> { wifiOn ->
                val visible = !wifiOn
                setStateFlowValue(visibleFlow, visible)
                renderStateStore.updateMobileTypeSingleVisible(subId, visible)
                scheduleRefreshBoundViews()
            }
        )
    }

    private fun bindMobileTypeSingleVisibilityWithDefaultConnections(viewModel: Any, defaultConnections: Any, subId: Int) {
        val visibleFlow = viewModel.getObjectField("mobileTypeSingleVisible")
        defaultConnectionsBySubId[subId] = defaultConnections

        val initialIsWifiDefault = normalizeWifiDefaultConnection(
            readIsWifiDefaultConnection(defaultConnections),
            safeIsWifiConnected()
        ) ?: false
        val initialVisible = !initialIsWifiDefault
        setStateFlowValue(visibleFlow, initialVisible)
        renderStateStore.updateMobileTypeSingleVisible(subId, initialVisible)
        MiuiStub.javaAdapter.alwaysCollectFlow(
            defaultConnections,
            Consumer<Any> { conn ->
                val isWifiDefault = normalizeWifiDefaultConnection(
                    readIsWifiDefaultFromSnapshot(conn),
                    safeIsWifiConnected()
                ) ?: false
                val visible = !isWifiDefault
                setStateFlowValue(visibleFlow, visible)
                renderStateStore.updateMobileTypeSingleVisible(subId, visible)
                scheduleRefreshBoundViews()
            }
        )
    }

    private fun syncWifiDefaultConnectionSnapshot(interactor: Any?) {
        val wifiConnectedNow = safeIsWifiConnected()
        val defaultConnections = runCatching {
            interactor?.getObjectFieldAs<Any>("connectRepo")
                ?.getObjectFieldAs<Any>("defaultConnections")
        }.getOrNull()
        isWifiDefaultConnection = normalizeWifiDefaultConnection(
            readIsWifiDefaultConnection(defaultConnections),
            wifiConnectedNow
        )
    }

    /** 监听上网卡切换 + SIM 变化，刷新已绑定的官方 mobile 布局 */
    @Synchronized
    private fun registerDataSimBroadcast(context: Context = EzXposed.appContext) {
        if (broadcastRegistered) return

        val filter = IntentFilter().apply {
            addAction("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED")
            addAction("android.intent.action.SIM_STATE_CHANGED")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                syncDataSimProxiesNow()
                scheduleRefreshBoundViews()
            }
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        broadcastRegistered = true
        BaseHook.registerReceiverHotReloadCleanup(context, receiver)
        BaseHook.putHotReloadRuntimeState(DATA_SIM_CONTEXT_KEY, context)
    }

    private fun syncDataSimProxiesNow() {
        val slot0SubId = renderStateStore.findSlot0SubId()
        showNameFlowProxy.syncFromBroadcast(slot0SubId)
        inOutVisibleProxy.syncFromBroadcast(slot0SubId)
        inOutResIdProxy.syncFromBroadcast(slot0SubId)
        mobileTypeSingleVisibleProxy.syncFromBroadcast(slot0SubId)
        mobileTypeVisibleProxy.syncFromBroadcast(slot0SubId)
    }

    @SuppressLint("NewApi")
    private fun setOnDefaultConnectionsListener(mobileUiAdapter: Any) {
        val miuiInt = runCatching {
            mobileUiAdapter.getObjectFieldAs<Any>("mobileIconsViewModel")
                .getObjectFieldAs<Any>("miuiIntsLazy")
                .callMethodOrNull("get")
        }.recoverCatching {
            mobileUiAdapter.getObjectFieldAs<Any>("mobileIconsViewModel")
                .getObjectFieldAs<Any>("miuiInt")
        }.getOrNull() ?: return

        val defaultConnections = miuiInt.getObjectFieldAs<Any>("connectRepo")
            .getObjectFieldAs<Any>("defaultConnections")

        if (defaultConnectionsCollectorSource !== miuiInt) {
            defaultConnectionsCollectorJob?.cancel()
            val initialRawIsWifiDefault = readIsWifiDefaultConnection(defaultConnections)
            isWifiDefaultConnection = normalizeWifiDefaultConnection(initialRawIsWifiDefault, safeIsWifiConnected())
            scheduleRefreshBoundViews()
            defaultConnectionsCollectorJob = MiuiStub.javaAdapter.alwaysCollectFlow(
                defaultConnections,
                Consumer<Any> { conn ->
                    val rawIsWifiDefault = readIsWifiDefaultFromSnapshot(conn)
                    isWifiDefaultConnection = normalizeWifiDefaultConnection(rawIsWifiDefault, safeIsWifiConnected())
                    scheduleRefreshBoundViews()
                }
            )
            defaultConnectionsCollectorSource = miuiInt
        }
    }

    @SuppressLint("NewApi")
    private fun setOnDataChangedListener(mobileUiAdapter: Any) {
        val miuiInt = runCatching {
            mobileUiAdapter.getObjectFieldAs<Any>("mobileIconsViewModel")
                .getObjectFieldAs<Any>("miuiIntsLazy")
                .callMethodOrNull("get")
        }.recoverCatching {
            mobileUiAdapter.getObjectFieldAs<Any>("mobileIconsViewModel")
                .getObjectFieldAs<Any>("miuiInt")
        }.getOrNull() ?: return

        val dataConnected = miuiInt.getObjectFieldAs<Any>("dataConnected")

        if (dataChangedCollectorSource !== miuiInt) {
            dataChangedCollectorJob?.cancel()
            dataChangedCollectorJob = MiuiStub.javaAdapter.alwaysCollectFlow(dataConnected, Consumer<BooleanArray> { states ->
                renderStateStore.updateDataConnectedBySlots(states)
                scheduleRefreshBoundViews()
            })
            dataChangedCollectorSource = miuiInt
        }
    }

    private fun registerMobileStateCollectors(viewModel: Any, interactor: Any?, subId: Int, slotIndex: Int) {
        renderStateStore.onCollectorAttached(subId, slotIndex)

        collectRenderState(
            viewModel.getObjectField("showName")
        ) { value ->
            renderStateStore.updateShowName(subId, value as? String ?: "")
        }
        collectRenderState(
            viewModel.getObjectField("inOutVisible")
        ) { value ->
            coerceBooleanState(value)?.let { renderStateStore.updateInOutVisible(subId, it) }
        }
        collectRenderState(
            runCatching { interactor?.getObjectField("isDataConnected") }.getOrNull()
        ) { value ->
            coerceBooleanState(value)?.let { renderStateStore.updateDataConnected(subId, it) }
        }
        collectRenderState(
            viewModel.getObjectField("wifiAvailable")
        ) { value ->
            coerceBooleanState(value)?.let { renderStateStore.updateWifiAvailable(subId, it) }
        }
        collectRenderState(
            viewModel.getObjectField("mobileTypeSingleVisible")
        ) { value ->
            coerceBooleanState(value)?.let { renderStateStore.updateMobileTypeSingleVisible(subId, it) }
        }
        collectRenderState(
            viewModel.getObjectField("mobileTypeVisible")
        ) { value ->
            coerceBooleanState(value)?.let { renderStateStore.updateMobileTypeVisible(subId, it) }
        }
    }

    private fun coerceBooleanState(value: Any?): Boolean? {
        return when (value) {
            null -> null
            is Boolean -> value
            else -> {
                runCatching { value.getObjectFieldAs<Boolean>("first") }.getOrNull()
                    ?: runCatching { value.callMethodAs<Boolean>("component1") }.getOrNull()
                    ?: runCatching { value.getBooleanField("value") }.getOrNull()
            }
        }
    }

    private fun collectRenderState(flow: Any?, update: (Any?) -> Unit) {
        if (flow == null) return
        runCatching { update(getStateFlowValue(flow)) }
        MiuiStub.javaAdapter.alwaysCollectFlow(flow, Consumer<Any?> { value ->
            update(value)
            scheduleRefreshBoundViews()
        })
    }

    private fun cacheBoundView(subId: Int, rootView: ViewGroup) {
        val views = boundViews.computeIfAbsent(subId) { linkedSetOf() }
        val iter = views.iterator()
        while (iter.hasNext()) {
            val view = iter.next()
            if (!view.isAttachedToWindow || view === rootView) {
                iter.remove()
                boundViewModels.remove(view)
                lastLoggedVisible.remove(view)
            }
        }
        views.add(rootView)
    }

    private fun scheduleRefreshBoundViews() {
        mainHandler.removeCallbacks(refreshBoundViewsRunnable)
        mainHandler.post(refreshBoundViewsRunnable)
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun refreshBoundViewsNow() {
        boundViews.values.forEach { viewSet ->
            val iter = viewSet.iterator()
            while (iter.hasNext()) {
                val rootView = iter.next()
                if (!rootView.isAttachedToWindow) {
                    iter.remove()
                    boundViewModels.remove(rootView)
                    lastLoggedVisible.remove(rootView)
                    continue
                }
                applyBoundViewState(rootView)
            }
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun applyBoundViewState(rootView: ViewGroup) {
        val viewSubId = runCatching { rootView.getIntField("subId") }.getOrDefault(-1)
        if (viewSubId == -1) return

        val targetSubId = renderStateStore.resolveRenderSubId(
            viewSubId = viewSubId,
            isEnableDouble = isEnableDouble,
            isSingleSimMode = MobileViewHelper.isSingleSimMode()
        )
        val renderState = renderStateStore.snapshot(targetSubId)

        val mobileTypeSingleView = rootView.findViewByIdName("mobile_type_single") as? TextView
        val mobileTypeSmallView = rootView.findViewByIdName("mobile_type") as? ImageView
        val inOutView = rootView.findViewByIdName("mobile_left_mobile_inout") as? ImageView

        val wifiConnectedNow = safeIsWifiConnected()
        // 现读该 subId 的 defaultConnections，避免用滞后的缓存值做判定
        val rawWifiDefault = readIsWifiDefaultConnection(defaultConnectionsBySubId[targetSubId])
            ?: isWifiDefaultConnection
        val normalizedWifiDefaultConnection = normalizeWifiDefaultConnection(rawWifiDefault, wifiConnectedNow)
        val effectiveWifiDefaultConnection = normalizedWifiDefaultConnection ?: wifiConnectedNow
        val resolvedLargeVisible = visibilityResolver.resolveLargeMobileTypeVisibility(
            mobileTypeSingleVisible = renderState.mobileTypeSingleVisible,
            isWifiDefaultConnection = effectiveWifiDefaultConnection,
            fallbackVisible = mobileTypeSingleView?.isVisible ?: false,
            forceDualMode = isEnableDouble && (mobileNetworkType == 0 || mobileNetworkType == 2)
        )
        val effectiveWifiConnected = if (!showMobileType && mobileNetworkType == 2) {
            safeIsWifiConnected() ?: renderState.wifiConnected
        } else {
            renderState.wifiConnected ?: safeIsWifiConnected()
        }
        val resolvedSmallVisible = visibilityResolver.resolveSmallMobileTypeVisibility(
            mobileTypeVisible = renderState.mobileTypeVisible,
            wifiConnected = effectiveWifiConnected,
            dataConnected = renderState.dataConnected,
            fallbackVisible = mobileTypeSmallView?.isVisible ?: false
        )

        if (shouldDriveLargeVisibility()) {
            // 判定结果同时写回 VM 的稳定 flow：视图订阅的就是它，改了它会连带驱动 MIUI 的 binder
            syncLargeVisibilityFlow(rootView, resolvedLargeVisible)
            if (lastLoggedVisible[rootView] != resolvedLargeVisible) {
                lastLoggedVisible[rootView] = resolvedLargeVisible
                logVisibility(
                    "apply",
                    "viewSub=$viewSubId target=$targetSubId rawWifiDefault=$rawWifiDefault " +
                        "wifiNow=$wifiConnectedNow -> large=$resolvedLargeVisible " +
                        "tv=${mobileTypeSingleView != null} attached=${rootView.isAttachedToWindow}"
                )
            }
        }
        viewRenderer.applyLargeMobileType(
            textView = mobileTypeSingleView,
            showName = renderState.showName,
            largeTypeVisible = resolvedLargeVisible
        )
        if (!visibilityResolver.shouldUseDualRowDataSimSync()) {
            viewRenderer.applyInOut(
                indicatorView = inOutView,
                inOutVisible = renderState.inOutVisible
            )
        }
        viewRenderer.applySmallMobileType(
            imageView = mobileTypeSmallView,
            smallVisible = resolvedSmallVisible,
            showName = renderState.showName
        )
    }

    private fun updateMobileTypeDrawable(imageView: ImageView, showName: String) {
        val drawable = imageView.drawable ?: return
        runCatching {
            val currentName = drawable.getObjectFieldAs<String>("mMobileType")
            if (currentName != showName) {
                drawable.setObjectField("mMobileType", showName)
                drawable.callMethodOrNull("measure")
                miuiMobileIconBinder.callStaticMethod("updateMobileTypeLayoutParams", drawable, showName, imageView)
                drawable.callMethodOrNull("invalidateSelf")
            }
        }.onFailure {
            XposedLog.w(TAG, lpparam.packageName, "updateMobileTypeDrawable failed: ${it.message}")
        }
    }
}
