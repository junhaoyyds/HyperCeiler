# Fork 调整日志 / Fork-side Change Log

本文件记录 **junhaoyyds/HyperCeiler** 相对上游 `ReChronoRain/HyperCeiler` 的**全部** fork 侧改动：
自建 CI、桌面底栏 hook 移植、以及主动合并的上游 open PR。**每轮同步后追加一条。**

- 每次构建的 release notes 里同时有「本次 fork 侧合并/调整」段落（由 `nightly.yml` 生成）。
- 上游 PR #1686 / `LDS-XiaoYe` 系列分支的持续跟进见下方表格。

## 规则

1. 上游修复**都要跟**，不限桌面底栏。
2. 合并前先验证对方代码引用的类/方法在本仓库存在；对不上的一律不直接合（见「未追 / 待处理」）。
3. 每合完一轮**必须真编一次**（上游 PR 代码从不经过 CI，合并不冲突 ≠ 能编译）。
4. 合并提交信息统一为 `Merge upstream PR #<编号>: <标题>`，便于 changelog 提取。

## 时间线

| 日期 | 版本 / 提交 | 类型 | 内容 |
|---|---|---|---|
| 2026-09-02 | `743747f89` 等 | CI | 删除上游 `ci_build.yml` / `release_build.yml`（依赖签名与 Telegram secret，fork 环境必红）；新增 `build.yml` / `nightly.yml`（无 secret 依赖，`macos-latest` + JDK 25；Android SDK platform 37 仅 macos runner 预装） |
| 2026-09-09 | `39a8951dd` | 功能 | 桌面底栏 hook 改为**源码原生打包**：native 走 `app/src/main/cpp/` CMake，`native_init.list` 放 `library/libhook/src/main/resources/META-INF/xposed/`。**废止一切构建后重打包/重签**（会破坏 `.so` 4096 页对齐 → `INSTALL_FAILED_INVALID_APK res=-2`） |
| 2026-09-10 | r4703 | 合并 | PR #1686（系统桌面：适配 OS4 底栏柔光玻璃，并修复 OS4 下底栏修改失效） |
| 2026-09-11 | r4710 | 合并 | PR #1686 的 6 个 follow-up：解锁飞入动画同步 keyguardGoingAway、解锁壁纸期间冻结玻璃材质、修复 OS4 实时 dock 跟随、hook 续接缺失时 fail-open、OS4 hooks 迁入 feature 包、license header 对齐 |
| 2026-09-14 | r4722 / `8134c132` | 合并 + 补丁 | PR #1686 第二轮 10 提交（native 层重构为 `nativehook/` + `targets/home/`；**模块库改名 `libhyperceiler_home.so` → `libHyperCeilerNative.so`**）。同时打 **fork 补丁 `8134c132`**：`home_new.xml` 在 `library/core` 引用 `@string/home_is_rust_tips`，而上游只在 app 模块定义该字符串 → 库模块引用不到导致 AAPT 失败；补进 `library/core/src/main/res/values{,-zh-rCN}/strings_app.xml` |
| 2026-09-14 | r4725 / `8bb544e4` | 合并 | `LDS-XiaoYe:os4-personal` 的 2 个提交：修复玻璃面板整体消失（`setVisible()` 幂等化 + 新增 `DockRotationPolicy` 走 display rotation）与横屏返回竖屏后玻璃颜色错乱（新增 `DockGlassGeometry` 钉住 host 的 `mConfigRot`）。顺带新增 3 个解锁动画样式 |
| 2026-09-14 | `cbd66808` | CI | `nightly.yml` 的 changelog 增加「本次 fork 侧合并/调整」段落（`git log --merges`，原有的 `--no-merges` 会把合并记录全部过滤掉），native 库名改为从 `native_init.list` 动态读取（原硬编码旧库名） |
| 2026-09-14 | r4742 / `3a5b7c4d` | 合并 | 上游 open PR 第一批（修复类）：<br>• #1700 修复状态栏温度/电源功率指示器显示模糊<br>• #1622 DeviceHelper 补回 HyperOS 2 版本信息<br>• #1670 修复「允许冻结受保护的应用」无法冻结应用商店<br>• #1690 修复双排信号图标（全局版 HyperOS）<br>• #1629 修复 Pad 引导式访问无法阻止推出手势<br>• #1646 修复解除小部件大小限制在 HyperOS 3 不可用（**部分合并**，见下） |
| 2026-09-14 | r4745 / `0d5c6ee0` | 合并 | #1657 引导式访问增强屏蔽增加「移除快捷窗口按钮」。与已合的 #1629 同改 `UiLockApp.java`，用 `git merge-file` 三方合并后人工裁决 2 处冲突：<br>① `SystemUIApplication.onCreate` 拦截器内**两者并列保留** —— 对方的 `installSystemUiHooks()`（把 SystemUI/WMShell 类加载推迟到 onCreate 前）+ 我们 #1629 的 `reconcileStaleLockState()`；<br>② `stopScreenPinning` 的 hook 辅助方法**保留我方版本**（同时覆盖 `OverviewProxyService$1` 与 `LauncherProxyService$1`，比对方只认后者更健壮）。`BaseHook.java` 采用对方版本（跳过抽象方法，避免 libxposed 102 抛 `IllegalArgumentException`） |
| 2026-09-14 | `fb904dff0` | 合并 | **B/C 档合并（本轮）**：<br>• dock 线：PR #1686 的 4 个新提交（解锁投影 hook、fly-in 样式改名、native 健康检查 worker 修复）<br>• #1667 云服务：国际版 ROM 下为中国区账号解除相册云同步封锁<br>• #1471 桌面布局上限提至 10×20<br>• #1692 powerkeeper：锁定温度最大 fps<br>• #1691 安全服务：背屏支付宝快捷手势<br>• #1698 省电模式允许开启<br>• #1651 OOBE 引导过渡稳定性<br>• #1694 应用详情/桌面卸载时调用第三方包管理器<br>• #1695 Android 17 hooks 适配 + 系统广告/遥测开关（新增 8 个类：`GlobalFileExplorer` / `DisableSecurityAds` / `DisableSecurityTelemetry` / `SkipHomeScan` / `HideHomeEntries` / `HidePersistentNotificationIcons` / `DisableSystemAds` / `DisableSystemTelemetry`）<br>**过程事故**：上一版手工 `base_tree=对方tree` 把 fork 专属文件整片覆盖，已回滚到 `7f7b00b2f` 并用安全合并法重做（详见「事故与教训」） |
| 2026-09-15 | `49df58ef6` | 修复 | **修复 #1690 引入的双排信号图标取色退化**（用户实测：图标颜色不随背景深浅变化，有时恒白、有时恒黑）。<br>**原因**：A 档合入的 #1690 把 `MobileSignalHook.hookDarkMode()` 里的 `MobileViewHelper.collectFlow(...)` **响应式订阅**换成一次性 `tintFlow.getValue()`，并且只在**直接父类**中按 **`parameterCount == 6`** 找 `onDarkChanged` 作兜底 → 取色锁死在 `bind()` 那一刻的瞬时值；hook 挂不上时只打一行 warning、没有任何补救。<br>**修法**：① 恢复 `collectFlow` 订阅（背景/主题变化持续更新）；② 保留一次性 `readDarkInfo()` 兜底（订阅不可用时仍有值，兼顾 #1690 想修的全局版 HyperOS）；③ `onDarkChanged` 改为**沿继承链向上查找且不限定参数个数**。<br>产物 r4784（`HyperCeiler-2.10.166-49df58ef6-r4784-canary.apk`），run `34880244704` success，全项校验 PASS；同步删除受影响的历史版本 r4742 / r4745 / r4782 |
| 2026-09-15 | `2f20fa6e` | 修复 | **修复「移动网络类型图标单独显示」随 WiFi 连断的显隐滞后**（长期问题，非本轮合并引入：`MobileTypeSingle2Hook.kt` 与其 `support/` 三个文件与上游 main **逐字节相同**）。<br>**原因**：模块是在 MIUI 绑完视图之后才替换可见性 flow 的，MIUI 的 view 早已订阅原 flow，所以图标最终显隐靠模块自己重算后写 `isVisible`；而重算入口 `scheduleRefreshBoundViews()` 只有「MIUI flow 推送 / 上网卡·SIM 广播（仅双排模式注册）/ 视图绑定」三个触发点，**没有任何一个来自 WiFi 连断本身**。叠加两处放大：① 判定优先读滞后缓存值 `mobileTypeSingleVisible`（实时状态只在缓存为 null 时才用）；② `normalizeWifiDefaultConnection()` 只做单向纠偏（缓存 true 而 WiFi 断开时能纠正，缓存 false 而 WiFi 已连上时不纠正）。<br>**修法**（仅 `MobileTypeSingle2Hook.kt`，不动设置项与资源）：① 注册 WiFi 网络的 `ConnectivityManager.NetworkCallback`，`onAvailable`/`onLost`/`onCapabilitiesChanged` 立刻重算；② `applyBoundViewState` 按 subId 记住 `defaultConnections` 并**现读** `wifi.isDefault`，缓存只作兜底；③ `normalizeWifiDefaultConnection` 改为以实时 WiFi 连接状态为准（双向纠偏）；④ 补上两个 `bindMobileTypeSingleVisibilityWith*` 订阅回调里漏掉的 `scheduleRefreshBoundViews()`（同文件 `setOnDefaultConnectionsListener` 是有的）；⑤ `defaultConnections` 读写统一收敛到 `readIsWifiDefaultConnection` / `readIsWifiDefaultFromSnapshot`。<br>产物 r4786（`HyperCeiler-2.10.166-2f20fa6ed-r4786-canary.apk`），run `34914898461` success（4 分 43 秒），文件数 2069 不变（仅该文件 blob 变更），全项校验 PASS |
| 2026-09-15 | `22fc6c36` | 修复 | **修正 r4786 引入的回归：断开 WiFi 后大 5G 图标完全不显示**（实测：桌面状态栏没有、下拉通知栏里有）。<br>**原因**：`NetworkCallback` 在 WiFi 连/断的**瞬间**触发重算，但系统此时**默认上网网络（`ConnectivityManager.activeNetwork`）还没切换完** —— 断开 WiFi 时读到的仍是旧网络，于是把「当前是 WiFi 上网 → 隐藏」这个**过渡态**结果写进视图并固化，之后没有新触发把它翻回来；下拉通知栏那份视图是展开时才新建的，读到的是**稳态值**，所以反而正常。<br>**修法**：保留「变化瞬间立即刷一次」，**再在过渡窗口之后补算两次**（800ms / 2500ms，`wifiSettleRefreshNear/FarRunnable`）用稳态值覆盖过渡态结果。<br>⚠️ **踩坑已修**：`MobileTypeSingle2Hook` 是 **`object`** 而不是 `class`，内部**不能**声明 `companion object`（`Modifier 'companion' is not applicable inside 'standalone object'`），常量直接声明在 object 内即可 —— 中间那次 run `34916484354` 因此编译失败。<br>产物 r4789（`HyperCeiler-2.10.166-22fc6c369-r4789-canary.apk`，MD5 `b20a6972347259298acfea7fd4e66820`），run `34916745146` success（4 分 20 秒），文件数 2069 不变（仅该文件 blob 变更），全项校验 PASS；同步删除带回退问题的 r4786 |
| 2026-09-15 | `36229e82` | 修复 | **大 5G 图标显隐第三轮：改为「每个 VM 复用同一个 flow」+ 在 bind 之前安装**（r4789 实测重启后数分钟仍不跟随 WiFi，说明前两轮只修「判定值 / 刷新时机」不够 —— 模块的判定根本没送到状态栏那份常驻视图上）。<br>**根因**：视图 bind 时会订阅 VM 里那个 flow **对象**，之后只认这个对象；而旧实现（含上游）每次 `bindConstructedMobileView` 都 `newReadonlyStateFlow(...)` **造一个新对象**覆盖字段：① 首次 bind 的桌面常驻视图订阅的还是 MIUI 原来的 flow → 模块之后写什么都没用；② 只有重新 bind 过的视图（下拉通知栏展开时新建的那份）会读到当前字段 → 所以它反而是对的。这正好解释「桌面不对、下拉对」「靠别的操作把它带出来」。另注：上游 `c9d471b44`（OS4 适配提交）自述「只验证了双排信号正常工作不代表逻辑正常」，该可见性逻辑在 OS4 上从未被验证过。<br>**修法**（仅 `MobileTypeSingle2Hook.kt`）：① 每个 VM 只在 `additionalInstanceField` 里创建一次可见性 flow，之后一直复用，**不再换对象**；② 在 `ModernStatusBarMobileView.constructAndBind` 的 **`proceed()` 之前**把该 flow 装进 VM（让即将绑定的视图订阅到它），OS4 的 `MiuiMobileIconVMImpl`（binder 直接消费其字段）一并写入；③ `applyBoundViewState` 把判定结果同时写回该 flow，保证之后任何一次 rebind 都拿到同一结论；④ 保留 WiFi 连断即时刷新 + 800ms / 2500ms 过渡窗口后补算；⑤ 新增 `DEBUG_LOG` 诊断日志（init / flow install / wifi 事件 / 判定变化各一行，结果不变不打），下次可直接用 LSPosed 日志定位。<br>产物 r4791（`HyperCeiler-2.10.166-36229e824-r4791-canary.apk`，MD5 `2cd6fb79973b470770d45d10bfb57d50`），run `34919079870` success（6 分 09 秒），文件数 2069 不变（仅该文件 blob 变更），全项校验 PASS；同步删除仍带问题的 r4789 |
| 2026-09-15 | `9bbf3f98` | 回滚 | **放弃修复「移动网络类型图标单独显示」随 WiFi 连断的显隐问题，`MobileTypeSingle2Hook.kt` 整体回滚到上游原版，等官方优化。**<br>三轮尝试（`2f20fa6e` r4786 / `22fc6c36` r4789 / `36229e82` r4791）在真机上均未生效：第一轮补 WiFi 连断的刷新触发、第二轮补过渡窗口后重算、第三轮改为「每个 VM 复用同一 flow 并在 bind 之前安装」，重启后实测仍不跟随。说明该问题不在模块这一侧能触达的判定链路上（很可能是 MIUI 在 bind 结束后自行覆写 `isVisible`）。<br>**回滚方式**：该文件恢复为与上游 `main` 逐字节相同的版本（804 行 / 36097 字节，git blob `1b6c235517cd`，与上游 blob 一致）——已核对确认该文件此前从未被 fork 改动过，回滚后即纯上游原版。<br>**保留** `49df58ef6` 对 `MobileSignalHook.kt` 的双排信号取色修复（独立问题、已确认有效）。<br>产物 r4792，同步删除 r4786 / r4789 / r4791 |
| 2026-09-17 | `55d51aa8da` / r4814 | 合并 ×3 | **本轮三点合并**（三来源同时有更新）：<br>**① 上游 main 2 个提交**（`柒柒喵`，09-16）：`4bd94caab4` AGP→9.4.0 / Gradle→9.7.1 / AndroidX 依赖升级；`55d51aa8da` 资源替换改用 EzHookTool 1.3.0 的 `EzResources` —— **删除 `utils/hookapi/tool/ResourcesTool.java`（−848 行）**，`BaseHook` 的 `getFakeResId`/`setResReplacement`/`setDensityReplacement`/`setObjectReplacement` 改为转发 `EzResources`（签名不变，调用方无需改），并去掉 `onHotReloading` 里「资源替换需重启进程」的闸门。合后复查：`BaseHook.java` 的 fork 补丁（`import Modifier` + `chainAllMethods` 跳过抽象方法 3 行）与 `XposedInitEntry.java` 的 `probeLauncherNativeEntry()` / `NativeHomeHooksOS4` import 均在；`EzResources` 转发已正确融入、`ResourcesTool` 引用已消失。<br>**② PR #1702** `fix: adapt hooks to HyperOS 4.0 (Android 17)`（`mexusbg`，24 文件 `+489/-98`，真机 OS4.0 `nezha` / LSPosed 2.2.0 验证）。修「OS4 上底座反射入口签名变了 → hook 静默失效/抛异常中断 `init()`」：`JavaAdapter#alwaysCollectFlow` 实例方法返回类型在 SDK 37 变 void（旧代码 `as Any` 抛 NPE → **flow 订阅根本没建立**）→ 先探返回类型再决定走实例/静态重载；`StateFlowHelper#setStateFlowValue` 写 `ReadonlyStateFlow` 抛 `MemberNotFoundException` → 抽 `resolveDelegateFlow`（按 `$$delegate_0` 字段名优先、再按类型兜底）并把 fallback 包进 `runCatching`；`PluginFactory` 的 `mComponentName` 在 3.3+ 改名 `componentName`（旧名读不到 → 所有 `miui.systemui.plugin` 功能静默失效）→ 两个字段名都探；另修 `StatusBarIcon` 写 `public static final` 在 A17 抛 `IllegalAccessException`（中断 `init()`）、`DisableThermal` 服务类挪包、`HideLockscreenZenMode` 的类在 4.0 被删、`HideVoWiFiIcon` 依赖的构造器被 R8 内联、`SystemUIB` 补 `HideGestureLine`。**该 PR 改了我们主动删过的 `.github/workflows/ci_build.yml`（modify/delete）→ 走 `hc_merge_safe.py`（临时分支恢复被删 workflow → 真三方合并 → 再剔除）。**合后复查：`JavaAdapter.kt` 88 行（含返回类型判定）、`StateFlowHelper.kt` 108 行（含 `resolveDelegateFlow`）、`PluginFactory.kt` 两个字段名都在；`DeviceHelper.kt` 的 fork 补丁（HyperOS 2 三行 `VersionInfo(35, 2.0f, …)`）保留，且 #1702 把 `VersionInfo(37, 3.0f, 3.3f, SUPPORT_PARTIAL)` 的注释解开也正确落地。<br>**③ PR #1700** `fix: 修复状态栏温度/电源功率指示器显示模糊`（`Andy-Dunne`，1 文件 `+42/-15`）。重写 `BatteryDetailIndicator.kt` 的 `setVisibleState` hook：加 `lastVisibleStates`/`lastShowStates` 判重（状态未变直接返回，不刷屏/不重复请求布局）、同步 `number` + `unit` + 自身可见性、`invalidate()` + 一次性 hardware layer 强制重绘，并修 `updateStatusbarViews` 里 `iconShow==false` 时仍去 push 文本的问题。<br>⚠️ **本轮教训：`#1700` 与 `#1702` 撞同一文件 `BatteryDetailIndicator.kt`**（只对比「PR vs fork」会漏掉「待合 PR 之间」的重叠）→ `/merges` 409。用三方 `git merge-file`（ours=当前 main / base=`compare/<对方head>...main` 的 `merge_base_commit` / theirs=对方 head）解：#1702 对该文件只删了 `param.result = null`，而 #1700 重写的同一段里本来就没有该行 → **取 theirs 即双方意图兼容**（已核 `diff -u theirs merged` 为零差异）。另注：`#1700` 分支自己 merge 过上游 main，其 merge base 是上游 head 而非 `pr.base.sha`。<br>**产物** r4814（`HyperCeiler-2.10.166-6c33bf73c-r4814-canary.apk`，MD5 `ce74f2c945e0455f57611404eea85b24`），run `35194407318`；tree 不变式全过（blob 2069 不变、DockGlass 16、cpp 19、workflows 仅 `build.yml`+`nightly.yml`、无 `jniLibs`、`native_init.list`=`libHyperCeilerNative.so`、`home_is_rust_tips` en/zh 均在）。
| 2026-09-20 | `5b0cf498e5` / r4817 | 合并 | **PR #1707** `fix(os3): 修复 OS3 的一些功能并增加两个功能`（`XUANHLGG`，42 文件 `+953/-74`，小米 17 Ultra `OS3.0.15.0` 真机验证；base 就是已合的 `55d51aa8da`，无旧 API 包袱）。<br>**修 7 项**：隐藏手势提示线、状态栏网速刷新频率、A16 高音量安全限制、原生照片/文件选择器、原生安装器、禁用频繁安装检查、禁用风险检测；**新增 2 项**：禁用 ICP 备案提示、点控制中心亮度滑块图标切换自动亮度（`BrightnessIconAutoToggle.kt`）。<br>**冲突处理**：42 文件走 `hc_merge_manual.py` 三方合并，40 个干净、2 个冲突共 3 处标记，全部是「同一位置各插不同东西」的良性冲突，**两侧并列保留** ——<br>① `app/SystemUI/SystemUIB.java` **2 处**：`// 导航栏` 段我们插的是 #1702 的 `HideGestureLine`、它插的是 `HideNavigationBar`（两者语义不矛盾：#1702 刻意不注册 launcher 侧同名类以避免改动手势热区，而 #1707 新增的是**另一个包**下的 `HideNavigationBar`，走 `NavigationHandle.setWillNotDraw(true)` 只禁绘制、保留输入区）；<br>② `base/XposedInitEntry.java` **1 处**：fork 的 `probeLauncherNativeEntry()` 与它的初始化重构都插在 `initPrefs()` 的 `catch` 之后。对方本次把模块初始化重构为 `initializeModuleState` + `registerTargetReadyCallback`，热重载改用 `EzXposed.handleHotReloadedWithTargetReady`（**已核实该 API 存在于 EzHookTool 1.3.0**，我们用的正是 1.3.0，不会编译失败），旧字段 `initializeEzXposed` 随重构消失 —— 解冲突时保留 fork 的 `probeLauncherNativeEntry()` + `NativeHomeHooksOS4` import，接受对方的初始化拆分。<br>其余撞点均为多语言 `strings_app.xml`（17 个），fork 补丁 `home_is_rust_tips`（values 第 1378 行 / zh-rCN 第 1328 行）与 PR 的 hunk（968+1448 / 919+1401）相距很远，自动合；`scope.list`（fork 补丁 `com.miui.securitycore`）同样自动合。<br>**⚠️ 本轮事故（已修）**：`hc_merge_manual.py` 的 `git merge-file` 第三个输入（theirs）被误传成 ours 的副本 → 合并结果恒等于 ours → 所有「我们没改、对方改了」的文件被**静默跳过**，第一次提交只带进 6 个新增文件（42 文件里 36 个丢失），已即时回滚 main 到 `2486c7e74d`。修复内容：① 第三参数改正为 theirs；② 加 `anomalies` 兜底（出现「ours==base 而 theirs!=base 却被跳过」直接拒绝提交）；③ 补 `do_commit` 的提交后自检（新提交相对 ours 的改动文件集必须覆盖对方改动文件集，42/42 才算过）；④ 把此前定义了却从未使用的 `--apply` 分支真正实现（避免重算覆盖人工解冲突的结果）。<br>**合后复查**：blob 2069 → 2075（+6 新增文件、**零删除**）；`cpp/` 19 不变、`DockGlass*` 16、`.github/workflows/` 仅剩 `build.yml`+`nightly.yml`、无 `jniLibs/`；fork 补丁 9 项逐条在位（`native_init.list`、`scope.list` 的 `com.miui.securitycore`、`XposedInitEntry` 的 `probeLauncherNativeEntry`+`NativeHomeHooksOS4`、`SystemUIB` 的 `HideGestureLine`、`BaseHook` 跳抽象方法、`DeviceHelper` 的 HyperOS 2 三行 `VersionInfo(35, 2.0f, …)`、两份 `home_is_rust_tips`、`MobileSignalHook` 的 `collectFlow`）。<br>产物 r4817（`HyperCeiler-2.10.166-5b0cf498e-r4817-canary.apk`，MD5 `18830d301db89a98dad125206f3c7912`），run `35485431724` success（4 分 20 秒），全项校验 PASS（`.so` 均 `STORED` 且 4096 对齐、`native_init.list` = `libHyperCeilerNative.so`、dex 命中 `DockGlass`×44 / `NativeHomeHooksOS4`×1 / 旧库名 ×0、v2 签名在位；`scope.list` 已含 PR 新增的 `com.android.packageinstaller`）。 |

