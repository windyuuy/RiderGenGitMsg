package dev.ridergen.gitmsg

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField

/**
 * Settings > Tools > RiderGenGitMsg (DeepSeek).
 *
 * The token is loaded from / saved to the IDE PasswordSafe (CredentialStore) —
 * it is never written to ridergen-gitmsg.xml.
 */
class RiderGenConfigurable : Configurable {

    private var panel: JPanel? = null

    private val baseUrlField = JTextField(32)
    private val modelField = JTextField(32)
    private val tokenField = JPasswordField(28)
    private val testButton = JButton("测试连接").apply { addActionListener { runTokenTest() } }
    private val statusLabel = JLabel(" ")
    private val temperatureField = JTextField(8)
    private val maxDiffCharsField = JTextField(10)
    private val languageCombo = JComboBox(arrayOf("中文（zh）", "English（en）"))
    private val conventionalCommitsBox =
        JCheckBox("Use Conventional Commits style (type(scope): subject)")
    private val instructionsArea = JTextArea(6, 40).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val instructionsScroll = JBScrollPane(instructionsArea)
    private val restoreDefaultsButton =
        JButton("恢复默认规则").apply { addActionListener { restoreDefaultInstructions() } }
    private val instructionsHintLabel = JLabel(
        "<html><body style='width: 540px'>会作为 system 提示的一部分发送给 DeepSeek。" +
            "留空则保持现有默认行为；自定义规则优先级最高，Conventional 开关与响应语言" +
            "只在规则未覆盖时提供默认格式。</body></html>"
    )
    private val hintLabel = JLabel()

    private var loadedToken: String = ""
    private var legacyTokenPending: Boolean = false
    private var testInProgress: Boolean = false

    private val settings: RiderGenSettings
        get() = RiderGenSettings.getInstance()

    override fun getDisplayName(): String = "RiderGenGitMsg (DeepSeek)"

