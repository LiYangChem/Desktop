# Docear 剪贴板粘贴化学结构图片 —— 透明通道丢失分析

> 问题：从 ChemDraw 复制化学结构 → 在 Docear 中 Paste → 弹出保存 PNG 窗口 → 保存出的 PNG 背景为黑色，透明背景丢失。
> 约束：本阶段只做代码分析，**不修改任何源码**、不升级依赖、不改 ChemDraw / PDF / Reference / MindMap / Metadata Provider 逻辑。

---

## 0. 结论速览（TL;DR）

| 项目 | 结论 |
|------|------|
| 最可能的根因 | Docear 读取剪贴板图像时**只使用 `DataFlavor.imageFlavor`**，在 Windows 上该 flavor 由 JRE 映射到 **CF_DIB（24 位位图，无 alpha 通道）**，ChemDraw 结构中的透明背景在 DIB 中已被填充为黑色像素。Docear 拿到的是一个**已经丢失 alpha、黑底不透明**的 `BufferedImage`。 |
| 问题归属 | 类型 **B**（Clipboard → BufferedImage 时 alpha 丢失），发生点位于 **Java AWT 层** `getTransferData(DataFlavor.imageFlavor)`，而非 Docear 的 PNG 编码层。 |
| 直接黑源 | 不是 `ImageIO.write` 造成的，也不是 Docear 主动把透明画黑；是 `getTransferData(DataFlavor.imageFlavor)` 返回的图像本身已黑底。 |
| 最小修复 | 在 `MClipboardController.getFlavorHandler()` 中，**优先检测并读取带 alpha 的图像 flavor（如 `image/png`）**，再回退到 `imageFlavor`；`ImageIO.read(InputStream)` 解码以保留 alpha。 |
| 是否影响现有功能 | 方案为“优先选择带 alpha flavor + 回退原逻辑”，不影响非透明图片 / JPG / 其他剪贴板图片 / 文件列表粘贴。 |

---

## 1. Paste 功能入口与完整调用链

### 1.1 入口：Docear 覆写了 Paste 动作

Docear 通过 `docear_plugin_pdfutilities` 插件注册了自己的 Paste 动作，覆盖 Freeplane 默认的 `PasteAction`。

| 层级 | 类 / 方法 / 文件 |
|------|------------------|
| Docear Paste 入口 | `org.docear.plugin.pdfutilities.actions.DocearPasteAction.actionPerformed(ActionEvent)` |
| 源文件 | `docear_plugin_pdfutilities/src/org/docear/plugin/pdfutilities/actions/DocearPasteAction.java`（第 32–58 行） |

`DocearPasteAction` 的逻辑（第 33–57 行）：
1. `MClipboardController clipboardController = (MClipboardController) ClipboardController.getController();`
2. `Transferable transferable = clipboardController.getClipboardContents();`
3. 先尝试按**文件列表 / URI 列表**处理（第 40–53 行）；如果不是文件，则调用 `clipboardController.paste(...)`（第 57 行）。
4. 真正的图像处理不在 `DocearPasteAction` 里，而在 `MClipboardController` 的 flavor 分发中。

### 1.2 底层剪贴板读取

| 层级 | 类 / 方法 / 文件 |
|------|------------------|
| 获取系统剪贴板 | `ClipboardController` 构造器：`Toolkit.getDefaultToolkit().getSystemClipboard()` |
| 源文件 | `freeplane/src/org/freeplane/features/clipboard/ClipboardController.java`（第 77–84 行） |
| 读取内容 | `ClipboardController.getClipboardContents()` → `clipboard.getContents(this)` |
| 源文件 | 同上（第 250–252 行） |

### 1.3 图像保存 PNG 的核心（关键代码）

