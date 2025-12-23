package com.wanggaowan.ohosdevtools.actions.translate

import com.intellij.json.psi.JsonObject
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.wanggaowan.ohosdevtools.settings.PluginSettings
import com.wanggaowan.ohosdevtools.ui.UIColor
import com.wanggaowan.ohosdevtools.utils.NotificationUtils
import com.wanggaowan.ohosdevtools.utils.ProgressUtils
import com.wanggaowan.ohosdevtools.utils.TranslateUtils
import com.wanggaowan.ohosdevtools.utils.ex.getChildOfType
import com.wanggaowan.ohosdevtools.utils.ex.isOhosProject
import com.wanggaowan.ohosdevtools.utils.ex.resRootPath
import com.wanggaowan.ohosdevtools.utils.ex.toPsiFile
import com.wanggaowan.ohosdevtools.utils.msg.Toast
import com.wanggaowan.ohosdevtools.utils.psi.ResJsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 提取文本为多语言
 *
 * @author Created by wanggaowan on 2025/12/22 09:15
 */
open class ExtractStr2L10nWithTranslateAction(private val translateOther: Boolean = true) : DumbAwareAction() {

    private var selectedPsiElement: PsiElement? = null
    private var selectedPsiFile: PsiFile? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        if (!project.isOhosProject) {
            e.presentation.isVisible = false
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            e.presentation.isVisible = false
            return
        }

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile == null || psiFile.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (!psiFile.name.endsWith(".ets")) {
            e.presentation.isVisible = false
            return
        }

        val psiElement: PsiElement? = psiFile.findElementAt(editor.selectionModel.selectionStart)
        if (psiElement == null) {
            e.presentation.isVisible = false
            return
        }

        if (psiElement !is LeafPsiElement) {
            e.presentation.isVisible = false
            return
        }

        selectedPsiFile = psiFile
        selectedPsiElement = psiElement
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val module = event.getData(LangDataKeys.MODULE) ?: return
        val selectedFile = selectedPsiFile ?: return
        val selectedElement = selectedPsiElement ?: return

        val stringJsonFile = VirtualFileManager.getInstance()
            .findFileByUrl("file://${module.resRootPath}/base/element/string.json")
        if (stringJsonFile == null || stringJsonFile.isDirectory) {
            NotificationUtils.showBalloonMsg(
                project,
                "未配置string.json模板文件，请提供resources/base/element/string.json模版文件",
                NotificationType.ERROR
            )
            return
        }

        val stringJsonPsiFile = stringJsonFile.toPsiFile(project) ?: return
        val jsonObject = stringJsonPsiFile.getChildOfType<JsonObject>()
        if (!ResJsonUtils.checkFormat(jsonObject, "string")) {
            NotificationUtils.showBalloonMsg(
                project,
                "模版文件json数据string节点格式错误",
                NotificationType.ERROR
            )
            return
        }

        // 得到的结果格式：'xx', "xx", 'xx$a', "xx$a"
        var text = selectedElement.text
        if (text.length > 2) {
            // 去除前后单引号或双引号
            text = text.substring(1, text.length - 1)
        }

        /// 多语言Key用于翻译的文本
        var keyTranslateText = text.trim()
        val oldLength = keyTranslateText.length
        keyTranslateText = removePlaceHolder(keyTranslateText)
        val isFormat = keyTranslateText.length != oldLength
        keyTranslateText = keyTranslateText.trim()

        val otherStringsFile = mutableListOf<TranslateStringsFile>()
        val existKey: String? = ResJsonUtils.isExistKeyByResStringValue(jsonObject, text)
        val sourceLanguageAlias: String? =
            ResJsonUtils.getResStringValue(jsonObject, "locale_alias")

        if (translateOther) {
            stringJsonPsiFile.virtualFile?.parent?.parent?.parent?.children?.let { files ->
                for (file in files) {
                    val name = file.name
                    if (name.contains("base")) {
                        continue
                    }

                    val stringJson = file.findChild("element")?.findChild("string.json") ?: continue
                    val stringJsonPsi = stringJson.toPsiFile(project) ?: continue
                    val jsonObject2 = stringJsonPsi.getChildOfType<JsonObject>() ?: continue
                    if (existKey != null && ResJsonUtils.getResStringValue(jsonObject2, existKey) != null) {
                        // 其它语言已存在当前key
                        continue
                    }

                    val targetLanguage = TranslateUtils.getLanguageByDirName(name)
                    val targetLanguageAlias =
                        ResJsonUtils.getResStringValue(jsonObject2, "locale_alias")
                    otherStringsFile.add(
                        TranslateStringsFile(
                            targetLanguage?.ifEmpty { null },
                            targetLanguageAlias,
                            stringJsonPsi,
                            jsonObject2
                        )
                    )
                }
            }
        }

