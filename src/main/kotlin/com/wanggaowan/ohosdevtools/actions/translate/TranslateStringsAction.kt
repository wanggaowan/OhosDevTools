package com.wanggaowan.ohosdevtools.actions.translate

import com.intellij.json.psi.JsonObject
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.application
import com.wanggaowan.ohosdevtools.utils.NotificationUtils
import com.wanggaowan.ohosdevtools.utils.ProgressUtils
import com.wanggaowan.ohosdevtools.utils.TranslateUtils
import com.wanggaowan.ohosdevtools.utils.ex.getChildOfType
import com.wanggaowan.ohosdevtools.utils.ex.isOhosProject
import com.wanggaowan.ohosdevtools.utils.ex.toPsiFile
import com.wanggaowan.ohosdevtools.utils.psi.ResJsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 翻译strings文件
 *
 * @author Created by wanggaowan on 2024/1/4 17:04
 */
class TranslateStringsAction : DumbAwareAction() {

    private var templateFile: VirtualFile? = null
    private var file: VirtualFile? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        file = null
        templateFile = null
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        if (!project.isOhosProject) {
            e.presentation.isVisible = false
            return
        }

        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || file.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (file.name != "string.json") {
            e.presentation.isVisible = false
            return
        }

        var parent: VirtualFile? = file.parent
        if (parent?.name != "element") {
            e.presentation.isVisible = false
            return
        }

        parent = parent.parent.parent
        if (parent?.name != "resources") {
            e.presentation.isVisible = false
            return
        }

        val valuesDir = parent.findChild("base")
        if (valuesDir != null && valuesDir.isDirectory) {
            val elementDir = valuesDir.findChild("element")
            if (elementDir != null && elementDir.isDirectory) {
                val strings = elementDir.findChild("string.json")
                if (strings != null && !strings.isDirectory) {
                    this.templateFile = strings
                }
            }
        }

        this.file = file
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (file == null) {
            return
        }

        val project = event.project ?: return
        if (templateFile == null) {
            NotificationUtils.showBalloonMsg(
                project,
                "请提供模版文件base/element/string.json",
                NotificationType.WARNING
            )
            return
        }

        val tempStringsPsiFile = templateFile!!.toPsiFile(project) ?: return
        val stringsPsiFile = file!!.toPsiFile(project) ?: return
        val tempJsonObject = tempStringsPsiFile.getChildOfType<JsonObject>()
        if (!ResJsonUtils.checkFormat(tempJsonObject, "string")) {
            NotificationUtils.showBalloonMsg(
                project,
                "模版文件json数据string节点格式错误",
                NotificationType.ERROR
            )
            return
        }

        val tempJsonArray = ResJsonUtils.getResList(tempJsonObject, "string")
        if (tempJsonArray.isNullOrEmpty()) {
            return
        }

        var sourceLanguage: String =
            ResJsonUtils.getResStringValue(tempJsonObject, "locale_alias") ?: "zh"
        if (sourceLanguage.isEmpty()) {
            sourceLanguage = "zh"
        }

        val jsonObject = stringsPsiFile.getChildOfType<JsonObject>()
        var targetLanguage = ResJsonUtils.getResStringValue(jsonObject, "locale_alias")
        if (targetLanguage.isNullOrEmpty()) {
            targetLanguage = TranslateUtils.getLanguageByDirName(file!!.parent?.parent?.name ?: "")
        }
        if (targetLanguage.isNullOrEmpty()) {
            NotificationUtils.showBalloonMsg(
                project,
                "${file!!.name}所属文件夹名未指定语言或${file!!.name}中未指定locale_alias属性，无法翻译，请配置属性后重试",
                NotificationType.WARNING
            )
            return
        }

        val propertyList = ResJsonUtils.getResList(jsonObject, "string") ?: listOf()
        ProgressUtils.runBackground(project, "Translate ${file!!.name}", true) { progressIndicator ->
            progressIndicator.isIndeterminate = false
            val needTranslateMap = mutableMapOf<String, String?>()
            application.invokeAndWait {
                tempJsonArray.forEach {
                    val keyValue = ResJsonUtils.getKeyValue(it) ?: return@forEach
                    val key = keyValue.first ?: return@forEach
                    val find = propertyList.find { tag ->
                        val key2 = ResJsonUtils.getKeyValue(tag)?.first
                        key2 == key
                    }

                    if (find == null) {
                        needTranslateMap[key] = keyValue.second
                    }
                }
            }

            if (needTranslateMap.isEmpty()) {
                progressIndicator.fraction = 1.0
                return@runBackground
            }

            progressIndicator.fraction = 0.05
            var existTranslateFailed = false
            CoroutineScope(Dispatchers.Default).launch launch2@{
                var count = 1.0
                val total = needTranslateMap.size
                needTranslateMap.forEach { (key, value) ->
                    if (progressIndicator.isCanceled) {
                        return@launch2
                    }

                    progressIndicator.text = "${count.toInt()} / $total Translating: $key"

                    var translateStr =
                        if (value.isNullOrEmpty()) value else TranslateUtils.translate(
                            value,
                            sourceLanguage,
                            targetLanguage
                        )
                    progressIndicator.fraction = count / total * 0.94 + 0.05
                    if (translateStr == null) {
                        existTranslateFailed = true
                    } else {
                        // 默认字符里面含有占位符
                        translateStr =
                            TranslateUtils.fixTranslateError(translateStr, targetLanguage)
                        if (translateStr != null) {
                            writeResult(project, stringsPsiFile, jsonObject, key, translateStr)
                        } else {
                            existTranslateFailed = true
                        }
                    }
                    count++
                }
                progressIndicator.fraction = 1.0
                if (existTranslateFailed) {
                    NotificationUtils.showBalloonMsg(
                        project,
                        "部分内容未翻译或插入成功，请重试",
                        NotificationType.WARNING
                    )
                }
            }
        }
    }

    private fun writeResult(
        project: Project,
        stringsPsiFile: PsiFile,
        jsonObject: JsonObject?,
        key: String,
        value: String
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = stringsPsiFile.viewProvider.document
            if (document != null) {
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
            ResJsonUtils.insertElement(project, stringsPsiFile, jsonObject, key, value)
            if (document != null) {
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } else {
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
        }
    }
}
