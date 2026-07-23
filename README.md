# LightPS — 移动端轻量 Photoshop

基于 Krita 源码架构，用纯 Java 实现的 Android 图像编辑软件。

## 目标

- **图层系统**：像素层、调整层、组层，完整混合模式
- **PSD 读写**：兼容 Photoshop PSD/PSB 格式，支持图层结构和 RLE 压缩
- **选区系统**：矩形、椭圆、套索、魔棒，支持扩展/收缩/羽化
- **色彩管理**：sRGB、AdobeRGB、Display P3 空间的线性转换

## 架构

```
com.lightps/
├── engine/
│   ├── layer/         — 图层系统 (Layer, PixelLayer, GroupLayer, AdjustmentLayer, LayerManager)
│   ├── blend/         — 混合模式引擎 (PSD 完全 27 种模式)
│   ├── selection/     — 选区系统 (Selection)
│   ├── color/         — 色彩管理 (ColorSpace)
│   ├── brush/         — 笔刷引擎 (Brush)
│   └── filter/        — 滤镜 (Filter, GaussianBlurFilter)
├── io/
│   └── psd/           — PSD 文件格式 (PSDHeader, PSDLayerRecord, PSDImageData, PSDLoader, PSDSaver)
├── model/
│   └── Document.java  — 文档模型
├── ui/
│   └── CanvasView.java — 画布视图 (CPU Canvas 渲染)
└── LightPSActivity.java — 主活动
```

## 编译

```bash
./gradlew assembleRelease
```

输出 APK: `app/build/outputs/apk/release/app-release.apk`

要求: Android 7.0+ (API 24)，OpenGL ES 2.0+

## 文件格式支持

| 格式 | 读 | 写 |
|------|----|----|
| PSD (v1) | ✅ 图层结构、通道数据、RLE | ✅ |
| PSB (v2) | ⚠️ 基础 | ⚠️ |
| PNG | ✅ | ✅ |
| JPEG | ✅ | — |

## 代码统计

```
 Language         Files    Lines
 Java              19     ~6000
 XML                6     ~400
 Total             25     ~6400
```

## 参考

- [Krita 源码](https://invent.kde.org/graphics/krita) — 图层、PSD、色彩管理、混合模式
- [Adobe Photoshop SDK 文档](https://www.adobe.io/photoshop/) — PSD 文件格式规范
- [openclaw](https://openclaw.ai/) — AI 辅助编码