| 层级 | 类 / 方法 / 文件 |
|------|------------------|
| Flavor 分发 | `MClipboardController.getFlavorHandler(Transferable)` |
| 源文件 | `freeplane/src/org/freeplane/features/clipboard/mindmapmode/MClipboardController.java`（第 511–583 行） |
| 图像 Flavor 分支 | `if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) { BufferedImage image = (BufferedImage) t.getTransferData(DataFlavor.imageFlavor); return new ImageFlavorHandler(image); }`（第 572–581 行） |
| 图像处理内部类 | `MClipboardController.ImageFlavorHandler`（第 387–451 行） |
| 透明重建（无效） | `ImageFlavorHandler` 构造器：`new BufferedImage(..., BufferedImage.TYPE_INT_ARGB)` + `Graphics2D.drawImage`（第 391–399 行） |
| 弹出保存窗口 | `ImageFlavorHandler.paste(...)` 中的 `JFileChooser.showSaveDialog(...)`（第 420–426 行） |
| 写 PNG | `ImageIO.write(image, "png", file)`（第 441 行） |

### 1.4 拖放（Drop）路径与 Paste 共用同一处理链

- `MNodeDropListener`（`freeplane/src/org/freeplane/view/swing/ui/mindmapmode/MNodeDropListener.java` 第 132、186 行）最终也调用 `((MClipboardController) ClipboardController.getController()).paste(...)`。
- 因此**拖放图像与粘贴图像走完全相同的 `getFlavorHandler()` → `ImageFlavorHandler` 链路**，透明丢失问题同样会发生在拖放图片上。

---

## 2. Clipboard DataFlavor 分析

### 2.1 Docear 实际支持的 DataFlavor（`MindMapNodesSelection` 静态块，第 43–54 行）

| Flavor 常量 | MIME 字符串 |
|-------------|-------------|
| `mindMapNodesFlavor` | `text/freeplane-nodes; class=java.lang.String` |
| `rtfFlavor` | `text/rtf; class=java.io.InputStream` |
| `htmlFlavor` | `text/html; class=java.lang.String` |
| `fileListFlavor` | `application/x-java-file-list; class=java.util.List` |
| `dropActionFlavor` | `text/drop-action; class=java.lang.String` |

另有 JRE 内置 flavor 被直接使用：`DataFlavor.stringFlavor`、**`DataFlavor.imageFlavor`**。

### 2.2 `getFlavorHandler()` 的检测顺序（`MClipboardController` 第 511–583 行）

1. `MindMapNodesSelection.mindMapNodesFlavor`（第 512 行）
2. `MindMapNodesSelection.fileListFlavor`（第 522 行）
3. `MindMapNodesSelection.htmlFlavor`（第 534 行）
4. `DataFlavor.stringFlavor`（第 562 行）
5. **`DataFlavor.imageFlavor`（第 572 行）**

### 2.3 关键结论

- Docear **从未定义/检测** `image/png`、`application/x-java-image` 之外的任何“带 alpha”的图片 flavor。
- `DataFlavor.imageFlavor` 的 MIME 是 `image/x-java-image; class=java.awt.Image`，在 Windows 上 JRE 把它映射到本地格式 **CF_DIB**（`sun.awt.windows.WDataTransferer`）。
- 因此：ChemDraw 复制结构后，Java 实际拿到的数据类型是 **`getTransferData(DataFlavor.imageFlavor)` 返回的 `BufferedImage`**，其来源是剪贴板里的 **CF_DIB（Device Independent Bitmap）**，而非 ChemDraw 可能同时提供的带透明通道的 PNG/EMF 格式。

### 2.4 为什么 `DataFlavor.imageFlavor` 对应 CF_DIB 会丢 alpha

- CF_DIB（`BITMAPINFOHEADER`）常用 24 位（每像素 RGB 三字节）或 32 位（BI_RGB 时第 4 字节为保留位）。
- 24 位 DIB **没有 alpha 通道**；透明区域在 DIB 数据中已经被填成某种底色（ChemDraw 通常填成**黑色 RGB(0,0,0)**）。
- JRE（Windows 下经 GDI+ / native 转换）把 CF_DIB 转成无 alpha 的 `BufferedImage`（典型为 `TYPE_INT_RGB` / `TYPE_3BYTE_BGR`），透明背景于是变成了实打实的黑色不透明像素。
- Docear 代码里有一处被注释掉的 debug 片段（`MClipboardController` 第 668–671 行）正好印证作者曾经想打印 `t.getTransferDataFlavors()` 来排查 flavor，但未启用。

---

## 3. 透明通道（Alpha Channel）分析

### 3.1 逐问题回答