## 事故与教训（2026-09-14）

**事故**：手工解 modify/delete 冲突时，若用**对方的 tree 作 `base_tree`**（`hc_apply_tree.py` 的旧做法），
会把 fork 专属文件**整片抹掉** —— CI workflow、`META-INF/xposed/native_init.list`、`app/src/main/cpp/**`、
`DockGlass*` 全部消失，只剩上游内容。当时的表现是「文件数从 2506 → 2389」「DockGlass 16 → 0」「cpp 22 → 0」。
已通过 `PATCH /git/refs/heads/main {sha: 最后一个健康提交, force:true}` 回滚重做。

**正确做法（安全合并）**：
- 首选 GitHub merge API（`base=main`，真实三方合并）。
- 撞 modify/delete 冲突时：在**临时分支**上把被我们删除的 workflow 按对方版本临时恢复 → 让 merge API 在临时分支上完成合并 → 拿它的合并 tree 作底、显式删掉那两个文件 → 以 `[我们的 main, 对方 head]` 为双亲建正式 merge commit。
- 仍冲突则逐文件 `git merge-file -p ours base theirs`（base 取 `compare/<head>...main` 的 `merge_base_commit`），
  **`base_tree` 必须是我们的 tree**，只把合并结果覆盖上去。

**每轮合并后必查的 tree 不变式**（`hc_verify_tree.py`）：文件数不减、`DockGlass*` = 16、`cpp/` = 19、
`.github/workflows/` 只有 `build.yml` + `nightly.yml`、`native_init.list` 在、`jniLibs/` 不存在、`FORK_LOG.md` 在。

