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

## 未追 / 待处理

| PR | 原因 | 处理 |
|---|---|---|
| #1643 opt: DisableReroute target change | 代码引用 `com.sevtinge.hyperceiler.libhook.callback.IMethodHook` 与 `io.github.kuuubiran.ezxhelper.*`，**本仓库均不存在**（`libhook/callback/` 下只有 `ICrashHandler.kt`）；作者基于旧 API 编写 | 不直接合，需按当前 API 移植 |
| #1657 fix: 引导式访问增强屏蔽增加移除快捷窗口按钮 | 与已合的 #1629 同改 `UiLockApp.java`，且其分支落后 #1629 三个提交（`diverged`） | 等作者 rebase 或上游合并后再跟 |
| #1646 的 `ResizableWidgets.kt` | 新代码依赖 `utils.hookapi.tool.hookAllMethods` / `callMethodOrNull`，本仓库无此 API；但其 XML（`home_other_new.xml` 的 SwitchPreference）、3 处文案、`HomePad`/`HomePhone` 的 hook 注册已采用 | 待上游合并后跟 |
| #1687 / #1688 perf（tryigit） | 两个 PR 内容重复；且都改 `settings.gradle.kts`（本 fork CI 的 `GIT_ACTOR`/`GIT_TOKEN` 逻辑所在） | 二选一，合并后须重跑 CI |
| #1625 / #1621 dependabot | 改的是本 fork 已删除的 `ci_build.yml` / `release_build.yml` | 忽略 |
| #1695 / #1669 Android 17 适配 | 两者改同一批文件，互相冲突；体量大 | 待评估 |

## 校验清单（每轮必过）

- `libHyperCeilerNative.so` / `libdexkit.so`：`STORED` 且 `data_off % 4096 == 0`
- `META-INF/xposed/native_init.list` 内容 == APK 内实际存在的 native 库名
- `scope.list` 含 `com.miui.home`
- dex 命中 `DockGlass` / `DockUnlockReveal` / `HomeDockWindow` / `NativeHomeHooksOS4` / `DockGlassRecoveryGate` / `RecoverableCalls`
- `app/src/main/jniLibs/` 必须**不存在**（否则预编译 `.so` 会覆盖 CMake 源码构建）
- v1 签名可缺席（minSdk ≥ 24），但 APK Signing Block + v2 必须在位
- fork 补丁 `home_is_rust_tips` 仍在 `library/core/.../strings_app.xml`（上游修好后需删除，否则重复定义）
