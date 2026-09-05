# Docear「文献元数据获取」功能完整代码链路分析

> 分析对象：BeelGroup/Docear-Desktop @ master（本地已编译运行验证）
> 分析日期：2026-09-02
> 性质：**纯只读分析，未修改任何 Java 源代码**

---

## 〇、总体结论（先看这个）

1. 存在**独立的 metadata 库**：`docear_metadata/`（Maven 项目，artifactId `docear-metadata-lib-0.0.1-SNAPSHOT`），源码在仓库里，但**编译产物以二进制 jar 形式预置**在 `docear_plugin_bibtex/lib/docear-metadata-lib-0.0.1.jar`，Ant 主构建**不会**自动重建它。
2. Google Scholar 检索**不是解析搜索结果页的标题/作者/年份**，而是直接抓取结果页中的 **"Import into BibTeX" 导出链接**（`a[href*=usercontent]`），拿到**完整 BibTeX 字符串**。
3. **没有结构化的 metadata 数据模型**。中间对象 `ScholarMetaData` 只有一个 `bibtex` 字符串字段 + rank + source + query；title/author/year/DOI 等全部藏在 BibTeX 文本里，最终由 JabRef 的 `BibtexParser` 解析成 `BibtexEntry` 字段表。
4. **没有任何自动匹配/验证机制**：无标题相似度、无作者比对、无年份校验。候选按 Google Scholar 排序展示，UI **默认预选第一条**，由用户手动确认。
5. HTTP 客户端是 **jsoup 1.7.3**，HTML 解析也是 jsoup（CSS 选择器），无正则解析、无 XPath 库。PDF 文本/标题提取用 **jPod**（`docear-pdf-inspector.jar`，二进制依赖，仓库无源码），OCR 回退用 jar 内自带的 Tesseract。

---

## 一、功能入口定位

用户操作：在导图/工作区中选中 PDF 节点 → 右键/菜单「References → Create or update reference」（i18n key：`AddNewReferenceAction.text`）。

| 层级 | 类 | 文件路径 | 关键方法 |
|---|---|---|---|
| **菜单注册** | `org.docear.plugin.bibtex.ReferencesController` | `docear_plugin_bibtex/src/org/docear/plugin/bibtex/ReferencesController.java` | L669 `WorkspaceController.addAction(addNewReference)`；L694/L737 挂到 References 菜单；Ribbon 定义在 `docear_plugin_bibtex/resources/xml/*.xml`（`<ribbon_action action="AddNewReferenceAction">`） |
| **Action** | `org.docear.plugin.bibtex.actions.AddNewReferenceAction` | `docear_plugin_bibtex/src/.../actions/AddNewReferenceAction.java` | `actionPerformed(ActionEvent)` → `createNewReference(Collection<NodeModel>)` |
| **事件处理/桥接** | `org.docear.plugin.bibtex.jabref.JabRefCommons` | `docear_plugin_bibtex/src/.../jabref/JabRefCommons.java` | `addNewRefenceEntry(String[], JabRefFrame, BasePanel)` → `addOrUpdateRefenceEntry(...)` |
| **向导编排** | `org.docear.plugin.bibtex.actions.MetaDataAction` | `docear_plugin_bibtex/src/.../actions/MetaDataAction.java` | `showDialog(MetaDataActionObject)`（构建 Wizard，注册两个页面） |
| **metadata retrieval 入口** | `org.docear.plugin.bibtex.dialogs.MetaDataExtractorPage` | `docear_plugin_bibtex/src/.../dialogs/MetaDataExtractorPage.java` | `preparePage(WizardSession)` → `searchMetadata()` |

注意还有**第二个 UI 入口**：`AddOrUpdateReferenceEntryWorkspaceAction`（工作区 PDF 文件右键菜单），同样汇入 `JabRefCommons.addNewRefenceEntry`。

`AddNewReferenceAction.actionPerformed` 分支：
- 节点链接是 `.pdf` → `JabRefCommons.addNewRefenceEntry(new String[]{path}, ...)`（走 metadata 检索流程）
- 非 PDF → 弹 `EntryTypeDialog` 手动建空条目（不走检索）
- 事件 command 为 `JABREF_DATABASE_SAVE_SUCCESS` → `addCreatedReference`（把保存后的 entry 通过 `docear_add_to_node` 字段回写到导图节点）

---

## 二、完整调用链（PDF → Reference）

