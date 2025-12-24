package com.wanggaowan.ohosdevtools.actions

import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.wanggaowan.ohosdevtools.utils.ProgressUtils
import com.wanggaowan.ohosdevtools.utils.ex.getChildOfType
import com.wanggaowan.ohosdevtools.utils.ex.isOhosProject
import com.wanggaowan.ohosdevtools.utils.ex.toPsiFile
import com.wanggaowan.ohosdevtools.utils.psi.ResJsonUtils

/**
 * 删除多个string.json文件相同key元素
 *
 * @author Created by wanggaowan on 2025/12/24 10:16
 */
class DeleteStringsSameKeyElementAction : DumbAwareAction() {
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

        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element?.parent !is JsonObject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val jsonObject = element?.parent ?: return
        if (jsonObject !is JsonObject) {
            return
        }

        val project = jsonObject.project
        ProgressUtils.runBackground(project, "delete strings same key") { indicator ->
            indicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                val results = mutableListOf<PsiElement>()
                results.add(jsonObject)
                val parent = jsonObject.parent
                if (parent is JsonArray) {
                    if (isLastChild(parent.valueList, jsonObject)) {
                        getWithElementDeleteOtherNodePrev(jsonObject, results)
                    } else {
                        getWithElementDeleteOtherNode(jsonObject, results)
                    }
                }

                val keyValue = ResJsonUtils.getKeyValue(jsonObject)
                val key = keyValue?.first
                if (key != null) {
                    val file = jsonObject.containingFile?.virtualFile
                    var parent = file?.parent?.parent
                    if (parent != null && parent.isDirectory) {
                        // base目录
                        val folderName = parent.name
                        parent = parent.parent
                        if (parent != null && parent.isDirectory) {
                            // res目录
                            getOtherArbSameElement(project, parent, folderName, key, results)
                        }
                    }
                }

                results.forEach {
                    it.delete()
                }
                FileDocumentManager.getInstance().saveAllDocuments()
                indicator.fraction = 1.0
            }
        }
    }

    private fun isLastChild(list: List<JsonValue>?, element: PsiElement): Boolean {
        if (list.isNullOrEmpty()) {
            return false
        }
        return list.last() == element
    }

    // 获取与指定element需要一起删除的其它节点，如换行，','等
    private fun getWithElementDeleteOtherNode(element: PsiElement, results: MutableList<PsiElement>) {
        val nextElement = element.nextSibling ?: return
        if (nextElement is JsonValue || !nextElement.isValid
            || nextElement.node.elementType == JsonElementTypes.R_BRACKET) {
            return
        }

        if (nextElement.node.elementType == JsonElementTypes.COMMA) {
            results.add(nextElement)
        }
        getWithElementDeleteOtherNode(nextElement, results)
    }

    // 获取与指定element需要一起删除的其它节点，如换行，','等
    private fun getWithElementDeleteOtherNodePrev(element: PsiElement, results: MutableList<PsiElement>) {
        val nextElement = element.prevSibling ?: return
        if (nextElement is JsonValue || !nextElement.isValid
            || nextElement.node.elementType == JsonElementTypes.L_BRACKET) {
            return
        }

        if (nextElement.node.elementType == JsonElementTypes.COMMA) {
            results.add(nextElement)
        }
        getWithElementDeleteOtherNodePrev(nextElement, results)
    }

    private fun getOtherArbSameElement(
        project: Project,
        parent: VirtualFile,
        currentFileFolder: String,
        key: String,
        results: MutableList<PsiElement>
    ) {
        parent.children.forEach loop1@{
            val name = it.name
            if (!it.isDirectory || name == currentFileFolder) {
                return@loop1
            }

            val stringsFile = it.findChild("element")?.findChild("string.json") ?: return@loop1
            val jsonArray =
                ResJsonUtils.getResList(stringsFile.toPsiFile(project)?.getChildOfType<JsonObject>(), "string")
                    ?: return@loop1
            jsonArray.forEach { jsonObj ->
                val keyValue = ResJsonUtils.getKeyValue(jsonObj)
                if (keyValue != null && keyValue.first == key) {
                    results.add(jsonObj)
                    if (isLastChild(jsonArray, jsonObj)) {
                        getWithElementDeleteOtherNodePrev(jsonObj, results)
                    } else {
                        getWithElementDeleteOtherNode(jsonObj, results)
                    }
                    return@loop1
                }
            }
        }
    }
}