| 问题 | 回答 | 依据 |
|------|------|------|
| 1. Clipboard 原始图片是否含 alpha？ | **含**（ChemDraw 结构本身透明；粘贴到其他软件能保留透明，说明剪贴板中存在带 alpha 的格式，如 PNG/EMF）。 | 用户描述 |
| 2. Docear 从 Clipboard 取得的 Image 是否含 alpha？ | **大概率不含**。Docear 只请求 `DataFlavor.imageFlavor` → CF_DIB，该路径丢 alpha。 | 见 §2.3 |
| 3. Image 在哪里第一次转成 `BufferedImage`？ | `MClipboardController.getFlavorHandler()` 第 574 行：`(BufferedImage) t.getTransferData(DataFlavor.imageFlavor)`。 | 第 574 行 |
| 4. 转换时使用的类型？ | 由 JRE 决定，非 Docear 指定。CF_DIB 24 位 → 通常 `TYPE_INT_RGB` / `TYPE_3BYTE_BGR`（无 alpha）。 | JRE 行为 |
| 5. 是否存在 `TYPE_INT_RGB`？ | Docear 代码里**没有**显式写 `TYPE_INT_RGB`，但 JRE 返回的图像可能是它。Docear 自己写的是 `TYPE_INT_ARGB`（第 393 行）。 | 全库 grep 确认 |
| 6. 是否有一步把透明背景画到黑色背景上？ | Docear 没有“主动画黑”。但 `Graphics2D.drawImage` 绘制的是**已经黑底不透明**的源图，等于把黑色背景原样画进 TYPE_INT_ARGB 目标图。 | 第 393–395 行 |
| 7. Graphics2D 是否用了不正确的 Composite？ | **否**。`ImageFlavorHandler` 的 Graphics2D 未设置 `setComposite` / 未设置背景色，默认 `SRC_OVER`，本身正确。问题不在 Composite。 | 第 393–395 行 |
| 8. ImageIO 写 PNG 是否保留 alpha？ | **会**。`ImageIO.write` 对 `TYPE_INT_ARGB` 的 `BufferedImage` 写 PNG 会保留 alpha 通道。 | 第 441 行 |
| 9. PNG 最终是否真的含 alpha？ | **不含**（或 alpha 全为 255）。因为源图已无 alpha，写出的 PNG 是黑底不透明。 | 结论 |

### 3.2 `ImageFlavorHandler` 构造器的“透明重建”为何无效

```java
// MClipboardController.java 第 391–399 行
public ImageFlavorHandler(BufferedImage img) {
    super();
    BufferedImage fixedImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D fig = fixedImg.createGraphics();
    fig.drawImage(img, 0, 0, null);
    fig.dispose();
    fixedImg.flush();
    this.image = fixedImg;
}
```

- 这段代码**意图是对的**（用 `TYPE_INT_ARGB` 重建可带 alpha 的图像），但**无法恢复已丢失的 alpha**：
  - 若 `img` 透明区域 alpha=0，`drawImage` 会保留透明 → 修复有效；
  - 若 `img` 已经是“黑底 alpha=255”（CF_DIB 转换产物），`drawImage` 只会把黑色原样画进去 → 修复无效。
- 当前实情是后者，所以“TYPE_INT_ARGB 重建”这一步形同虚设。
- 另注：第 397 行 `fixedImg.flush()` 在 `dispose()` 之后调用，属于冗余但**无害**（`BufferedImage.flush()` 不清空像素 Raster），不是根因。

---

## 4. 黑色背景的直接来源判定

排除法定位（结合全库 grep，Docear 源码中无 `TYPE_INT_RGB`、无背景填充、无错误 Composite）：