```
[UI] References 菜单 / 工作区右键
  └─ AddNewReferenceAction.actionPerformed()                     docear_plugin_bibtex/.../actions/AddNewReferenceAction.java
      └─ createNewReference(nodes)
          ├─ NodeLinks.getLink(node)                             freeplane/.../features/link/NodeLinks.java（取节点链接）
          └─ JabRefCommons.addNewRefenceEntry({pdfPath}, ...)    docear_plugin_bibtex/.../jabref/JabRefCommons.java
              └─ addOrUpdateRefenceEntry(fileNames, -1, frame, panel, null, true)
                  ├─ [查重] 遍历 BibtexDatabase：url 字段或 file 链接匹配 → existingEntry
                  ├─ MetaDataAction.showDialog(result)           .../actions/MetaDataAction.java
                  │   └─ Wizard（docear_plugin_core 的 org.docear.plugin.core.ui.wizard.Wizard）
                  │       └─ MetaDataExtractorPage.preparePage() .../dialogs/MetaDataExtractorPage.java
                  │           ├─ [1] PDF 标题提取
                  │           │    AnnotationController.getDocumentTitle(uri)      docear_plugin_pdfutilities/.../map/AnnotationController.java
                  │           │      └─ PdfDataExtractor.extractTitle()           org.docear.pdf（docear-pdf-inspector.jar，无源码）
                  │           │           基于 jPod(de.intarsys.pdf)：首页文本按字体实体排序 TreeMap<PdfTextEntity,StringBuilder>
                  │           │           取最大字号条目 → 失败则 Tesseract OCR → 再退到 PDF Info 字典 Title
                  │           ├─ [2] XMP 元数据（并行选项）
                  │           │    readXmpData(file) → ImportFormatReader.importFromFile(new PdfXmpImporter(), path)   JabRef 2.7 fork
                  │           ├─ [3] 注册引擎：searchHub.registerSearchEngine(new GoogleScholarSearchEngine(null))
                  │           └─ searchMetadata()
                  │               ├─ setupSources()   → {GoogleScholarSearchEngine.class}（受 docear_metadata_searchScholar 开关控制）
                  │               ├─ setupSearchOptions() → {MAXRESULTS, COOKIE_FOLDER(用户设置目录), DEBUGLOGGING}
                  │               └─ MetaDataSearchHub.asyncSearch(query, sources, options, listener)   docear_metadata/.../MetaDataSearchHub.java
                  │                   └─ 线程池 submit(GoogleScholarSearchEngine.getExtractor(...))
                  │                       └─ new GoogleScholarExtractor(queryConfig, listener)          docear_metadata/.../extractors/GoogleScholarExtractor.java
                  │                           └─ [异步] search(query)  ← ★ Google Scholar 检索核心
                  │                               ├─ getCookies("GoogleScholarCookie.xml")（XStream 持久化，GSP 加 ":CF=4" 开启 BibTeX 导出链接）
                  │                               ├─ jsoup GET http://scholar.google.com/scholar?q=<query>&hl=en
                  │                               ├─ [CAPTCHA 分支] noscript>iframe / HTTP 503 → handleCaptchaRequest / handleReCaptchaRequest
                  │                               ├─ doc.select("a[href*=usercontent]")  ← "Import into BibTeX" 链接
                  │                               └─ 逐个 GET 导出链接 → body 以"@"开头且以"}"结尾
                  │                                   → new ScholarMetaData(rank=i, bibtex, query)
                  │                               └─ fire FetchedResultsEvent → listener.onFinishedRequest()
                  └─ [回调] MetaDataExtractorPage$1.onFinishedRequest(FetchedResultsEvent)
                      ├─ 过滤：source instanceof ScholarSource && searchValue.equals(result.getQuery())
                      ├─ BibtexParser.parse(new StringReader(((ScholarMetaData)result).getBibtex()))   JabRef fork: net.sf.jabref.imports.BibtexParser
                      │    → ParserResult.getDatabase().getEntries() → List<BibtexEntry>
                      ├─ listModelFetchedResults.addEntries(...)（HTML 预览用 JabRef Layout 排版）
                      └─ listFetchedResults.setSelectedIndex(0)   ← 默认预选第一条
[用户点 OK]
  └─ 向导结束，回到 JabRefCommons.addOrUpdateRefenceEntry
      ├─ isSelectedCancel → 跳过
      ├─ isAttachOnly → DroppedFileHandler.linkPdfToEntry(path, entryToUpdate)   JabRef fork
      ├─ isSelectedBlank → addOrUpdateEntryToDatabase(file, new BibtexEntry())
      └─ isSelectedFetched/Xmp && resultEntry != null
          └─ addOrUpdateEntryToDatabase(file, selected)
              ├─ 查旧：Reference.containsFile(file) → oldEntry？
              ├─ 新：selected.setId(Util.createNeutralId())
              │       database.insertEntry(selected)
              │       DroppedFileHandler.linkPdfToEntry(path, selected)   ← PDF 关联到 entry（file 字段）
              │       LabelPatternUtil.makeLabel(...)                     ← 生成 BibTeX key（引用键）
              │       showInReferenceManager(selected, false)             ← UI 展示
              └─ 旧：updateEntryInDatabase(file, selected, oldEntry)
```

