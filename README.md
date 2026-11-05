# StudyInk 学习笔记

面向小米平板（Android）的学习笔记 App。支持 PDF 导入并手写标注、创建空白笔记、三种笔迹（钢笔/马克笔/荧光笔）、自选颜色、自动保存、导出带标注的 PDF。

> 当前版本（v1）聚焦"能写字"：手写实时识别（ML Kit）暂未实现，架构已预留扩展位，后续可无缝加入。

## 功能

- 📄 **导入 PDF**：从平板文件选择器导入 PDF，逐页渲染，可在任意页手写标注。
- 📝 **空白笔记**：一键新建空白笔记，自由书写。
- 🖊️ **三种笔迹**：钢笔（压力感粗细）、马克笔、荧光笔（半透明高亮）。
- 🎨 **自选颜色**：预设色板 + 色相/饱和度/明度自定义取色。
- 💾 **自动保存**：笔迹实时落库，退出重开不丢失。
- ⬆️ **导出 PDF**：把「原页 + 笔迹」合成导出为新 PDF。
- ↩️ **撤销 / 重做**：按笔迹为单位撤销/重做。

## 一键云端构建（推荐，无需本机工具链）

1. 把本项目推到你的 GitHub 仓库（`main` 分支）。
2. 打开仓库的 **Actions** 页，会看到 `Build APK` 工作流自动运行（也可手动点 `Run workflow`）。
3. 等绿灯跑完后，进入该次运行 → 底部 **Artifacts** → 下载 `StudyInk-debug`（解压得到 `app-debug.apk`）。

> 首次构建约 5–10 分钟（需下载 Gradle 与依赖）。

## 安装到小米平板

1. 把 `app-debug.apk` 传到平板（微信/QQ/数据线/U 盘均可）。
2. 点击该文件安装；系统提示"未知来源"时允许即可。
3. 打开 **StudyInk 学习笔记**，开始使用。

## 本地构建（备选，需本机工具链）

需要 JDK 17 + Gradle 8.9（或 Android Studio 2024+）。

```bash
# 命令行
gradle --no-daemon assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或用 Android Studio 打开本项目根目录，等待同步完成后点 **Build → Build APK(s)**。

## 项目结构

```
app/src/main/java/com/example/studyink/
  MainActivity.kt           入口
  StudyInkApp.kt            应用类（提供 Repository）
  model/                    笔迹类型、点、画布规格等模型
  data/                     Room 实体 / DAO / 数据库 / 仓库
  pdf/                      PDF 导入 / 渲染 / 导出
  ui/theme/                 Compose 主题
  ui/notelist/              笔记列表页
  ui/editor/                编辑页 / 画布 / 颜色选择器
```

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- PDF 渲染/导出：Android 内置 `PdfRenderer` / `PdfDocument`（无第三方依赖）
- 存储：Room（SQLite）+ 笔迹 JSON 序列化
- 构建：Gradle 8.9 + AGP 8.5 + Kotlin 2.0，minSdk 24 / targetSdk 34

## 说明与已知限制

- 导出 PDF 为栅格化页面（按屏渲染清晰度），不含可编辑文字层，个人学习用途足够。
- v1 暂不支持：手写实时识别、云同步、图片/录音插入、PDF 矢量文字重排、上架商店签名。
- 手写识别预留位置：`model/InkPoint` 已记录每点时间戳与压力，未来接入 ML Kit Digital Ink Recognition 时可直接复用笔迹数据。
