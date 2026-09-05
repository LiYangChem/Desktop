# Docear-Modified 1.0.0 Portable 发布记录

日期：2026-09-02 ｜ 阶段：Portable 打包（无业务代码改动）

## 1. 启动方式分析

- `freeplanelauncher.jar`（Main-Class: `org.freeplane.launcher.Launcher`）以**自身 jar 所在目录**为根，设置 Knopflerfish OSGi 属性（`bundlestorage=memory`、`gosg.jars=reference:file:<dir>/core/`、`globalresourcedir=<dir>/resources`），再用 `props.xargs` + `init.xargs` 启动 `framework.jar`（Knopflerfish 4.1.10）。
- `init.xargs` 只 `-istart org.freeplane.core`；其余 11 个插件由 `ActivatorImpl.loadPlugins()` 递归扫描 `plugins/` 目录（exploded OSGi bundle）和用户目录 `~/.docear` 动态安装。
- **结论：一切路径相对 launcher jar 所在目录解析 → 应用天然可重定位，无需改任何代码即支持 Portable。**

## 2. Java 版本

- 字节码目标：`-source/-target 1.6`（JDK11+ 无法编译）；构建用 Temurin JDK 8u504。
- **内置运行时：`runtime/` = Temurin JRE 8 (8u504)**，从 `tools/jdk8u504-b01/jre` 复制。运行日志确认 `java_version = 1.8.0_504`。
- 不采用 jpackage：其要求 JDK 14+ 运行时，本应用为 1.6 时代 Swing/OSGi 代码栈，改用 launch4j（jre path=runtime）。

## 3. 目录结构

```
Docear-Modified-1.0.0-Portable\
├── Docear.exe                  launch4j 3.50 生成（jre path=runtime，优先内置 JRE）
├── start-docear.bat            批处理入口（runtime\bin\javaw.exe 优先）
├── start-docear-console.bat    调试入口（java.exe，日志可见）
├── README-portable.txt         使用说明
├── freeplanelauncher.jar / framework.jar / init.xargs / props.xargs
├── core\org.freeplane.core\    核心 + 全部依赖 jar（jsoup/xstream/JabRef 等）
├── plugins\                    11 个 OSGi 插件（bibtex/core/pdfutilities/services/…）
├── resources\ doc\ scripts\
├── docear.ico / docear-portable.l4j.xml   Docear.exe 重建素材
└── runtime\                    Temurin JRE 8u504（约 97 MB）
```

裁剪：删除 8 个 `*-javadoc.jar`/`*-sources.jar`（不在任何 Bundle-ClassPath 中）；未带入旧 `docear.exe/docear.bat/docear.sh`（依赖系统 Java/注册表）。

## 4. 用户数据（与程序目录完全分离）

- `~/.docear/`：设置(auto.properties)、日志(logs/)、模板、用户插件
- `~/Docear/`：默认工作区（项目、.bib 参考文献库、思维导图）
- **实测程序目录零写入**（多次运行后 build/release 目录无新增文件）→ 可放只读位置。

## 5. 干净环境验证（模拟无 Java 电脑）

环境：`JAVA_HOME=` 清空、`PATH=C:\Windows\System32`（无任何 java）。

| 验证项 | 结果 |
|---|---|
| 原始目录 Docear.exe 启动 | ✅ javaw PID 存活，日志完整（11 插件、Framework launched） |
| 原始目录 javaw 直接启动 | ✅ |
| **ZIP 解压到新位置后启动** | ✅ PID 7580，12 bundle 全启动，References 数据库 5 条加载，MindMap 模式，zh_CN 界面 |
| 数据跨重启持久 | ✅ 多次启停复用同一 ~/.docear 与 ~/Docear 项目，My Thesis.bib 稳定加载 |

注：沙箱内经 `bash→cmd→start→javaw` 三层进程链启动 bat 会被沙箱 job 对象清理子进程（测试环境伪象）；用户桌面双击场景（explorer 直接启动）无此问题。GUI 内 9 项功能（PDF/References/Crossref/GS/MindMap/Annotation/保存/重启）请双击 `Docear.exe` 人工复核。

## 6. 产物与重建

- `release\Docear-Modified-1.0.0-Portable\`（约 163 MB）
- `release\Docear-Modified-1.0.0-Portable.zip`（**92.0 MB**，393 文件，解压得同名顶层目录）
- 重建：`bash tools/make-portable.sh`（需 ant build 产物 + tools/jdk8u504-b01 + tools/launch4j；launch4j 本身用内置 JRE 运行，构建机无需装 Java）
- 新增文件：`tools/make-portable.sh`、`tools/docear-portable.l4j.xml`、`tools/start-docear.bat`、`tools/start-docear-console.bat`、`tools/README-portable.txt`（以上为规范副本，portable 目录内为发布副本）；下载 `tools/launch4j-3.50-win32.zip`
- **业务代码零改动**（CrossrefProvider/GS Provider/Reference/PDF/Annotation/MindMap 均未触碰）

## 7. 无 Java 电脑测试步骤

1. 拷贝 `Docear-Modified-1.0.0-Portable.zip` 到目标机
2. 解压（右键 → 全部解压）
3. 双击 `Docear.exe`（或 `start-docear.bat`）
4. 若要看日志：双击 `start-docear-console.bat`，或查看 `%USERPROFILE%\.docear\logs\log.0`