### 精确的 package / class / method 表

| 步骤 | package.class#method | 文件 |
|---|---|---|
| 菜单入口 | `org.docear.plugin.bibtex.actions.AddNewReferenceAction#actionPerformed` | `docear_plugin_bibtex/src/org/docear/plugin/bibtex/actions/AddNewReferenceAction.java` |
| 桥接 | `org.docear.plugin.bibtex.jabref.JabRefCommons#addNewRefenceEntry / addOrUpdateRefenceEntry` | `docear_plugin_bibtex/src/org/docear/plugin/bibtex/jabref/JabRefCommons.java` |
| 向导 | `org.docear.plugin.bibtex.actions.MetaDataAction#showDialog` | `docear_plugin_bibtex/src/org/docear/plugin/bibtex/actions/MetaDataAction.java` |
| 检索页 UI | `org.docear.plugin.bibtex.dialogs.MetaDataExtractorPage#preparePage / searchMetadata / onFinishedRequest` | `docear_plugin_bibtex/src/org/docear/plugin/bibtex/dialogs/MetaDataExtractorPage.java` |
| PDF 标题 | `org.docear.plugin.pdfutilities.map.AnnotationController#getDocumentTitle` | `docear_plugin_pdfutilities/src/org/docear/plugin/pdfutilities/map/AnnotationController.java` |
| PDF 标题(实现) | `org.docear.pdf.PdfDataExtractor#extractTitle` | **二进制**：`docear_plugin_pdfutilities/lib/docear-pdf-inspector.jar` |
| 调度中心 | `org.docear.metadata.MetaDataSearchHub#asyncSearch / getExtractors` | `docear_metadata/src/main/java/org/docear/metadata/MetaDataSearchHub.java` |
| 引擎 | `org.docear.metadata.engines.GoogleScholarSearchEngine#getExtractor` | `docear_metadata/src/main/java/org/docear/metadata/engines/GoogleScholarSearchEngine.java` |
| HTTP+解析 | `org.docear.metadata.extractors.GoogleScholarExtractor#search` | `docear_metadata/src/main/java/org/docear/metadata/extractors/GoogleScholarExtractor.java` |
| HTTP 基类 | `org.docear.metadata.extractors.HtmlDataExtractor#getConnection / saveCookies / readCookies` | `docear_metadata/src/main/java/org/docear/metadata/extractors/HtmlDataExtractor.java` |
| 数据模型 | `org.docear.metadata.data.ScholarMetaData / MetaData / MetaDataSource` | `docear_metadata/src/main/java/org/docear/metadata/data/*.java` |
| BibTeX 解析 | `net.sf.jabref.imports.BibtexParser#parse` | `Jabref_Beta_2_7_Docear/src/java/net/sf/jabref/imports/BibtexParser.java` |
| 入库+关联 | `org.docear.plugin.bibtex.jabref.JabRefCommons#addOrUpdateEntryToDatabase` | 同上 JabRefCommons.java |

---

## 三、Google Scholar Parser 与相关组件清单

### 独立 metadata 库（确认存在）

- **项目**：`docear_metadata/`，Maven（`pom.xml`），artifactId `docear-metadata-lib`，version `0.0.1-SNAPSHOT`
- **发布形态**：预编译 `docear_plugin_bibtex/lib/docear-metadata-lib-0.0.1.jar`（Ant 构建直接打包该 jar，不编译 `docear_metadata/` 源码）
- **包结构**（15 个类）：

