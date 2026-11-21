package com.sxsbsxs.filesniffing

enum class ResourceKind(val label: String) {
    VIDEO("视频"),
    SEGMENT("媒体分段"),
    AUDIO("音频"),
    IMAGE("图片"),
    PLAYLIST("媒体清单"),
    DOCUMENT("文档"),
    ARCHIVE("压缩包"),
    APPLICATION("应用与安装包"),
    FONT("字体"),
    TEXT_DATA("代码与数据"),
    OTHER("其他文件")
}

data class SniffedResource(
    val url: String,
    val kind: ResourceKind,
    val fileName: String,
    val mimeType: String?,
    val pageTitle: String,
    val sourcePage: String
) {
    val id: String get() = url
}

object ResourceSniffer {
    fun detect(
        rawUrl: String,
        hintedKind: String? = null,
        hintedMime: String? = null,
        hintedFileName: String? = null,
        pageTitle: String = "",
        sourcePage: String = ""
    ): SniffedResource? {
        val detection = ResourceClassifier.detect(rawUrl, hintedKind, hintedMime, hintedFileName) ?: return null
        val kind = runCatching {
            ResourceKind.valueOf(detection.kind.name)
        }.getOrNull() ?: return null

        return SniffedResource(
            url = detection.url,
            kind = kind,
            fileName = detection.fileName,
            mimeType = detection.mimeType,
            pageTitle = pageTitle,
            sourcePage = sourcePage
        )
    }
}

data class DownloadTaskUi(
    val id: String,
    val fileName: String,
    val progress: Int,
    val state: String,
    val detail: String,
    val fileUri: String? = null
)