        val defaultStringsFile = TranslateStringsFile(
            "zh",
            sourceLanguageAlias,
            stringJsonPsiFile,
            jsonObject,
            TranslateUtils.fixTranslateError(text, "zh", true)
        )

        changeData(
            project,
            selectedFile,
            selectedElement,
            existKey,
            text,
            keyTranslateText,
            defaultStringsFile,
            otherStringsFile,
            isFormat
        )
    }

    /// 移除字符串中的占位符
    private fun removePlaceHolder(translate: String): String {
        if (translate.isEmpty()) {
            return translate
        }

        var translateText = translate
        val placeHolders = listOf("s", "d", "f")
        placeHolders.forEach {
            val regex = Regex(TranslateUtils.getPlaceHolderRegex(it))
            translateText = translateText.replace(regex, "")
        }
        return translateText
    }

    /**
     * 执行翻译插入操作
     *
     * [existKey] 表示当前提取的多语言是否在模版arb文件中已存在
     * [originalText] 为选中的原始文本，未经任何加工
     * [keyTranslateText] 为对原始文本进行加工，用于翻译为引用字段Key的文本内容
     * [defaultStringsFile] 为模板arb文件中需要翻译的内容
     * [otherStringsFile] 为其它语言strings文件
     * [isFormat] 表示是否存在占位符，不使用templateEntryList判断，是因为java语言没有此内容，此内容仅Kotlin文本存在
     */
    private fun changeData(
        project: Project,
        selectedFile: PsiFile,
        selectedElement: PsiElement,
        existKey: String?,
        originalText: String,
        keyTranslateText: String,
        defaultStringsFile: TranslateStringsFile,
        otherStringsFile: List<TranslateStringsFile>,
        isFormat: Boolean
    ) {

        if (existKey != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                replaceElement(selectedFile, selectedElement, existKey)
            }
        } else {
            ProgressUtils.runBackground(project, "Translate", true) { progressIndicator ->
                progressIndicator.isIndeterminate = false
                val totalCount = 1.0 + otherStringsFile.size
                CoroutineScope(Dispatchers.Default).launch launch2@{
                    val sourceLanguage = defaultStringsFile.translateLanguage!!
                    val enTranslate =
                        TranslateUtils.translate(keyTranslateText, sourceLanguage, "en")
                    val key = TranslateUtils.mapStrToKey(enTranslate, isFormat)
                    if (progressIndicator.isCanceled) {
                        return@launch2
                    }

                    var current = 1.0
                    progressIndicator.fraction = current / totalCount * 0.95
                    otherStringsFile.forEach { file ->
                        val translateLanguage = file.translateLanguage
                        if (translateLanguage == "en" && !isFormat) {
                            file.translate = TranslateUtils.fixTranslateError(enTranslate, "en")
                        } else if (!translateLanguage.isNullOrEmpty()) {
                            file.translate =
                                TranslateUtils.translate(originalText, sourceLanguage, translateLanguage)
                            file.translate =
                                TranslateUtils.fixTranslateError(file.translate, translateLanguage)
                        }

                        if (progressIndicator.isCanceled) {
                            return@launch2
                        }

                        current++
                        progressIndicator.fraction = current / totalCount * 0.95
                    }

                    if (progressIndicator.isCanceled) {
                        return@launch2
                    }

                    CoroutineScope(Dispatchers.EDT).launch {
                        var showRename = false
                        if (key == null || PluginSettings.getExtractStr2L10nShowRenameDialog(project)) {
                            showRename = true
                        }

                        if (progressIndicator.isCanceled) {
                            return@launch
                        }

                        if (showRename) {
                            progressIndicator.fraction = 1.0
                            val newKey =
                                renameKey(project, key, defaultStringsFile, otherStringsFile)
                                    ?: return@launch

                            insertElement(
                                project,
                                progressIndicator,
                                selectedFile,
                                selectedElement,
                                defaultStringsFile,
                                newKey,
                                otherStringsFile,
                            )
                        } else {
                            insertElement(
                                project,
                                progressIndicator,
                                selectedFile,
                                selectedElement,
                                defaultStringsFile,
                                key!!,
                                otherStringsFile,
                            )
                        }
                    }
                }
            }
        }
    }

    // 将翻译后的内容插入arb文件
    private fun insertElement(
        project: Project,
        progressIndicator: ProgressIndicator,
        selectedFile: PsiFile,
        selectedElement: PsiElement,
        defaultStringsFile: TranslateStringsFile,
        newKey: String,
        otherStringsFile: List<TranslateStringsFile>,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            ResJsonUtils.insertElement(
                project,
                defaultStringsFile.stringsFile,
                defaultStringsFile.jsonObject, newKey,
                defaultStringsFile.translate ?: "",
            )

            replaceElement(
                selectedFile,
                selectedElement,
                newKey
            )

            var existFailed = false
            otherStringsFile.forEach { file ->
                val tl = file.translate
                if (!tl.isNullOrEmpty()) {
                    ResJsonUtils.insertElement(
                        project,
                        file.stringsFile,
                        file.jsonObject,
                        newKey,
                        tl,
                    )
                } else if (file.translateLanguage.isNullOrEmpty()) {
                    existFailed = true
                }
            }

            progressIndicator.fraction = 1.0
            if (existFailed) {
                NotificationUtils.showBalloonMsg(
                    project,
                    "存在部分string.json所属文件夹名未指定语言或string.json中未指定locale_alias属性，此文件的翻译已忽略",
                    NotificationType.WARNING
                )
            }
        }
    }

    // 重命名多语言在strings.xml文件中的key
    private fun renameKey(
        project: Project,
        key: String?,
        defaultStringsFile: TranslateStringsFile,
        otherStringsFile: List<TranslateStringsFile>
    ): String? {
        val dialog = InputKeyDialog(project, key, defaultStringsFile, otherStringsFile)
        dialog.show()
        if (dialog.exitCode != DialogWrapper.OK_EXIT_CODE) {
            return null
        }

        return dialog.getValue()
    }

    private fun replaceElement(
        selectedFile: PsiFile,
        selectedElement: PsiElement,
        key: String
    ) {
        val content = "\$r('app.string.$key')"
        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(selectedFile.virtualFile)
        document?.replaceString(selectedElement.startOffset, selectedElement.endOffset, content)
    }
}

