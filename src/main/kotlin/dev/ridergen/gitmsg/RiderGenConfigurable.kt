package dev.ridergen.gitmsg

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Settings > Tools > AIGenCommitMsg (DeepSeek).
 *
 * The token is never shown as plaintext again: the field starts empty, the page only reports
 * "已保存 ●●●●", and the "显示" button reveals a short-lived masked summary (first 4 + last 4).
 * The token is read from storage on demand by generation / connection tests.
 */
class RiderGenConfigurable : Configurable {

    private var panel: JPanel? = null

    private val baseUrlField = JTextField(32)
    private val modelField = JTextField(32)
    private val tokenField = JPasswordField(28)
    private val tokenStatusLabel = JLabel(" ")
    private val showTokenButton = JButton("显示").apply { addActionListener { showStoredToken() } }
    private val clearTokenButton =
        JButton("清除 Token").apply { addActionListener { clearStoredToken() } }
    private val migrateTokenButton =
        JButton("迁移到系统钥匙串").apply { addActionListener { runMigration() } }
    private val testButton = JButton("测试连接").apply { addActionListener { runTokenTest() } }
    private val statusLabel = JLabel(" ")
    private val temperatureField = JTextField(8)
    private val maxDiffCharsField = JTextField(10)
    private val customHostConfirmBox =
        JCheckBox("我确认 Token 会发送到上面的自定义地址（非 api.deepseek.com）")
    private val customHostWarningLabel = JLabel()
    private val allowLocalhostHttpBox =
        JCheckBox("允许 http://localhost 调试（仅本机回环地址，测试用）")
    private val languageChooser = LanguageChooser(CommitLanguages.DEFAULT)
    private val languageHintLabel = JLabel(
        "<html><body style='width: 540px'>语言列表顺序为随机排列，与语言热度、推荐程度或任何其它因素无关。" +
            "点击下拉框可按国家代码、英文名或本土名搜索。</body></html>"
    )
    private val languageRow = JPanel(BorderLayout(0, 4)).apply {
        add(languageChooser, BorderLayout.NORTH)
        add(languageHintLabel, BorderLayout.CENTER)
    }
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

    /** True only while the user has typed something that still needs saving/clearing. */
    private var tokenDirty: Boolean = false

    /** Whether a token exists in the active store (never holds the plaintext). */
    private var tokenLoaded: Boolean = false

    /** Suppresses dirty tracking while the field is updated programmatically. */
    private var suppressTokenDirty: Boolean = false

    /** The field currently contains the temporary masked summary, not user input. */
    private var fieldShowsMask: Boolean = false

    private var maskTimer: Timer? = null

    private var activeStoreName: String? = null
    private var storageErrors: List<String> = emptyList()

    private var testInProgress: Boolean = false
    private var storageBusy: Boolean = false

    private val settings: RiderGenSettings
        get() = RiderGenSettings.getInstance()

    override fun getDisplayName(): String = "AIGenCommitMsg (DeepSeek)"

    override fun createComponent(): JComponent {
        if (panel == null) {
            val tokenButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(testButton)
                add(showTokenButton)
                add(clearTokenButton)
                add(migrateTokenButton)
            }
            val tokenArea = JPanel(BorderLayout(0, 4)).apply {
                add(tokenField, BorderLayout.NORTH)
                add(tokenButtons, BorderLayout.CENTER)
                add(tokenStatusLabel, BorderLayout.SOUTH)
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
                .addLabeledComponent("提交信息语言 / Commit message language:", languageRow)
                .addComponent(conventionalCommitsBox)
                .addLabeledComponent("Max diff characters:", maxDiffCharsField)
                .addLabeledComponent("生成规则 / 额外提示词:", instructionsScroll)
                .addComponent(instructionsFooter)
                .addComponent(customHostConfirmBox)
                .addComponent(customHostWarningLabel)
                .addComponent(allowLocalhostHttpBox)
                .addComponent(hintLabel)
                .addComponentFillVertically(JPanel(), 0)
                .panel

            installListeners()
        }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = settings.current
        return baseUrlField.text.trim().ifBlank { RiderGenSettings.DEFAULT_BASE_URL } !=
            state.baseUrl.ifBlank { RiderGenSettings.DEFAULT_BASE_URL } ||
            modelField.text.trim().ifBlank { RiderGenSettings.DEFAULT_MODEL } !=
            state.model.ifBlank { RiderGenSettings.DEFAULT_MODEL } ||
            tokenDirty ||
            temperatureField.text.trim().toDoubleOrNull() != state.temperature ||
            maxDiffCharsField.text.trim().toIntOrNull() != state.maxDiffChars ||
            languageChooser.selectedLanguage.storageValue != state.resolvedLanguage().storageValue ||
            conventionalCommitsBox.isSelected != state.conventionalCommits ||
            instructionsArea.text.trim() != state.customInstructions.trim() ||
            customHostConfirmBox.isSelected != state.customBaseUrlConfirmed ||
            allowLocalhostHttpBox.isSelected != state.allowLocalhostHttp
    }

