package com.sxsbsxs.filesniffing.presentation.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadTaskDetailPolicyTest {

    @Test
    fun genericWorkManagerFailureDoesNotOverwriteWorkerDiagnostic() {
        val stored = "下载失败（IOException）：连接被服务器重置"

        val actual = DownloadTaskDetailPolicy.choose(
            previousState = "failed",
            previousDetail = stored,
            incomingState = "failed",
            incomingDetail = "任务异常退出（未返回错误详情）；可以尝试重试"
        )

        assertEquals(stored, actual)
    }

    @Test
    fun concreteWorkManagerFailureCanReplaceAnOlderGenericDetail() {
        val actual = DownloadTaskDetailPolicy.choose(
            previousState = "failed",
            previousDetail = "任务异常退出（未返回错误详情）",
            incomingState = "failed",
            incomingDetail = "下载任务启动失败（IllegalStateException）：前台服务启动失败"
        )

        assertEquals("下载任务启动失败（IllegalStateException）：前台服务启动失败", actual)
    }

    @Test
    fun newWorkerStateUsesIncomingDetail() {
        val actual = DownloadTaskDetailPolicy.choose(
            previousState = "downloading",
            previousDetail = "正在下载",
            incomingState = "failed",
            incomingDetail = "下载失败（IOException）：超时"
        )

        assertEquals("下载失败（IOException）：超时", actual)
    }

    @Test
    fun nonFailureUpdatesAreNotOverriddenByOldFailureText() {
        val actual = DownloadTaskDetailPolicy.choose(
            previousState = "failed",
            previousDetail = "下载失败（IOException）：旧错误",
            incomingState = "completed",
            incomingDetail = "下载完成"
        )

        assertEquals("下载完成", actual)
    }
}