## 上游 API 迁移对照（2026-07-19 `fe998bf97`，与 OS4 无关）

上游提交 `fe998bf97`「refactor: migrate hooks to EzHookTool and Xposed API 102」（300 文件）把自研 hook 框架
整体换成 EzHookTool 库。**这是模块内部 API 迁移，跟 HyperOS 版本无关**——上游 main、我们 fork、`LDS-XiaoYe:os4-branch`
三方都已在迁移后状态。凡是引用下列旧路径的 PR，都是 2026-07-19 之前拉的分支且从未 rebase，**不能直接合、需要改 import**：

| 旧路径（PR 里写的） | 现行路径 |
|---|---|
| `…libhook.callback.IMethodHook` / `IReplaceHook` | `io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook` / `IReplaceHook` |
| `…hookapi.tool.afterHookMethod` / `beforeHookMethod` | `io.github.lingqiqi5211.ezhooktool.xposed.dsl.afterHookMethod` / `beforeHookMethod` |
| `…hookapi.tool.callMethod` / `callMethodAs` | `io.github.lingqiqi5211.ezhooktool.core.callMethod` / `callMethodAs` |
| `…hookapi.tool.setIntField` / `getAdditionalInstanceField` / `setAdditionalInstanceField` | `…libhook.base.BaseHook` 的**静态方法**（同名） |
| `…hookapi.tool.EzxHelpUtils` | 已删除，用 EzHookTool 的 `xposed.dsl.*` / `core.*` |

