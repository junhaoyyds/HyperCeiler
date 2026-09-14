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

**每轮合并后必查的 tree 不变式**（`hc_verify_tree.py`）：文件数不减、`DockGlass*` = 16、`cpp/` = 22、
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
