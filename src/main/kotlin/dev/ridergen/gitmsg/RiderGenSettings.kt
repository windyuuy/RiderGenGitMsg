package dev.ridergen.gitmsg

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "RiderGenGitMsgSettings", storages = [Storage("ridergen-gitmsg.xml")])
class RiderGenSettings : PersistentStateComponent<RiderGenSettings.State> {

    data class State(
        /** DeepSeek OpenAI-compatible base URL, without the /chat/completions suffix. */
        var baseUrl: String = DEFAULT_BASE_URL,
        var model: String = DEFAULT_MODEL,
        /** Legacy plaintext token from 0.1.x. Migrated to PasswordSafe on Apply. */
        var apiKey: String = "",
        var temperature: Double = 0.2,
        /** "zh" (Simplified Chinese) or "en". */
        var responseLanguage: String = "zh",
        var conventionalCommits: Boolean = true,
        var maxDiffChars: Int = 24000,
        /**
         * Extra user instructions appended to the system prompt as the highest-priority rules.
         * Empty means the built-in default behaviour is unchanged.
         */
        var customInstructions: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        // 0.1.x shipped OpenAI defaults; normalize them to DeepSeek on upgrade.
        if (state.baseUrl.isBlank() || state.baseUrl.contains("api.openai.com")) {
            state.baseUrl = DEFAULT_BASE_URL
        }
        if (state.model.isBlank() || state.model == "gpt-4o-mini" || state.model == "gpt-4o") {
            state.model = DEFAULT_MODEL
        }
    }

    val current: State
        get() = myState

    fun update(block: (State) -> Unit) {
        block(myState)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"

        /**
         * Text filled in by the settings page "恢复默认规则" button.
         * It is NOT applied automatically — the user must press Apply to persist it.
         */
        val DEFAULT_INSTRUCTIONS: String = """
            - First line: a short imperative summary, at most 72 characters, no trailing period.
            - If Conventional Commits is enabled, use a suitable type prefix (feat, fix, docs, refactor, test, build, ci, chore); omit the scope when it is unclear.
            - Keep the body optional and short: 1-5 lines explaining why or impact, not a file-by-file diff dump.
            - Never invent files, features, issues or breaking changes that are not visible in the diff.
            - Do not output markdown fences, quotes, signatures or emojis.
        """.trimIndent()

        fun getInstance(): RiderGenSettings =
            ApplicationManager.getApplication().getService(RiderGenSettings::class.java)
    }
}