| 包 | 类 | 职责 |
|---|---|---|
| `org.docear.metadata` | `MetaDataSearchHub` | 引擎注册表 + 缓存线程池（`ThreadPoolExecutor`, 0~∞ 线程, SynchronousQueue），`search()`（同步）/`asyncSearch()`（异步） |
| `org.docear.metadata.engines` | `SearchEngine`（抽象基类）, `GoogleScholarSearchEngine` | 工厂：`getExtractor(query, options, listener)` 返回 `Callable<Collection<MetaData>>` |
| `org.docear.metadata.extractors` | `MetaDataExtractor`（接口）, `HtmlDataExtractor`（抽象基类：jsoup 连接、UA/referrer/timeout、XStream cookie 持久化）, `GoogleScholarExtractor`, `ExtractorConfigKey`（接口+两个 enum）, `MalformedConfigException` | HTTP 抓取 + HTML 解析 |
| `org.docear.metadata.data` | `MetaData`（抽象）, `ScholarMetaData`, `MetaDataSource`（接口） | 数据模型 |
| `org.docear.metadata.events` | `MetaDataEvent`, `FetchedResultsEvent`, `CaptchaEvent`, `MetaDataListener`, `CaptchaListener` | 回调事件 |

### 第三方依赖（metadata 链路相关）

| 组件 | 版本 | 用途 | 位置 |
|---|---|---|---|
| **jsoup** | 1.7.3 | HTTP 客户端 **和** HTML 解析（CSS 选择器） | `docear_plugin_bibtex/lib/plugins/`（运行时由 metadata lib 依赖） |
| **XStream** | 1.4.5 | cookie 持久化为 XML | 同上 |
| **slf4j + slf4j-jdk14** | 1.7.6 | 日志 | `docear_plugin_bibtex/lib/slf4j-jdk14-1.7.6.jar` |
| **jPod** (de.intarsys.pdf) | — | PDF 文本/结构解析（标题提取） | `docear_plugin_pdfutilities/lib/jpod/` |
| **docear-pdf-inspector** | — | `PdfDataExtractor`（标题、hash、标注、OCR），**仓库无源码，纯二进制**，内含 Tesseract-ocr | `docear_plugin_pdfutilities/lib/docear-pdf-inspector.jar` |
| **JabRef** | 2.7.1 fork | `BibtexParser`、`BibtexEntry`、`PdfXmpImporter`、`DroppedFileHandler`、`LabelPatternUtil` | `docear_plugin_bibtex/lib/JabRef-2.7.1.jar`（源码在 `Jabref_Beta_2_7_Docear/`，构建时会先编译） |

### HTML 解析用到的全部选择器（`GoogleScholarExtractor`）

| 选择器 | 位置 | 目的 |
|---|---|---|
| `noscript > iframe` | search() L76 | 检测新版 ReCaptcha 拦截页 |
| `center > img` | handleReCaptchaRequest() L174 | ReCaptcha 图片 |
| `input`, `textarea` | 多处 | 表单字段收集（captcha token 等） |
| `a[href*=usercontent]` | search() L109 | **核心**：搜索结果中的「Import into BibTeX」导出链接 |
| `img`, `form` | handleCaptchaRequest() | 旧版 503 /sorry/ 验证码页 |

**没有**使用：正则表达式解析 HTML、XPath、CSS selector 库（jsoup 自带）。

---

## 四、数据模型分析

### 中间对象：`ScholarMetaData`

```java
// docear_metadata/src/main/java/org/docear/metadata/data/ScholarMetaData.java
public class ScholarMetaData extends MetaData {
    public enum ScholarSource implements MetaDataSource { GOOGLESCHOLAR; }
    private String bibtex;              // ← 唯一的"数据"字段：完整 BibTeX 字符串
    public ScholarMetaData(int rank, String bibtex, String query) { ... }
}
// 父类 MetaData: int rank; MetaDataSource source; String query;
```

**关键结论：所有书目字段都以 BibTeX 文本形式"打包"传输，没有逐字段的结构化对象。**

### 各字段的产生与保存位置

| 字段 | 产生位置 | 载体 | 保存位置 |
|---|---|---|---|
| **title** | Google Scholar 导出的 BibTeX `title=` 行 | `ScholarMetaData.bibtex` 字符串 | `BibtexEntry.getField("title")` → JabRef 数据库（`.bib` 文件） |
| **authors** | BibTeX `author=` 行 | 同上 | `BibtexEntry.getField("author")` |
| **journal** | BibTeX `journal=` 行（Google 仅对期刊文章导出） | 同上 | `BibtexEntry.getField("journal")` |
| **year** | BibTeX `year=` 行 | 同上 | `BibtexEntry.getField("year")` |
| **volume / issue(number) / pages** | BibTeX `volume=` / `number=` / `pages=` 行 | 同上 | `BibtexEntry` 对应字段 |
| **DOI** | BibTeX `doi=` 行（**仅当 Google 导出包含**，很多结果没有） | 同上 | `BibtexEntry.getField("doi")`（无独立提取/校验逻辑） |
| **URL** | BibTeX `url=` 行 | 同上 | `BibtexEntry.getField("url")`（还被 JabRefCommons 查重逻辑用于文件匹配） |
| **abstract** | **不获取**——BibTeX 导出链接内容通常无摘要 | — | — |
| **publisher** | BibTeX `publisher=` 行（书籍/会议） | 同上 | `BibtexEntry.getField("publisher")` |
| **BibTeX key** | 本地生成：`LabelPatternUtil.makeLabel(Globals.prefs.getKeyPattern(), db, entry)`（JabRef fork） | — | entry 的 cite key，按用户配置的 key pattern |
| **rank**（第几条候选） | `new ScholarMetaData(i, ...)`，i = 导出链接在结果页中的顺序 | `MetaData.rank` | 仅 UI 排序用，不入库 |
| **query** | 检索词 | `MetaData.query` | 仅用于回调过滤（`searchValue.equals(result.getQuery())`），不入库 |