    override fun apply() {
        val baseUrl = baseUrlField.text.trim().ifBlank { RiderGenSettings.DEFAULT_BASE_URL }
        val customHost = DeepSeekUrlPolicy.isCustomHost(baseUrl)
        val confirmed = customHost && customHostConfirmBox.isSelected
        val allowLocalhostHttp = allowLocalhostHttpBox.isSelected

        when (val policy = DeepSeekUrlPolicy.validate(baseUrl, confirmed, allowLocalhostHttp)) {
            is DeepSeekUrlPolicy.Result.Rejected -> {
                setStatus("未保存：${policy.reason}", error = true)
                return
            }

            DeepSeekUrlPolicy.Result.Allowed -> Unit
        }

        val language = languageChooser.selectedLanguage
        settings.update { state ->
            state.baseUrl = baseUrl
            state.model = modelField.text.trim().ifBlank { RiderGenSettings.DEFAULT_MODEL }
            state.temperature = (temperatureField.text.trim().toDoubleOrNull() ?: 0.2)
                .coerceIn(0.0, 2.0)
            state.responseLanguage = ""
            state.commitLanguage = language.storageValue
            state.conventionalCommits = conventionalCommitsBox.isSelected
            state.maxDiffChars = (maxDiffCharsField.text.trim().toIntOrNull() ?: 24000)
                .coerceIn(2000, 200_000)
            state.customInstructions = instructionsArea.text.trim()
            state.customBaseUrlConfirmed = confirmed
            state.allowLocalhostHttp = allowLocalhostHttp
        }

        if (tokenDirty) {
            applyTokenChange()
        } else {
            setStatus("已保存。", error = false)
        }

        updateUrlWarning()
        updateHint()
        updateTokenStatus()
    }

    override fun reset() {
        val state = settings.current
        baseUrlField.text = state.baseUrl.ifBlank { RiderGenSettings.DEFAULT_BASE_URL }
        modelField.text = state.model.ifBlank { RiderGenSettings.DEFAULT_MODEL }
        temperatureField.text = state.temperature.toString()
        maxDiffCharsField.text = state.maxDiffChars.toString()
        languageChooser.select(state.resolvedLanguage())
        languageChooser.reshuffle()
        conventionalCommitsBox.isSelected = state.conventionalCommits
        instructionsArea.text = state.customInstructions
        instructionsArea.caretPosition = 0
        customHostConfirmBox.isSelected = state.customBaseUrlConfirmed
        allowLocalhostHttpBox.isSelected = state.allowLocalhostHttp

        maskTimer?.stop()
        maskTimer = null
        setTokenFieldText("")
        fieldShowsMask = false
        tokenDirty = false
        tokenLoaded = false
        activeStoreName = null
        storageErrors = emptyList()

        updateUrlWarning()
        updateHint()
        updateTokenStatus()
        if (!testInProgress) {
            testButton.text = "测试连接"
            updateActionButtons()
        }
        setStatus("正在检测 Token 存储…", error = false)
        refreshTokenStatusAsync(updateStatus = true)
    }

    override fun disposeUIResources() {
        maskTimer?.stop()
        maskTimer = null
        setTokenFieldText("")
        fieldShowsMask = false
        tokenDirty = false
        tokenLoaded = false
        activeStoreName = null
        storageErrors = emptyList()
        panel = null
        testInProgress = false
        storageBusy = false
    }