### 依赖坐标对照（`gradle/libs.versions.toml`）

| 迁移前（`7266aaa0`） | 迁移后（≈ 2026-07-19 起） |
|---|---|
| `io.github.kyuubiran.ezxhelper:core` = 3.1.1-rc1 | **删除** |
| — | `io.github.lingqiqi5211.ezhooktool:core` = 1.1.3 |
| — | `io.github.lingqiqi5211.ezhooktool:hook-xposed-102` = 1.1.3 |
| `io.github.libxposed:api` = 101.0.1 / `:service` = 101.0.0 | `io.github.libxposed:api` = **102.0.0** / `:service` = 102.0.0 |

### 旧的自研工具层是干什么的（`fe998bf97` 已删）

`library/libhook/.../utils/hookapi/tool/EzxHelpUtils.kt`（968 行，工具入口）+ `KtHelpUtils.kt`（Kotlin 扩展）
+ `tool/internal/Ezx{Class,Field,Method,Hook,Application,ModuleHolder}Helper.kt`（内部实现）
+ `callback/IMethodHook.java`、`callback/IReplaceHook.java`（方法 hook 的 before/after 与 replace 回调接口）。

它是**所有**功能 hook 的公共底座，只做四件事：
1. 反射查找 —— `findClass` / `findClassIfExists` / 找方法、构造器、字段；
2. 字段与方法操作 —— `get/setIntField`、`callMethod` / `callMethodAs`、`getAdditionalInstanceField`；
3. 注册 hook —— `hookMethod` / `beforeHookMethod` / `afterHookMethod` / `hookAllMethods`，把 libxposed 的 `Hooker` 包成 `IMethodHook` / `IReplaceHook`；
4. 模块生命周期 —— 持有 `XposedModule` 实例、Application 创建/attach 回调。