最终对象：`net.sf.jabref.BibtexEntry`（id + type + `Map<String,String>` 字段表），插入 `BibtexDatabase`，由 `DocearSaveDatabaseAction` 落盘为项目目录下的 `.bib` 文件。

---

## 五、当前匹配机制（逐问回答）

1. **使用什么 URL/API/HTTP 请求？**
   `GET http://scholar.google.com/scholar?q=<标题>&hl=en`（`GoogleScholarExtractor.BaseURL`，注意是 **http** 而非 https）。无官方 API，纯网页抓取。BibTeX 通过 GET 搜索结果页中 `a[href*=usercontent]` 的导出链接（形如 `/scholar.bib?q=...&output=sv&hl=...`）获得。

2. **查询参数？** `q`（检索词）+ `hl`（语言，默认 `en`，可由 `ScholarConfigKeys.LANGUAGE` 配置，UI 未暴露）。验证码流程中还会附带表单数据。

3. **是否 URL encoding？** 是——jsoup 的 `Connection.data("q", query, "hl", language)` 负责序列化与编码，非手工拼接。

4. **是否对标题清洗？** **没有**。检索词就是：① PDF 提取的标题原样（`PdfDataExtractor.extractTitle()`，仅 `trim()`），或 ② 文件名去扩展名（`pdfFileName.substring(0, lastIndexOf("."))`），或 ③ 用户在文本框手输的内容。没有去标点、去换行、去副标题、去 OCR 噪声等任何 normalization。

5. **返回几个候选结果？** 最多 `maxResults` 条（默认 **3**；`MetaDataOptionsPage` 中 spinner 可设 1–50，key `docear_metadata_maxResult`）。

6. **如何判断哪个是正确论文？** **不判断**。全部候选展示给用户，由用户选择。程序不做任何自动匹配。

7. **标题相似度？** 无。唯一沾边的是回调里 `searchValue.equals(result.getQuery())`——这只是丢弃"过期请求"的结果（用户改了搜索词后旧请求返回的结果），不是相似度计算。

8. **作者匹配？** 无。

9. **年份匹配？** 无。

10. **是否直接选第一个？** 事实上是"**默认预选**第一个"（`listFetchedResults.setSelectedIndex(0)`，L411），但用户可改选；只有用户点 OK 才会采纳。若用户不改，效果等同于选第一条搜索结果。

11. **DOI 如何提取？** **不单独提取**。完全依赖 Google Scholar BibTeX 导出中的 `doi=` 字段；没有的话该字段就是缺失，无 Crossref/DOI 兜底查询。

12. **BibTeX 如何获得？** 逐条 GET 搜索结果页里的 "Import into BibTeX" 链接（`usercontent` 域的导出端点），响应体 `trim()` 后校验以 `@` 开头、`}` 结尾即采纳；不合法内容仅记日志丢弃。**启用该链接的前提**是 GSP cookie 追加了 `:CF=4`（`getCookies()` L332-334）。

---

## 六、错误处理分析