    /**
     * Only fills the editor; persistence happens when the user presses Apply.
     */
    private fun restoreDefaultInstructions() {
        instructionsArea.text = RiderGenSettings.DEFAULT_INSTRUCTIONS
        instructionsArea.caretPosition = 0
    }

    private fun installListeners() {
        tokenField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = markTokenDirty()
            override fun removeUpdate(e: DocumentEvent) = markTokenDirty()
            override fun changedUpdate(e: DocumentEvent) = markTokenDirty()
        })
        baseUrlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateUrlWarning()
            override fun removeUpdate(e: DocumentEvent) = updateUrlWarning()
            override fun changedUpdate(e: DocumentEvent) = updateUrlWarning()
        })
    }

    private fun markTokenDirty() {
        if (suppressTokenDirty) return
        maskTimer?.stop()
        fieldShowsMask = false
        tokenDirty = true
        updateTokenStatus()
    }

    private fun applyTokenChange() {
        val chars = tokenField.password
        val newToken = try {
            String(chars).trim()
        } finally {
            chars.fill('\u0000')
        }

        val outcome = if (newToken.isEmpty()) {
            TokenStorage.service().clearAll()
        } else {
            TokenStorage.service().writeToken(newToken)
        }

        if (outcome.success) {
            // Write new → read back (inside the service) → only now drop the legacy XML value.
            settings.update { state -> state.apiKey = "" }
            tokenLoaded = newToken.isNotEmpty()
            tokenDirty = false
            setTokenFieldText("")
            activeStoreName = outcome.storeName ?: activeStoreName
            setStatus(outcome.message, error = false)
        } else {
            setStatus("${outcome.message}；旧 Token 未被删除", error = true)
        }
    }

    private fun showStoredToken() {
        if (fieldShowsMask) {
            setTokenFieldText("")
            fieldShowsMask = false
            updateTokenStatus()
            return
        }

        val chars = tokenField.password
        val typed = try {
            String(chars).trim()
        } finally {
            chars.fill('\u0000')
        }
        if (tokenDirty && typed.isNotEmpty()) {
            displayMasked(typed)
            return
        }

        showTokenButton.isEnabled = false
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { TokenStorage.service().readToken() }
            val value = result.getOrNull()
            val token = value?.token?.let { String(it) }
            value?.close()
            ApplicationManager.getApplication().invokeLater {
                if (panel == null) return@invokeLater
                showTokenButton.isEnabled = true
                if (token.isNullOrBlank()) {
                    setStatus("没有已保存的 Token 可供显示。", error = true)
                } else {
                    activeStoreName = value.storeName ?: activeStoreName
                    displayMasked(token)
                }
            }
        }
    }

    private fun displayMasked(token: String) {
        setTokenFieldText(SecurityRedaction.maskToken(token))
        fieldShowsMask = true
        maskTimer?.stop()
        maskTimer = Timer(SHOW_TOKEN_MILLIS, java.awt.event.ActionListener { clearMaskedToken() })
            .apply {
                isRepeats = false
                start()
            }
        setStatus("已显示脱敏摘要（前 4 位与后 4 位），${SHOW_TOKEN_SECONDS} 秒后自动清空。", error = false)
        updateTokenStatus()
    }

    private fun clearMaskedToken() {
        if (!fieldShowsMask) return
        setTokenFieldText("")
        fieldShowsMask = false
        if (panel != null) updateTokenStatus()
    }

    private fun clearStoredToken() {
        if (storageBusy) return
        maskTimer?.stop()
        maskTimer = null
        setTokenFieldText("")
        fieldShowsMask = false
        tokenDirty = false
        storageBusy = true
        updateActionButtons()
        setStatus("正在清除 Token…", error = false)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = TokenStorage.service().clearAll()
            ApplicationManager.getApplication().invokeLater {
                storageBusy = false
                if (panel == null) return@invokeLater
                tokenLoaded = false
                activeStoreName = null
                updateActionButtons()
                updateTokenStatus()
                updateHint()
                setStatus(result.message, error = !result.success)
            }
        }
    }

    private fun runMigration() {
        if (storageBusy) return
        storageBusy = true
        updateActionButtons()
        setStatus("正在迁移 Token 到系统钥匙串…", error = false)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { TokenStorage.service().migrateToSystem() }
            ApplicationManager.getApplication().invokeLater {
                storageBusy = false
                if (panel == null) return@invokeLater
                updateActionButtons()
                val migration = result.getOrNull()
                if (migration == null) {
                    setStatus("当前平台没有可用的系统钥匙串，继续使用 IDE PasswordSafe。", error = true)
                } else {
                    setStatus(migration.message, error = !migration.success)
                }
                refreshTokenStatusAsync()
            }
        }
    }

    private fun refreshTokenStatusAsync(updateStatus: Boolean = false) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { TokenStorage.service().readToken() }
            val value = result.getOrNull()
            val hasToken = value?.hasToken() == true
            val storeName = value?.storeName
            val migration = value?.migration
            val errors = value?.errors.orEmpty().ifEmpty {
                result.exceptionOrNull()?.let { listOf(it.message ?: it.javaClass.simpleName) }
                    ?: emptyList()
            }
            value?.close()
            ApplicationManager.getApplication().invokeLater {
                if (panel == null) return@invokeLater
                tokenLoaded = hasToken
                activeStoreName = storeName ?: activeStoreName
                storageErrors = errors
                updateTokenStatus()
                updateHint()
                updateActionButtons()
                if (migration != null) {
                    setStatus(migration.message, error = !migration.success)
                } else if (updateStatus && !testInProgress) {
                    setStatus("就绪。", error = false)
                }
            }
        }
    }

    private fun runTokenTest() {
        if (testInProgress) return

        val baseUrl = baseUrlField.text.trim().ifBlank { RiderGenSettings.DEFAULT_BASE_URL }
        val customHost = DeepSeekUrlPolicy.isCustomHost(baseUrl)
        val confirmed = customHost && customHostConfirmBox.isSelected
        val allowLocalhostHttp = allowLocalhostHttpBox.isSelected

        when (val policy = DeepSeekUrlPolicy.validate(baseUrl, confirmed, allowLocalhostHttp)) {
            is DeepSeekUrlPolicy.Result.Rejected -> {
                setStatus("无法测试：${policy.reason}", error = true)
                return
            }

            DeepSeekUrlPolicy.Result.Allowed -> Unit
        }

        val chars = tokenField.password
        val typed = try {
            if (fieldShowsMask) "" else String(chars).trim()
        } finally {
            chars.fill('\u0000')
        }

        if (typed.isEmpty()) {
            startTestFromStorage(baseUrl, allowLocalhostHttp, confirmed)
        } else {
            val typedChars = typed.toCharArray()
            startTest(baseUrl, typedChars, allowLocalhostHttp, confirmed)
        }
    }

    private fun startTestFromStorage(
        baseUrl: String,
        allowLocalhostHttp: Boolean,
        customConfirmed: Boolean
    ) {
        testInProgress = true
        beginTestUi(baseUrl)

        ApplicationManager.getApplication().executeOnPooledThread {
            val read = runCatching { TokenStorage.service().readToken() }
            val value = read.getOrNull()
            try {
                val chars = value?.token
                if (chars == null || chars.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        if (panel == null) return@invokeLater
                        finishTestUi()
                        setStatus("没有已保存的 Token，请在上方输入后再测试。", error = true)
                    }
                } else {
                    val token = String(chars)
                    val result = DeepSeekCommitMessageGenerator.testToken(
                        baseUrl,
                        token,
                        allowLocalhostHttp,
                        customConfirmed
                    )
                    ApplicationManager.getApplication().invokeLater {
                        if (panel == null) return@invokeLater
                        finishTestUi()
                        setStatus(SecurityRedaction.redact(result.message, token), error = !result.ok)
                    }
                }
            } finally {
                value?.close()
            }
        }
    }

    private fun startTest(
        baseUrl: String,
        tokenChars: CharArray,
        allowLocalhostHttp: Boolean,
        customConfirmed: Boolean
    ) {
        testInProgress = true
        beginTestUi(baseUrl)

        ApplicationManager.getApplication().executeOnPooledThread {
            val token = String(tokenChars)
            try {
                val result = DeepSeekCommitMessageGenerator.testToken(
                    baseUrl,
                    token,
                    allowLocalhostHttp,
                    customConfirmed
                )
                ApplicationManager.getApplication().invokeLater {
                    if (panel == null) return@invokeLater
                    finishTestUi()
                    setStatus(SecurityRedaction.redact(result.message, token), error = !result.ok)
                }
            } catch (t: Throwable) {
                ApplicationManager.getApplication().invokeLater {
                    if (panel == null) return@invokeLater
                    finishTestUi()
                    setStatus(
                        SecurityRedaction.redact(
                            "测试失败：${t.message ?: t.javaClass.simpleName}",
                            token
                        ),
                        error = true
                    )
                }
            } finally {
                tokenChars.fill('\u0000')
            }
        }
    }

    private fun beginTestUi(baseUrl: String) {
        testButton.text = "测试中…"
        updateActionButtons()
        setStatus(
            "正在请求 ${SecurityRedaction.sanitizeUrl(baseUrl)}/models …",
            error = false
        )
    }

    private fun finishTestUi() {
        testInProgress = false
        testButton.text = "测试连接"
        updateActionButtons()
    }

    private fun updateActionButtons() {
        testButton.isEnabled = !testInProgress && !storageBusy
        showTokenButton.isEnabled = !storageBusy
        clearTokenButton.isEnabled = !storageBusy
        migrateTokenButton.isEnabled = !storageBusy && tokenLoaded
    }

    private fun setTokenFieldText(value: String) {
        suppressTokenDirty = true
        try {
            tokenField.text = value
            tokenField.caretPosition = 0
        } finally {
            suppressTokenDirty = false
        }
    }

    private fun updateUrlWarning() {
        val baseUrl = baseUrlField.text.trim().ifBlank { RiderGenSettings.DEFAULT_BASE_URL }
        val customHost = DeepSeekUrlPolicy.isCustomHost(baseUrl)
        customHostConfirmBox.isEnabled = customHost
        customHostWarningLabel.isVisible = customHost
        customHostWarningLabel.text = if (customHost) {
            "<html><body style='width: 560px'><b>警告：</b>Token 会发送到自定义地址 " +
                "<b>${escapeHtml(DeepSeekUrlPolicy.hostOf(baseUrl) ?: baseUrl)}</b>。" +
                "仅在你信任该地址时使用；请求前会再次校验协议与端口。</body></html>"
        } else {
            ""
        }
        updateHint()
    }

    private fun updateTokenStatus() {
        val chars = tokenField.password
        val typed = try {
            String(chars).trim()
        } finally {
            chars.fill('\u0000')
        }
        val location = activeStoreName ?: "凭据存储"
        val text = when {
            fieldShowsMask -> "已显示脱敏摘要（前 4 位与后 4 位）"
            tokenDirty && typed.isNotEmpty() -> "新的 Token 将在点击 Apply 后保存到 $location"
            tokenDirty -> "Token 将被清除（点击 Apply 后生效）"
            tokenLoaded -> "已保存 ●●●●（$location）"
            storageErrors.isNotEmpty() -> "读取失败：${storageErrors.first()}"
            else -> "未保存 Token（未配置时使用本地模板）"
        }
        tokenStatusLabel.text = "<html><body style='width: 480px'>${escapeHtml(text)}</body></html>"
        tokenStatusLabel.foreground = when {
            storageErrors.isNotEmpty() && !tokenLoaded -> ERROR_COLOR
            tokenLoaded || fieldShowsMask -> OK_COLOR
            else -> JBColor.foreground()
        }
    }

    private fun updateHint() {
        val location = activeStoreName
        val storageLine = if (location == null) {
            "Token 优先保存到系统钥匙串（Windows 凭据管理器 / macOS 钥匙串 / Linux Secret Service），" +
                "不可用时回退到 IDE PasswordSafe。"
        } else {
            "当前 Token 存储位置：<b>${escapeHtml(location)}</b>。"
        }
        hintLabel.text =
            "<html><body style='width: 560px'>" + storageLine +
                "设置页不会回填明文 Token；旧版 ridergen-gitmsg.xml 中的明文会在首次使用或点击 Apply 时自动迁移。" +
                "Base URL 默认 <b>${RiderGenSettings.DEFAULT_BASE_URL}</b>，仅允许 https；" +
                "自定义地址需要勾选确认。发送 diff 前会默认打码疑似密钥。</body></html>"
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
        private const val SHOW_TOKEN_SECONDS = 5
        private const val SHOW_TOKEN_MILLIS = SHOW_TOKEN_SECONDS * 1000
    }
}