| 候选 | 判定 |
|------|------|
| A. Clipboard 本身已经是黑底 | ✗ 剪贴板里存在带 alpha 格式（PNG/EMF），不是只有黑底 DIB |
| **B. Clipboard → BufferedImage 时 alpha 丢失** | ✅ **根因**。`getTransferData(DataFlavor.imageFlavor)` 走 CF_DIB 无 alpha |
| C. BufferedImage → 新 BufferedImage 时 alpha 丢失 | ✗ Docear 用的是 `TYPE_INT_ARGB`，不会丢（但无法“无中生有”） |
| D. Graphics2D 绘制时透明被黑色填充 | ✗ 未设背景色 / 未 `setComposite`，不会主动填黑 |
| E. PNG 编码时 alpha 丢失 | ✗ `ImageIO.write` 对 ARGB 写 PNG 会保留 alpha |
| F. PNG 有 alpha 但显示用黑底 | ✗ 用户确认“保存出来的 PNG 文件本身就是黑底”，非显示问题 |

**最终判定：类型 B。** 黑底产生于 Java AWT 层 `getTransferData(DataFlavor.imageFlavor)` 将 Windows CF_DIB 转换为无 alpha 的 `BufferedImage` 的过程中。Docear 自身未“画黑”，但也没有优先选择带 alpha 的剪贴板格式。

> 说明：B 的最终确认需一次运行时验证（临时在 `getFlavorHandler` 图像分支打印 `image.getType()` 与 `image.getColorModel().hasAlpha()`）。但从代码路径与 Windows/Java 机制可高度确定。

---

## 5. 保存窗口分析

问题表象：ChemDraw → Paste → 弹“保存 PNG”窗口。

### 5.1 为什么 Paste 需要保存 PNG？

Freeplane/Docear 的图片节点设计是“**图片作为外部文件被节点引用**”，而非把像素内嵌进 `.mm`。因此粘贴图片必须先把剪贴板图像落盘成文件，再由思维导图节点通过 `ExternalResource` + URI 引用该文件。

对应代码：`ImageFlavorHandler.paste(...)` 第 401–445 行。

### 5.2 图片保存到哪里？

- 初始路径：`mindmapFile.getParentFile()`（即当前思维导图 `.mm` 文件所在目录），用 `File.createTempFile(文件名前缀, ".png", dir)` 生成临时文件名（第 416–417 行）。
- 实际路径：由用户在 `JFileChooser` 保存对话框中决定（第 420–431 行）。
- 若用户输入的文件名无 `.png` 后缀，会自动补上 `.png`（第 437–438 行）。

### 5.3 保存之前经过什么处理？

1. `getFlavorHandler` 取 `BufferedImage`（第 574 行，**此步已丢 alpha**）。
2. `ImageFlavorHandler` 构造器用 `TYPE_INT_ARGB` + `Graphics2D.drawImage` 重建（第 393–395 行，**无法恢复 alpha**）。
3. 落盘前无其他像素处理，直接 `ImageIO.write(image, "png", file)`（第 441 行）。

### 5.4 用户取消保存后发生什么？

`JFileChooser` 返回非 `APPROVE_OPTION` 时：`tempFile.delete()` 删除临时文件并 `return`（第 427–430 行），**不插入任何节点**。

### 5.5 保存完成后 Docear 如何引用这个 PNG？

保存成功后（第 440–445 行）：
1. `LinkController.toLinkTypeDependantURI(mindmapFile, file)` 生成相对/类型化 URI。
2. `mapController.newNode(file.getName(), ...)` 新建节点，节点文本为文件名。
3. `node.addExtension(new ExternalResource(uri))` 挂载外部资源。
4. `mapController.insertNode(...)` 插入节点。

---

## 6. 透明 PNG 正确处理方式评估

- 现有代码**已经**使用 `BufferedImage.TYPE_INT_ARGB`（第 393 行），方向正确，**无需改这个类型**。
- 代码中**不存在** `new BufferedImage(w, h, TYPE_INT_RGB)` 这类直接导致透明变黑/不透明的错误构造。
- 真正的问题不在 `BufferedImage` 类型选择，而在于**上游取图时选错了剪贴板格式**：`DataFlavor.imageFlavor`（CF_DIB，无 alpha）而非 `image/png`（有 alpha）。
- 正确做法：优先从剪贴板读取带 alpha 的格式（如 `image/png`），用 `ImageIO.read(InputStream)` 解码（`ImageIO.read` 对 PNG 会保留 alpha），得到真正的 `TYPE_INT_ARGB`/`TYPE_4BYTE_ABGR` 图像；仅在无此格式时回退 `imageFlavor`。

---

