# Muse Runtime Pack（内置 Node 运行时）

Muse 应用沙盒的「扩展运行时」构建工具。为 MCP stdio server、技能脚本、终端提供
真实 Node.js / npm 执行环境，不依赖用户安装 Termux。

## 架构

```
app/
├── src/main/jniLibs/<abi>/          ← 可执行部分（随 APK 分发，安装时解压到 nativeLibraryDir）
│   ├── libmuse_node.so              ← Node 24.18.0 LTS（Termux 官方构建，NDK r29，16KB 页对齐）
│   ├── libc++_shared.so / libssl.so / libcrypto.so / libcares.so
│   ├── libicui18n.so / libicuuc.so / libicudata.so / libsqlite3.so / libz.so
│   （全部经 patchelf 规范化：改名 / SONAME / DT_NEEDED 重写 / RPATH=$ORIGIN，自包含）
└── src/main/assets/
    └── muse-runtime-assets.zip      ← 数据部分（npm 11.20.0，纯 JS，首次使用时解压）
```

**为什么这样分层**（Android W^X 限制）：
- targetSdk 29+ 的应用不可 `exec` 自己数据目录里的文件；
- 但 `nativeLibraryDir`（APK 安装时解压出的只读区域）不受限，可安全 exec / dlopen；
- 因此「可执行文件」必须随 APK 以 `lib*.so` 命名分发（jniLibs 规范）；
- 「纯数据」（npm 的 JS 文件）放 assets，解压到数据目录使用（node 读取 JS 不需要 exec 权限）。

**消费方**：`app/src/main/java/io/zer0/muse/runtime/MuseRuntime.kt`（路径 / 解压 / 子进程环境）。

## 重建

在 WSL / Linux 下：

```bash
sudo apt-get install -y curl ca-certificates patchelf   # 一次性
bash tools/runtime-pack/build-runtime-pack.sh both      # arm64 + x86_64 全量重建
```

产物直接写入 `app/src/main/jniLibs/` 与 `app/src/main/assets/`（这两个位置已 gitignore，二进制不入库）。

## 验证

```bash
# 模拟器（需先启动 AVD 且 adb devices 在线）
./gradlew :app:connectedDebugAndroidTest -PnoUniversal \
  "-Pandroid.testInstrumentationRunnerArguments.class=io.zer0.muse.runtime.RuntimeSelfCheckTest" \
  "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

8 项自检覆盖：二进制存在 → exec → JS 求值 → 动态库加载（zlib/crypto/ICU）→
TLS 网络 → npm 解压 → npm 执行 → symlink exec（P1 名字解析基础）。

## ⚠️ 陷阱清单（勿踩）

1. **AGP strip 会破坏 patchelf 产物**：`stripDebugDebugSymbols`（llvm-strip）对 patchelf
   处理过的 ELF 重排段布局，导致运行时段映射错乱、SIGSEGV（秒崩、无输出、无 tombstone）。
   所有运行时库必须列入 `app/build.gradle.kts` 的 `jniLibs.keepDebugSymbols`。
   验证：`stripped_native_libs/.../lib*.so` 与 `jniLibs/.../lib*.so` md5 必须一致。
2. **`extractNativeLibs` 必须为 true**（build.gradle 已通过 `useLegacyPackaging = true` 设置），
   否则库不落盘、无法 exec。
3. **升级版本时同步改三处**：本目录脚本常量 → `MuseRuntime.RUNTIME_DATA_VERSION`
   → `keepDebugSymbols`（仅新增库时）。
4. **安装链路无损**：安装器的「Punching extracted elf file」只是对零页打洞省空间，
   内容无损（已实测 md5 一致）。

## 版本锁定（2026-09）

| 组件 | 版本 | 来源 |
|---|---|---|
| Node.js | 24.18.0 LTS | Termux nodejs-lts 24.18.0-1 |
| npm | 11.20.0 | Termux npm（all） |
| libc++ | 30 | Termux libc++ |
| OpenSSL | 3.6.3 | Termux openssl |
| c-ares | 1.34.8 | Termux c-ares |
| ICU | 78.3 | Termux libicu |
| SQLite | 3.53.4 | Termux libsqlite |
| zlib | 1.3.2 | Termux zlib |

> 运行时二进制为 GPLv3 项目的一部分，来源为 Termux 官方预编译包（各组件保留其原始
> 开源许可证），构建脚本与处理流程见本目录。