private fun isExistKey(jsonObject: JsonObject?, key: String): Boolean {
    return ResJsonUtils.isExistKeyByResStringValue(jsonObject, key) != null
}

class InputKeyDialog(
    val project: Project,
    private var defaultValue: String?,
    private val defaultStringsFile: TranslateStringsFile,
    private val otherStringsFile: List<TranslateStringsFile>,
) : DialogWrapper(project, false) {

    private val rootPanel: JComponent
    private var contentTextField: JBTextArea? = null
    private var existKey: Boolean = false

    init {
        rootPanel = createRootPanel()
        init()
    }

    override fun createCenterPanel(): JComponent = rootPanel

    override fun getPreferredFocusedComponent(): JComponent? {
        return contentTextField
    }

    private fun createRootPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        builder.addComponent(JLabel("输入多语言key："))

        val existKeyHint = JLabel("已存在相同key")
        existKeyHint.foreground = JBColor.RED
        existKeyHint.font = UIUtil.getFont(UIUtil.FontSize.SMALL, existKeyHint.font)
        existKey =
            if (defaultValue.isNullOrEmpty()) false else isExistKey(defaultStringsFile.jsonObject, defaultValue!!)
        existKeyHint.isVisible = existKey

        val content = JBTextArea()
        content.text = defaultValue
        content.minimumSize = Dimension(300, 40)
        content.lineWrap = true
        content.wrapStyleWord = true
        contentTextField = content

        val jsp = JBScrollPane(content)
        jsp.minimumSize = Dimension(300, 40)
        jsp.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        )

        content.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                jsp.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
                )
                if (this@InputKeyDialog.defaultValue == null) {
                    contentTextField?.also {
                        Toast.show(it, MessageType.WARNING, "翻译失败，请输入多语言key")
                    }
                    this@InputKeyDialog.defaultValue = ""
                }
            }

            override fun focusLost(p0: FocusEvent?) {
                jsp.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
                )
            }
        })

        content.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(defaultStringsFile.jsonObject, str)
                existKeyHint.isVisible = existKey
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(defaultStringsFile.jsonObject, str)
                existKeyHint.isVisible = existKey
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(defaultStringsFile.jsonObject, str)
                existKeyHint.isVisible = existKey
            }
        })

        content.addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent?) {

            }

            override fun keyPressed(e: KeyEvent?) {

            }

            override fun keyReleased(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                    val text = content.text
                    content.text = text.replace("\n", "").replace("\r", "")
                    doOKAction()
                }
            }
        })

        builder.addComponent(jsp)
        builder.addComponent(existKeyHint)

        val stringsFiles: MutableList<TranslateStringsFile> = mutableListOf(defaultStringsFile)
        stringsFiles.addAll(otherStringsFile)
        val label = JLabel("以下为翻译内容：")
        label.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
        builder.addComponent(label)
        stringsFiles.forEach {
            val box = Box.createHorizontalBox()
            box.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)

            var title = it.targetLanguage
            if (!it.targetLanguageAlias.isNullOrEmpty()) {
                title += "(${it.targetLanguageAlias})"
            }
            title += "："

            val label2 = JBTextArea(title)
            label2.isEditable = false // 如果不需要编辑功能
            label2.lineWrap = true // 启用自动换行
            label2.wrapStyleWord = true // 确保单词不会被拆分到两行
            label2.border = BorderFactory.createEmptyBorder()
            label2.background = Color(0, 0, 0, 0)
            label2.preferredSize = Dimension(40, 60)
            label2.maximumSize = Dimension(80, 60)
            label2.minimumSize = Dimension(40, 60)
            box.add(label2)

            val content =
                if (it.translateLanguage.isNullOrEmpty()) "${it.stringsFile.name}所属文件夹名未指定语言或${it.stringsFile.name}中未指定locale_alias属性，无法翻译，请配置属性后重试" else it.translate
            val textArea = JBTextArea(content)
            textArea.minimumSize = Dimension(260, 60)
            textArea.lineWrap = true
            textArea.wrapStyleWord = true
            if (it.translateLanguage.isNullOrEmpty()) {
                textArea.isEditable = false
                textArea.foreground = JBColor.RED
            }

            val jsp2 = JBScrollPane(textArea)
            jsp2.minimumSize = Dimension(260, 60)
            box.add(jsp2)

            textArea.addFocusListener(object : FocusListener {
                override fun focusGained(p0: FocusEvent?) {
                    jsp2.border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)
                    )
                }

                override fun focusLost(p0: FocusEvent?) {
                    jsp2.border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
                        BorderFactory.createEmptyBorder(2, 2, 2, 2)
                    )
                }
            })

            textArea.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(p0: DocumentEvent?) {
                    val str = textArea.text.trim()
                    it.translate = str
                }

                override fun removeUpdate(p0: DocumentEvent?) {
                    val str = textArea.text.trim()
                    it.translate = str
                }

                override fun changedUpdate(p0: DocumentEvent?) {
                    val str = textArea.text.trim()
                    it.translate = str
                }
            })

            builder.addComponent(box)
        }

        val rootPanel: JPanel = if (stringsFiles.size > 5) {
            builder.addComponentFillVertically(JPanel(), 0).panel
        } else {
            builder.panel
        }

        val jb = JBScrollPane(rootPanel)
        jb.preferredSize = JBUI.size(300, 40 + 60 * (stringsFiles.size).coerceAtMost(5))
        jb.border = BorderFactory.createEmptyBorder()
        return jb
    }

    fun getValue(): String {
        return contentTextField?.text ?: ""
    }

    override fun doOKAction() {
        val value = getValue()
        if (value.isEmpty()) {
            contentTextField?.also {
                Toast.show(it, MessageType.WARNING, "请输入多语言key")
            }
            return
        }

        if (existKey) {
            contentTextField?.also {
                Toast.show(it, MessageType.WARNING, "已存在相同的key")
            }
            return
        }

        super.doOKAction()
    }
}

// 需要翻译的strings.xml数据
data class TranslateStringsFile(
    val targetLanguage: String?,
    val targetLanguageAlias: String?,
    val stringsFile: PsiFile,
    val jsonObject: JsonObject?,
    var translate: String? = null
) {
    /// 用于翻译的目标语言
    val translateLanguage
        get() = targetLanguageAlias ?: targetLanguage
}