## 7. 完整调用链

```
ChemDraw（透明化学结构）
   │  Ctrl+C
   ▼
Windows Clipboard
   ├─ CF_DIB（24位位图，无 alpha，透明→黑）
   ├─ 可能 PNG（有 alpha）
   └─ 可能 EMF（矢量透明）
   │
   ▼
DocearPasteAction.actionPerformed()
   (docear_plugin_pdfutilities/.../DocearPasteAction.java:32)
   │  getClipboardContents()
   ▼
ClipboardController.getClipboardContents()
   (freeplane/.../ClipboardController.java:250)  → Toolkit.getDefaultToolkit().getSystemClipboard()
   │
   ▼
MClipboardController.paste(Transferable, NodeModel, boolean, boolean, int)
   (freeplane/.../MClipboardController.java:664)
   │  getFlavorHandler(t)
   ▼
MClipboardController.getFlavorHandler(Transferable)
   (MClipboardController.java:511)
   │  if (t.isDataFlavorSupported(DataFlavor.imageFlavor))          ← 只检测 imageFlavor
   ▼
t.getTransferData(DataFlavor.imageFlavor)   ← ★ 黑底产生点（CF_DIB 无 alpha）
   (MClipboardController.java:574)
   │  → 返回已丢 alpha 的 BufferedImage
   ▼
new ImageFlavorHandler(image)                 ← 构造器 TYPE_INT_ARGB 重绘（无效恢复）
   (MClipboardController.java:391)
   │
   ▼
ImageFlavorHandler.paste(...)
   (MClipboardController.java:401)
   │  JFileChooser.showSaveDialog(...)         ← 保存 PNG 窗口
   │
   ▼
ImageIO.write(image, "png", file)             ← PNG 编码（会保留 alpha，但源已无 alpha）
   (MClipboardController.java:441)
   │
   ▼
黑底 PNG 文件  →  new ExternalResource(uri) 挂到节点 → insertNode()
```

**每一步的具体 class + method：**

| 步骤 | package | class | method |
|------|---------|-------|--------|
| Paste 入口 | `org.docear.plugin.pdfutilities.actions` | `DocearPasteAction` | `actionPerformed(ActionEvent)` |
| 读剪贴板 | `org.freeplane.features.clipboard` | `ClipboardController` | `getClipboardContents()` |
| Paste 分发 | `org.freeplane.features.clipboard.mindmapmode` | `MClipboardController` | `paste(Transferable, NodeModel, boolean, boolean, int)` |
| Flavor 选择 | 同上 | `MClipboardController` | `getFlavorHandler(Transferable)` |
| 取图（丢 alpha） | `java.awt.datatransfer` / `sun.awt.windows` | `Transferable` / `WDataTransferer` | `getTransferData(DataFlavor.imageFlavor)` |
| 图像处理 | `org.freeplane.features.clipboard.mindmapmode` | `MClipboardController$ImageFlavorHandler` | 构造器 + `paste(...)` |
| 写 PNG | `javax.imageio` | `ImageIO` | `write(RenderedImage, String, File)` |

---

## 8. 最小修复方案（仅建议，不实施）

### 8.1 最可能的根因

Docear 读取剪贴板图像只使用 `DataFlavor.imageFlavor`，Windows 下该 flavor 映射到无 alpha 的 CF_DIB，导致透明背景在“Clipboard → BufferedImage”阶段被固化为黑色。

### 8.2 确切代码位置

- `freeplane/src/org/freeplane/features/clipboard/mindmapmode/MClipboardController.java`
  - 第 572–581 行（`getFlavorHandler` 的图像分支）
  - 第 648–657 行（`getFlavorHandlers` 的同步图像分支，需一并处理保持一致）
  - 第 387–399 行（`ImageFlavorHandler` 构造器，可保留但需配合上游修复）

### 8.3 最小修改方案（思路）

在 `getFlavorHandler()` 中，**在 `DataFlavor.imageFlavor` 分支之前**插入对带 alpha 图片 flavor 的优先处理：