    override fun createComponent(): JComponent {
        if (panel == null) {
            val tokenRow = JPanel(BorderLayout(8, 0)).apply {
                add(tokenField, BorderLayout.CENTER)
                add(testButton, BorderLayout.EAST)
            }
            val tokenArea = JPanel(BorderLayout(0, 4)).apply {
                add(tokenRow, BorderLayout.NORTH)
                add(statusLabel, BorderLayout.CENTER)
            }

            val instructionsFooter = JPanel(BorderLayout(8, 0)).apply {
                add(restoreDefaultsButton, BorderLayout.WEST)
                add(instructionsHintLabel, BorderLayout.CENTER)
            }

            panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Base URL:", baseUrlField)
                .addLabeledComponent("Model:", modelField)
                .addLabeledComponent("DeepSeek API Token:", tokenArea)
                .addLabeledComponent("Temperature (0-2):", temperatureField)
                .addLabeledComponent("响应语言 / Response language:", languageCombo)
                .addComponent(conventionalCommitsBox)
                .addLabeledComponent("Max diff characters:", maxDiffCharsField)
                .addLabeledComponent("生成规则 / 额外提示词:", instructionsScroll)
                .addComponent(instructionsFooter)
                .addComponent(hintLabel)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = settings.current
        val token = String(tokenField.password).trim()
        return baseUrlField.text.trim().ifBlank { RiderGenSettings.DEFAULT_BASE_URL } !=
            state.baseUrl.ifBlank { RiderGenSettings.DEFAULT_BASE_URL } ||
            modelField.text.trim().ifBlank { RiderGenSettings.DEFAULT_MODEL } !=
            state.model.ifBlank { RiderGenSettings.DEFAULT_MODEL } ||
            token != loadedToken ||
            temperatureField.text.trim().toDoubleOrNull() != state.temperature ||
            maxDiffCharsField.text.trim().toIntOrNull() != state.maxDiffChars ||
            languageCombo.selectedIndex != (if (state.responseLanguage == "en") 1 else 0) ||
            conventionalCommitsBox.isSelected != state.conventionalCommits ||
            instructionsArea.text.trim() != state.customInstructions.trim() ||
            legacyTokenPending
    }

    override fun apply() {
        val token = String(tokenField.password).trim()
        val language = if (languageCombo.selectedIndex == 1) "en" else "zh"

        settings.update { state ->
            state.baseUrl = baseUrlField.text.trim().ifBlank { RiderGenSettings.DEFAULT_BASE_URL }
            state.model = modelField.text.trim().ifBlank { RiderGenSettings.DEFAULT_MODEL }
            state.temperature = (temperatureField.text.trim().toDoubleOrNull() ?: 0.2).coerceIn(0.0, 2.0)
            state.responseLanguage = language
            state.conventionalCommits = conventionalCommitsBox.isSelected
            state.maxDiffChars = (maxDiffCharsField.text.trim().toIntOrNull() ?: 24000)
                .coerceIn(2000, 200_000)
            state.customInstructions = instructionsArea.text.trim()
            // Migrated to PasswordSafe below; never keep it in the XML again.
            state.apiKey = ""
        }

        DeepSeekTokenStore.write(token)
        loadedToken = token
        legacyTokenPending = false
        updateHint()
        setStatus("已保存。Token 已写入 IDE PasswordSafe。", error = false)
    }

    override fun reset() {
        val state = settings.current
        baseUrlField.text = state.baseUrl.ifBlank { RiderGenSettings.DEFAULT_BASE_URL }
        modelField.text = state.model.ifBlank { RiderGenSettings.DEFAULT_MODEL }
        temperatureField.text = state.temperature.toString()
        maxDiffCharsField.text = state.maxDiffChars.toString()
        languageCombo.selectedIndex = if (state.responseLanguage == "en") 1 else 0
        conventionalCommitsBox.isSelected = state.conventionalCommits
        instructionsArea.text = state.customInstructions
        instructionsArea.caretPosition = 0

        val stored = try {
            DeepSeekTokenStore.read()
        } catch (t: Throwable) {
            null
        }
        val token = stored ?: state.apiKey.takeIf { it.isNotBlank() }.orEmpty()
        tokenField.text = token
        loadedToken = token.trim()
        legacyTokenPending = stored == null && state.apiKey.isNotBlank()

        updateHint()
        if (!testInProgress) {
            testButton.text = "测试连接"
            testButton.isEnabled = true
        }
        setStatus(
            if (token.isBlank()) {
                "未配置 Token。填写后可点击“测试连接”，无需先保存。"
            } else {
                "Token 已加载。点击“测试连接”可验证有效性。"
            },
            error = false
        )
    }

    override fun disposeUIResources() {
        panel = null
        testInProgress = false
    }

    /**
     * Only fills the editor; persistence happens when the user presses Apply.
     */
    private fun restoreDefaultInstructions() {
        instructionsArea.text = RiderGenSettings.DEFAULT_INSTRUCTIONS
        instructionsArea.caretPosition = 0
    }

    private fun runTokenTest() {
        if (testInProgress) return

        val token = String(tokenField.password).trim()
        if (token.isBlank()) {
            setStatus("请先输入 DeepSeek API Token 再测试。", error = true)
            return
        }

        val baseUrl = baseUrlField.text.trim().ifBlank { RiderGenSettings.DEFAULT_BASE_URL }
        testInProgress = true
        testButton.isEnabled = false
        testButton.text = "测试中…"
        setStatus("正在请求 $baseUrl/models …", error = false)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = DeepSeekCommitMessageGenerator.testToken(baseUrl, token)
            ApplicationManager.getApplication().invokeLater {
                if (panel == null) return@invokeLater
                testInProgress = false
                testButton.isEnabled = true
                testButton.text = "测试连接"
                setStatus(result.message, error = !result.ok)
            }
        }
    }

    private fun updateHint() {
        hintLabel.text = if (legacyTokenPending) {
            "<html><body style='width: 560px'>检测到旧版 <b>ridergen-gitmsg.xml</b> 中的明文 Token。" +
                "点击 <b>Apply</b> 后会写入 IDE PasswordSafe，并从配置文件清除明文。</body></html>"
        } else {
            "<html><body style='width: 560px'>Base URL 默认指向 DeepSeek（" +
                RiderGenSettings.DEFAULT_BASE_URL +
                "），Model 默认 " + RiderGenSettings.DEFAULT_MODEL +
                "。Token 只保存在 IDE PasswordSafe（系统凭据存储）中，不会明文写入项目或 XML。" +
                "未配置 Token 时生成按钮会使用本地模板。</body></html>"
        }
    }

    private fun setStatus(text: String, error: Boolean) {
        statusLabel.text = "<html><body style='width: 480px'>${escapeHtml(text)}</body></html>"
        statusLabel.foreground = if (error) ERROR_COLOR else OK_COLOR
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    companion object {
        private val OK_COLOR = JBColor(0x1B7F3B, 0x77C58A)
        private val ERROR_COLOR = JBColor(0xB3261E, 0xF08A82)
    }
}