| 场景 | 处理方式 | 代码位置 |
|---|---|---|
| **Google Scholar 无结果** | `bibtexLinks` 迭代器为空 → 返回空 list → `FetchedResultsEvent` 照常触发 → UI 显示 "Fetched 0 entries"，用户可改关键词重搜/选空白条目/XMP | `GoogleScholarExtractor.search()` L109-140；`MetaDataExtractorPage` L405 |
| **返回多个结果** | 全部进候选列表（上限 maxResults），用户手选，默认预选第一条 | `MetaDataExtractorPage.onFinishedRequest()` L410-412 |
| **HTTP timeout** | jsoup `timeout(3000)`（默认值；**UI 从不配置 TIMEOUT**，`setupSearchOptions()` 没放这个 key）→ `SocketTimeoutException`（IOException 子类）→ catch 记日志 → `search()` 继续走到 fire event，返回已收集的部分/空结果 | `HtmlDataExtractor` L24, L108；`GoogleScholarExtractor` L149-152 |
| **HTTP 403** | `triedNewCookie` 标志防止死循环：`requestNewCookie()` 重新访问首页拿新 cookie（再加 `:CF=4`）→ **递归重试一次** `search(query)`；再失败则放弃 | `GoogleScholarExtractor` L146-148, L339-351 |
| **CAPTCHA（两种形态）** | ① 页面内 `noscript>iframe`（ReCaptcha）：`handleReCaptchaRequest()` 取 `center>img` 图片 → `sendCaptchaEvent()` → UI 弹 `CaptchaRequestDialog` 让用户输入 → 提交表单拿 token → 带 token 重发检索。② HTTP 503：`handleCaptchaRequest()` 解析 `/sorry/` 表单 → 同样弹窗 → 提交 → 若 302 重定向则合并 cookie 存盘。**若无任何 listener（如独立运行测试）则退化到控制台手输** | `GoogleScholarExtractor` L76-107, L163-231, L233-303；`CaptchaRequestDialog.showDialog()` |
| **页面结构改变** | `a[href*=usercontent]` 选择器失配 → 空结果，**无任何告警或版本检测**，静默失败（这是该功能当今失效的最大风险点） | `GoogleScholarExtractor` L109 |
| **DOI 不存在** | 无处理——BibTeX 里没有 `doi=` 字段就没有，不补查 | — |
| **作者缺失** | 同上，字段缺失即缺失 | — |
| **年份缺失** | 同上 | — |
| **标题识别错误** | 无校验。错误标题 → 错误 query → 候选全错，用户只能自己发现并手改搜索词 | `MetaDataExtractorPage.searchMetadata()` |
| **网络断开** | `IOException` → catch 记日志（slf4j）→ 空 `FetchedResultsEvent` → UI "Fetched 0 entries"。**无重试、无用户可见错误提示**（仅 debug logging 开启时进日志） | `GoogleScholarExtractor` L149-152 |
| **配置错误** | `MalformedConfigException`（config map 含 null 值或类型转换失败）→ `MetaDataExtractorPage` catch 后 `LogUtils.warn` | `HtmlDataExtractor.readConfig()`；`MetaDataExtractorPage` L423-425 |
| **重复 PDF** | 检索前查重（url 字段 / 文件链接比对）→ 命中则 `MetaDataDuplicatePage.showDuplicateMessage` 或显示红色警告 + attach-only 选项 | `JabRefCommons.addOrUpdateRefenceEntry` 查重段；`MetaDataAction.showDialog` |

---

## 七、现有架构图

### Mermaid

```mermaid
flowchart TD
    A[用户: References 菜单<br/>Create or update reference] --> B[AddNewReferenceAction<br/>actionPerformed / createNewReference]
    B --> C[JabRefCommons<br/>addNewRefenceEntry → addOrUpdateRefenceEntry]
    C --> D{节点链接是 PDF?}
    D -- 否 --> E[EntryTypeDialog 手动建空条目]
    D -- 是 --> F[MetaDataAction.showDialog<br/>Wizard 向导]
    F --> G[MetaDataExtractorPage.preparePage]
    G --> H1[AnnotationController.getDocumentTitle<br/>↓ PdfDataExtractor.extractTitle<br/>jPod 字号启发式 / Tesseract OCR / Info字典]
    G --> H2[readXmpData<br/>PdfXmpImporter (JabRef)]
    G --> I[searchMetadata]
    I --> J[MetaDataSearchHub.asyncSearch<br/>线程池]
    J --> K[GoogleScholarSearchEngine.getExtractor]
    K --> L[GoogleScholarExtractor.search<br/>★ jsoup 抓取]
    L --> L1[GET scholar.google.com/scholar?q=...&hl=en<br/>带 GSP cookie :CF=4]
    L1 --> L2{CAPTCHA?}
    L2 -- 是 --> L3[CaptchaEvent →<br/>CaptchaRequestDialog 用户输入]
    L2 -- 403 --> L4[requestNewCookie 重试一次]
    L2 -- 503 --> L5[handleCaptchaRequest /sorry/ 流程]
    L2 -- 否 --> M[doc.select a[href*=usercontent]<br/>BibTeX 导出链接]
    M --> N[逐条 GET 导出链接<br/>校验 @...} → ScholarMetaData]
    N --> O[FetchedResultsEvent<br/>onFinishedRequest 回调]
    O --> P[BibtexParser.parse(bibtex)<br/>→ List&lt;BibtexEntry&gt;]
    P --> Q[候选列表 UI<br/>默认预选第一条, 用户手选]
    Q --> R[用户点 OK]
    R --> S[JabRefCommons<br/>addOrUpdateEntryToDatabase]
    S --> T[BibtexDatabase.insertEntry<br/>DroppedFileHandler.linkPdfToEntry<br/>LabelPatternUtil.makeLabel 生成 BibTeX key]
    T --> U[Reference 入库 + PDF 关联<br/>Docear UI 展示]
```