迁移后**保留**的同类工具（与 hook 底座无关）：`AppsTool.java`、`MiuiDialog.java`、`MiuixPreferenceUtils.kt`、`ResourceViewUtils.kt`、`ResourcesTool.java`、`callback/ICrashHandler.kt`。

> 另外注意：「文件按 import 猜路径」的校验会误报。`DataSimFlowProxy`（在 `MobileViewHelper.kt`）、
> `KotlinJob`（在 `JavaAdapter.kt`）、`findViewByIdName`（在 `ResourceViewUtils.kt`）、`clazzMiuiBuild`（在 `loadClassByLazy.kt`）、
> `getDeviceToken`（在 `DeviceHelper.kt`）都**存在**，只是声明文件名与 import 末段不同。

## 未追 / 待处理

| PR | 原因 | 处理 |
|---|---|---|
| #1643 opt: DisableReroute target change | 引用旧 `libhook.callback.IMethodHook`（见上表），作者未 rebase | 按 EzHookTool 改 import 后可移植 |
| #1646 的 `ResizableWidgets.kt` | 依赖旧 `utils.hookapi.tool.hookAllMethods`；其 XML（`home_other_new.xml` 的 SwitchPreference）、3 处文案、`HomePad`/`HomePhone` 的 hook 注册已采用 | 待上游合并后跟 |
| #1610 双击状态栏 / #1472 桌面布局 / #1634 隐藏手势提示线 / #1638 游戏二倍速 / #1574 shortcut 强制停止 | 同上，均基于 2026-07-19 前的旧 hook API（`IMethodHook` / `afterHookMethod` / `EzxHelpUtils` 等） | 需按对照表改 import + 过一遍编译 |
| #1687 / #1688 perf（tryigit） | 两个 PR 内容重复；且都改 `settings.gradle.kts`（本 fork CI 的 `GIT_ACTOR`/`GIT_TOKEN` 逻辑所在） | 二选一，合并后须重跑 CI |
| #1625 / #1621 dependabot | 改的是本 fork 已删除的 `ci_build.yml` / `release_build.yml` | 忽略 |
| #1669 Android 17 适配 | 改的文件与已合的 #1695 重叠 | #1695 已合；#1669 待其 rebase 后再评估 |

## 校验清单（每轮必过）

- `libHyperCeilerNative.so` / `libdexkit.so`：`STORED` 且 `data_off % 4096 == 0`
- `META-INF/xposed/native_init.list` 内容 == APK 内实际存在的 native 库名
- `scope.list` 含 `com.miui.home`
- dex 命中 `DockGlass` / `DockUnlockReveal` / `HomeDockWindow` / `NativeHomeHooksOS4` / `DockGlassRecoveryGate` / `RecoverableCalls`
- `app/src/main/jniLibs/` 必须**不存在**（否则预编译 `.so` 会覆盖 CMake 源码构建）
- v1 签名可缺席（minSdk ≥ 24），但 APK Signing Block + v2 必须在位
- fork 补丁 `home_is_rust_tips` 仍在 `library/core/.../strings_app.xml`（上游修好后需删除，否则重复定义）