1. 遍历 `t.getTransferDataFlavors()`，寻找 mime 以 `image/` 开头、且**不是** `image/x-java-image` 的 flavor（典型为 `image/png`，Windows 下 ChemDraw 若提供 “PNG” 格式即可命中）。
2. 命中后：`BufferedImage image = ImageIO.read((InputStream) t.getTransferData(pngFlavor));`（`ImageIO.read` 保留 PNG alpha）。
3. 用该 `image` 构造 `ImageFlavorHandler`。
4. 若未命中任何带 alpha flavor，**回退到现有 `DataFlavor.imageFlavor` 分支**（行为完全不变）。

### 8.4 是否需要修改 DataFlavor 处理？

**需要（核心修改点）**：新增对 `image/png`（或遍历 `getTransferDataFlavors()` 选带 alpha 格式）的优先检测。

### 8.5 是否需要修改 BufferedImage 类型？

**不需要**。现有 `TYPE_INT_ARGB` 已正确。

### 8.6 是否需要修改 Graphics2D 绘制？

**不需要**。现有 `drawImage` 未设置错误 Composite/背景，逻辑正确。

### 8.7 是否需要修改 PNG 保存？

**不需要**。`ImageIO.write(image, "png", file)` 已能保留 alpha。

### 8.8 是否会影响现有非透明图片？

**不影响**。无带 alpha flavor 时回退原 `imageFlavor` 分支，非透明图片路径不变。

### 8.9 是否会影响 JPG？

**不影响**。JPG（`image/jpeg`）同理：优先分支只匹配带 alpha 的 `image/png`（或显式白名单），JPG 仍走原 `imageFlavor` 分支。

### 8.10 是否会影响其他剪贴板图片？

**不影响**，且对“带 alpha 的 PNG 剪贴板图片”反而是修复。关键在于优先分支需做“支持格式白名单 + 失败回退”双保险，避免对任意 `image/*` flavor 强行 `ImageIO.read` 造成新问题。

---

## 附录 A：关键代码位置索引

| 文件 | 行号 | 内容 |
|------|------|------|
| `docear_plugin_pdfutilities/src/org/docear/plugin/pdfutilities/actions/DocearPasteAction.java` | 32–58 | Paste 入口，分流文件/内容粘贴 |
| `freeplane/src/org/freeplane/features/clipboard/ClipboardController.java` | 77–84 | 获取系统剪贴板 |
| 同上 | 250–252 | `getClipboardContents()` |
| `freeplane/src/org/freeplane/features/clipboard/MindMapNodesSelection.java` | 43–54 | Docear 自定义 DataFlavor 定义 |
| `freeplane/src/org/freeplane/features/clipboard/mindmapmode/MClipboardController.java` | 511–583 | `getFlavorHandler()` flavor 分发 |
| 同上 | 572–581 | 图像 flavor 分支（`DataFlavor.imageFlavor`） |
| 同上 | 387–399 | `ImageFlavorHandler` 构造器（TYPE_INT_ARGB 重绘） |
| 同上 | 401–450 | `ImageFlavorHandler.paste()`（保存窗口 + 写 PNG） |
| 同上 | 441 | `ImageIO.write(image, "png", file)` |
| 同上 | 600–659 | `getFlavorHandlers()`（第二处图像分支） |
| 同上 | 668–671 | 被注释掉的 flavor debug 代码 |

## 附录 B：建议的运行时验证步骤（不修改源码，仅临时验证）

1. 在 `getFlavorHandler` 图像分支临时打印：
   ```java
   System.out.println("image type = " + image.getType());
   System.out.println("hasAlpha   = " + image.getColorModel().hasAlpha());
   DataFlavor[] fl = t.getTransferDataFlavors();
   for (DataFlavor f : fl) System.out.println("flavor = " + f);
   ```
2. 从 ChemDraw 复制结构后在 Docear 粘贴，观察输出：
   - 若 `image type` 为 `TYPE_INT_RGB` / `TYPE_3BYTE_BGR`（=1/5）且 `hasAlpha=false`，且 flavor 列表里存在 `image/png` → 完全印证本报告结论。
   - 若 flavor 列表里**没有**任何带 alpha 格式 → 则黑底来自 ChemDraw 提供的 DIB 本身，需在 `ImageFlavorHandler` 层做背景透明化（属于另一类修复，本报告不展开）。