### ASCII（主干）

```
PDF 文件
  |
  v
PdfDataExtractor.extractTitle()          [docear-pdf-inspector.jar, jPod, 无源码]
  |  (title 原样, 无清洗)
  v
MetaDataExtractorPage.searchMetadata()   [文本框 q 值]
  |
  v
MetaDataSearchHub.asyncSearch()          [线程池, 异步]
  |
  v
GoogleScholarSearchEngine.getExtractor() [工厂]
  |
  v
GoogleScholarExtractor.search(query)     [jsoup 1.7.3]
  |   GET http://scholar.google.com/scholar?q=&hl=en  (+GSP cookie :CF=4)
  |   [captcha / 403 / 503 处理]
  |   doc.select("a[href*=usercontent]")
  |   逐条 GET 导出链接 → BibTeX 字符串
  v
ScholarMetaData(rank, bibtex, query)     [唯一数据字段 = bibtex 字符串]
  |  FetchedResultsEvent → onFinishedRequest
  v
BibtexParser.parse(bibtex)               [JabRef 2.7 fork]
  |
  v
BibtexEntry (title/author/year/journal/doi/... = 字段表)
  |  用户从候选列表选择 (默认第一条)
  v
BibtexDatabase.insertEntry + linkPdfToEntry + makeLabel(BibTeX key)
  |
  v
Reference (.bib 数据库) + Docear UI
```

---

## 八、最小修改点（替换 Google Scholar provider，不改代码——仅规划）

### 前提认知

替换点天然存在：**`MetaDataSearchHub` 是引擎注册表 + `SearchEngine` 是抽象工厂**。UI 只通过 `registerSearchEngine()` 注册、`setupSources()` 选择引擎类，理论上多引擎并存（UI 代码按"多引擎并发、结果汇总"设计，Docear 官方引擎就是被注释掉的第二个引擎）。

### 必须修改

| # | 位置 | 内容 |
|---|---|---|
| 1 | `docear_metadata/src/main/java/org/docear/metadata/engines/` | 新建 `XxxSearchEngine extends SearchEngine`（实现 `getExtractor()`） |
| 2 | `docear_metadata/src/main/java/org/docear/metadata/extractors/` | 新建 `XxxDataExtractor`（建议 extends `HtmlDataExtractor` 复用 HTTP/cookie/UA/timeout 基建；如调用 JSON API 可直接 implements `MetaDataExtractor`） |
| 3 | `docear_metadata/src/main/java/org/docear/metadata/data/` | 新的 metadata 载体类（见下方"兼容性捷径"） |
| 4 | `docear_plugin_bibtex/src/.../dialogs/MetaDataExtractorPage.java` L541 `preparePage()` | `searchHub.registerSearchEngine(new GoogleScholarSearchEngine(null))` → 注册新引擎 |
| 5 | `docear_plugin_bibtex/src/.../dialogs/MetaDataExtractorPage.java` L438-448 `setupSources()` | `sources.add(GoogleScholarSearchEngine.class)` → 换成/加上新引擎类 |
| 6 | **构建产物**（非源码） | 用 Maven 重构建 `docear_metadata` → 替换 `docear_plugin_bibtex/lib/docear-metadata-lib-0.0.1.jar`（Ant 主构建只打包预置 jar，不会自己编译 metadata 源码——不改构建方式的前提下，这是手动步骤） |

**兼容性捷径（强烈建议）**：让新引擎返回的 `MetaData` 继承 `ScholarMetaData`（或直接产生含合法 BibTeX 的 `ScholarMetaData` 实例）。这样 `onFinishedRequest()` 里的 `instanceof ScholarSource` 判断和 `((ScholarMetaData)result).getBibtex()` 强转、以及 `BibtexParser` 整条下游链路**一行都不用改**——新 provider 只需产出 BibTeX 字符串（Crossref content negotiation、OpenAlex → BibTeX 转换等都能满足）。

