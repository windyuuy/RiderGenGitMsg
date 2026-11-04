package dev.ridergen.gitmsg

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Toolbar button in the commit message field, registered right after
 * `Vcs.ShowMessageHistory` (the clock icon) in `Vcs.MessageActionGroup`.
 */
class GenerateCommitMessageAction : AnAction(
    "Generate commit message with DeepSeek",
    "Generate a Git commit message from staged changes using DeepSeek",
    IconLoader.getIcon("/icons/deepseekGenerate.svg", GenerateCommitMessageAction::class.java)
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val hasCommitMessageUi = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null ||
            e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) != null
        e.presentation.isEnabled = e.project != null && hasCommitMessageUi && !running.get()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessage = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val document = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT)
        if (commitMessage == null && document == null) return

        val existing = document?.text?.takeIf { it.isNotBlank() }
        val appendMode = if (existing == null) {
            false
        } else {
            when (askHowToHandleExisting(project)) {
                ExistingContentAction.CANCEL -> return
                ExistingContentAction.APPEND -> true
                ExistingContentAction.OVERWRITE -> false
            }
        }

        if (!running.compareAndSet(false, true)) {
            notify(project, "DeepSeek 正在生成提交信息，请等待本次请求完成。", NotificationType.INFORMATION)
            return
        }

        val settings = RiderGenSettings.getInstance().current.copy()
        val progressNotification = notify(project, "正在调用 DeepSeek 生成提交信息…", NotificationType.INFORMATION)

        object : Task.Backgroundable(project, "Generating commit message with DeepSeek", true) {
            private var generated: String? = null
            private var problem: String? = null
            private var usedLocalFallback = false
            private var usedLegacyToken = false

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "读取已暂存改动…"

                val snapshot = try {
                    GitStagedChanges.read(project)
                } catch (t: Throwable) {
                    problem = "无法读取已暂存改动：${t.message ?: t.javaClass.simpleName}"
                    return
                }
                if (snapshot.files.isEmpty()) {
                    problem = "没有已暂存改动，请先 git add 后再生成提交信息。"
                    return
                }

                val token = try {
                    DeepSeekTokenStore.effectiveToken(settings)
                } catch (t: Throwable) {
                    null
                }
                usedLegacyToken = try {
                    DeepSeekTokenStore.hasLegacyToken(settings)
                } catch (t: Throwable) {
                    false
                }

                if (token.isNullOrBlank()) {
                    usedLocalFallback = true
                    generated = HeuristicCommitMessageGenerator.generate(
                        snapshot.files,
                        snapshot.stat,
                        settings.conventionalCommits,
                        settings.responseLanguage,
                        settings.customInstructions
                    )
                    return
                }

                indicator.text = "调用 DeepSeek（${settings.model}）…"
                generated = try {
                    DeepSeekCommitMessageGenerator.generate(settings, snapshot, token)
                } catch (t: Throwable) {
                    problem = t.message ?: t.javaClass.simpleName
                    null
                }
            }

            override fun onSuccess() {
                if (project.isDisposed) return
                val message = generated
                if (message == null) {
                    notify(project, problem ?: "生成提交信息失败", NotificationType.ERROR)
                    return
                }

                val finalText = if (appendMode && existing != null) {
                    existing.trimEnd() + "\n\n" + message
                } else {
                    message
                }
                insertMessage(project, commitMessage, document, finalText)

                when {
                    usedLocalFallback -> notify(
                        project,
                        "未配置 DeepSeek API Token，已用本地模板生成。请在 Settings > Tools > RiderGenGitMsg (DeepSeek) 配置 Token。",
                        NotificationType.WARNING
                    )

                    usedLegacyToken -> notify(
                        project,
                        "已使用旧版明文 Token 生成。请打开设置页点击 Apply，把 Token 迁移到 IDE PasswordSafe。",
                        NotificationType.WARNING
                    )

                    else -> notify(project, "DeepSeek 已生成提交信息。", NotificationType.INFORMATION)
                }
            }

            override fun onThrowable(error: Throwable) {
                if (project.isDisposed) return
                notify(project, "生成提交信息失败：${error.message ?: error.javaClass.simpleName}", NotificationType.ERROR)
            }

            override fun onFinished() {
                progressNotification.expire()
                running.set(false)
            }
        }.queue()
    }

    private fun askHowToHandleExisting(project: Project): ExistingContentAction {
        val options = arrayOf("覆盖", "追加", "取消")
        val index = Messages.showDialog(
            project,
            "提交信息输入框已有内容。DeepSeek 生成完成后如何处理？\n\n" +
                "覆盖：替换现有内容\n" +
                "追加：保留现有内容，空一行后追加生成结果",
            "RiderGenGitMsg",
            options,
            0,
            Messages.getQuestionIcon()
        )
        return when (index) {
            1 -> ExistingContentAction.APPEND
            2 -> ExistingContentAction.CANCEL
            else -> ExistingContentAction.OVERWRITE
        }
    }

    private fun insertMessage(
        project: Project,
        commitMessage: CommitMessageI?,
        document: Document?,
        message: String
    ) {
        if (commitMessage != null) {
            commitMessage.setCommitMessage(message)
            return
        }
        if (document != null) {
            CommandProcessor.getInstance().executeCommand(
                project,
                {
                    ApplicationManager.getApplication().runWriteAction {
                        document.setText(message)
                    }
                },
                "Generate commit message with DeepSeek",
                null
            )
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) =
        NotificationGroupManager.getInstance()
            .getNotificationGroup("RiderGenGitMsg")
            .createNotification(content, type)
            .also { it.notify(project) }

    private enum class ExistingContentAction {
        OVERWRITE,
        APPEND,
        CANCEL
    }

    companion object {
        private val running = AtomicBoolean(false)
    }
}
