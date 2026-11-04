package dev.ridergen.gitmsg

import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Random
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Searchable language picker.
 *
 * Display format: `CN · Chinese (Simplified) · 简体中文`.
 * Filtering matches the country code, the English name or the native name (case-insensitive).
 * The order of the full list is shuffled (in [reshuffle]) and is unrelated to popularity.
 */
class LanguageChooser(initial: CommitLanguage) : JPanel(BorderLayout(6, 0)) {

    private val displayField = JTextField(42).apply {
        isEditable = false
        isFocusable = false
    }
    private val openButton = JButton("▼").apply {
        toolTipText = "打开语言列表 / Open language list"
    }
    private val filterField = JTextField()
    private val listModel = DefaultListModel<CommitLanguage>()
    private val list = JList(listModel)
    private val popup = JPopupMenu()
    private val emptyItem = CommitLanguage("", "", "No matching languages / 无匹配语言")

    private var order: List<CommitLanguage> = CommitLanguages.shuffled()

    var selectedLanguage: CommitLanguage = initial
        private set

    init {
        add(displayField, BorderLayout.CENTER)
        add(openButton, BorderLayout.EAST)
        updateDisplay()

        displayField.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) showPopup()
            }
        })
        openButton.addActionListener { showPopup() }

        configurePopup()
    }

    /** Re-randomizes the display order without touching [selectedLanguage]. */
    fun reshuffle(random: Random = Random()) {
        order = CommitLanguages.shuffled(CommitLanguages.ALL, random)
        if (popup.isVisible) refresh()
    }

    fun select(language: CommitLanguage) {
        selectedLanguage = language
        updateDisplay()
    }

    private fun updateDisplay() {
        val label = selectedLanguage.displayLabel()
        displayField.text = label
        displayField.toolTipText = label
    }

    private fun configurePopup() {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.visibleRowCount = 14
        list.cellRenderer = ListCellRenderer<CommitLanguage> { _, value, _, isSelected, _ ->
            val background = if (isSelected) {
                UIManager.getColor("List.selectionBackground")
            } else {
                list.background
            }
            val foreground = if (isSelected) {
                UIManager.getColor("List.selectionForeground")
            } else {
                list.foreground
            }
            JLabel(
                if (value.countryCode.isBlank()) value.nativeName else value.displayLabel()
            ).apply {
                isOpaque = true
                this.background = background
                this.foreground = foreground
                border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            }
        }

        list.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            val value = list.selectedValue ?: return@addListSelectionListener
            if (value.countryCode.isBlank()) return@addListSelectionListener
            select(value)
            popup.isVisible = false
        }

        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val value = list.selectedValue
                        if (value != null && value.countryCode.isNotBlank()) {
                            select(value)
                            popup.isVisible = false
                        }
                    }
                    KeyEvent.VK_ESCAPE -> popup.isVisible = false
                }
            }
        })

        filterField.toolTipText = "按国家代码、英文名或本土名搜索 / Search by code, English or native name"
        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refresh()
            override fun removeUpdate(e: DocumentEvent) = refresh()
            override fun changedUpdate(e: DocumentEvent) = refresh()
        })
        filterField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE -> popup.isVisible = false
                    KeyEvent.VK_DOWN -> if (listModel.size() > 0) {
                        list.selectedIndex = 0
                        list.requestFocusInWindow()
                    }
                    KeyEvent.VK_ENTER -> {
                        val first = listModel.elements().asSequence()
                            .firstOrNull { it.countryCode.isNotBlank() }
                        if (first != null) {
                            select(first)
                            popup.isVisible = false
                        }
                    }
                }
            }
        })

        val header = JPanel(BorderLayout(6, 0)).apply {
            add(JLabel("搜索 / Search:"), BorderLayout.WEST)
            add(filterField, BorderLayout.CENTER)
        }
        val content = JPanel(BorderLayout(0, 6)).apply {
            preferredSize = Dimension(640, 380)
            add(header, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
        }
        popup.add(content)
    }

    private fun showPopup() {
        filterField.text = ""
        refresh()
        list.clearSelection()
        popup.show(displayField, 0, displayField.height)
        filterField.requestFocusInWindow()
    }

    private fun refresh() {
        val results = CommitLanguages.search(order, filterField.text)
        listModel.clear()
        if (results.isEmpty()) {
            listModel.addElement(emptyItem)
        } else {
            results.forEach(listModel::addElement)
        }
        list.isEnabled = results.isNotEmpty()
    }
}