### 可能修改

| 位置 | 何时需要 |
|---|---|
| `MetaDataExtractorPage.onFinishedRequest()` L384-399 | 若新 provider 不走 `ScholarMetaData`（返回结构化字段而非 BibTeX），需改 `instanceof` 分支和字段填充逻辑 |
| `docear_plugin_bibtex/src/.../dialogs/MetaDataOptionsPage.java` | 需要新引擎的开关/参数 UI（属性 key：`docear_metadata_searchXxx`），以及 `Resources_en.properties` 翻译 |
| `docear_plugin_bibtex/src/org/docear/plugin/bibtex/ReferencesController.java` | 需要为新选项注册默认值（`setDefaultProperty`） |
| `docear_plugin_bibtex/src/.../dialogs/CaptchaRequestDialog.java` + `CaptchaEvent` | 仅当新 provider 也需要人机验证交互（学术 API 一般不需要，可不动） |
| `ScholarMetaData.ScholarSource` / `BibtexEntryListCellRenderer`（`getElementAt` 中来源标签） | 需要在结果列表里区分显示来源时 |
| `HtmlDataExtractor` | 仅当需要 HTTPS 证书定制、代理、更长超时等 HTTP 行为调整（`timeout` 目前硬编码 3000ms 且 UI 未暴露——升级时建议顺手经 config 传入，这属于"可能"而非"必须"） |

### 不要修改

| 位置 | 理由 |
|---|---|
| `AddNewReferenceAction` | UI 入口，与 provider 无关 |
| `AddOrUpdateReferenceEntryWorkspaceAction` | 同上（第二入口） |
| `MetaDataAction`（向导编排） | 页面流转与 provider 解耦 |
| `JabRefCommons`（addNewRefenceEntry / addOrUpdateEntryToDatabase） | 入库、查重、PDF 关联、key 生成全部在下游，与 provider 无关 |
| `MetaDataSearchHub` | 注册表设计本身已满足多引擎，无需动 |
| `SearchEngine` 抽象基类 | 已满足扩展需求 |
| `AnnotationController` / `PdfDataExtractor`（标题提取） | 与 provider 选择正交（标题提取质量是另一个独立话题） |
| `Jabref_Beta_2_7_Docear/` 整个 fork | `BibtexParser`/`BibtexEntry`/`DroppedFileHandler`/`LabelPatternUtil` 是稳定的下游消费者 |
| Freeplane 核心 / OSGi manifest / 构建脚本 | 与本功能无关 |

### 附：新 provider 集成草图（未来实施参考，本次未写任何代码）

```
新 CrossrefSearchEngine extends SearchEngine
  └─ getExtractor() → new CrossrefExtractor(queryConfig, listener)
新 CrossrefExtractor implements MetaDataExtractor
  └─ call()/search(): 
       GET api.crossref.org/works?query.bibliographic=<title>&rows=N
       → 命中 → 组装 BibTeX 字符串（或复用 content negotiation 直接拿 BibTeX）
       → new ScholarMetaData(rank, bibtex, query)   ← 关键：复用现有下游
       → fire FetchedResultsEvent
UI: MetaDataExtractorPage.preparePage() 注册新引擎; setupSources() 勾选
```

---

## 附：已发现的潜在问题（仅记录，不修复）

1. `GoogleScholarExtractor.BaseURL` 是 `http://`（明文），现代 scholar.google.com 会 301 到 https，jsoup followRedirects=true 所以能工作，但多一跳。
2. HTTP 超时硬编码 3000ms，UI 从不传 `TIMEOUT` 配置。
3. `GoogleScholarSearchEngine.getExtractor()` 中 `queryConfig.putAll(options)` **会污染引擎自身持有的 config map**（共享可变状态，多线程下有隐患）。
4. `a[href*=usercontent]` 选择器对 Google 页面改版零容错——这正是今天该功能在线上大概率静默失效的原因。
5. `searchMetadata()` 里 `if(this.searchValue.equals(this.textFieldSearch.getText())) return;` 意味着同一关键词无法手动重试。
6. 无结果/网络失败时 UI 只显示 "Fetched 0 entries"，不区分"没找到"和"请求失败"。
7. `MetaDataExtractorPage` 每次向导打开都 `new MetaDataSearchHub()` + `new GoogleScholarSearchEngine(null)`，旧 hub 的线程池（static 共享）不会 shutdown。

---

*本报告基于静态代码阅读 + 字节码反编译（javap）完成；分析过程中未修改任何 Java 源代码、依赖或构建脚本。*
