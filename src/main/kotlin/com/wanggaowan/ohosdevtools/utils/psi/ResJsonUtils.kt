package com.wanggaowan.ohosdevtools.utils.psi

import com.intellij.json.JsonFileType
import com.intellij.json.psi.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.LocalTimeCounter
import com.wanggaowan.ohosdevtools.utils.ex.getChildOfType

/**
 * 资源文件Json操作Psi对象工具
 *
 * @author Created by wanggaowan on 2025/12/22 10:51
 */
object ResJsonUtils {
    /**
     * 校验[nodeName]节点格式是否正确,仅节点存在且格式不正常时返回false
     */
    fun checkFormat(jsonObject: JsonObject?, nodeName: String): Boolean {
        val node = jsonObject?.findProperty(nodeName) ?: return true
        return node.value is JsonArray
    }

    /**
     * 获取资源文件string.json中指定value内容是否存在，存在则返回key
     */
    @Suppress("DuplicatedCode")
    fun isExistKeyByResStringValue(jsonObject: JsonObject?, value: String): String? {
        val stringArray = getResList(jsonObject, "string") ?: return null
        if (stringArray.isEmpty()) {
            return null
        }

        for (child in stringArray) {
            val keyValue = getKeyValue(child)
            if (keyValue != null && keyValue.second == value) {
                return keyValue.first
            }
        }
        return null
    }

    /**
     * 获取资源文件string.json中指定Key内容
     */
    @Suppress("DuplicatedCode")
    fun getResStringValue(jsonObject: JsonObject?, key: String): String? {
        val stringArray = getResList(jsonObject, "string") ?: return null
        if (stringArray.isEmpty()) {
            return null
        }

        for (child in stringArray) {
            val keyValue = getKeyValue(child)
            if (keyValue != null && keyValue.first == key) {
                return keyValue.second
            }
        }
        return null
    }

    /**
     * 获取资源文件中给定的JsonValue种对应的key和value
     */
    fun getKeyValue(obj: JsonValue): Pair<String?, String?>? {
        if (obj !is JsonObject) {
            return null
        }

        var key = obj.findProperty("name")?.value?.text
        if (key != null && key.length >= 2) {
            key = key.substring(1, key.length - 1)
        }

        var value = obj.findProperty("value")?.value?.text
        if (value != null && value.length >= 2) {
            value = value.substring(1, value.length - 1)
        }
        return Pair(key, value)
    }

    /**
     * 获取资源文件中[nodeName]对应的值列表
     */
    fun getResList(jsonObject: JsonObject?, nodeName: String): List<JsonValue>? {
        val stringArray = jsonObject?.findProperty(nodeName)?.value
        if (stringArray !is JsonArray) {
            return null
        }

        return stringArray.valueList
    }

    /**
     * 将值插入Json文件
     *
     * [stringFile] 需要拆入的json文件对象
     * [jsonObject] [stringFile]的顶层JsonObject对象，如果此值存在，则将新加的值插入到此对象下面，否则创建新顶层对象插入
     * [key] 插入的json数据key
     * [value] 插入的json数据value
     */
    fun insertElement(
        project: Project,
        stringFile: PsiFile,
        jsonObject: JsonObject?,
        key: String,
        value: String,
    ) {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.${JsonFileType.INSTANCE.defaultExtension}",
            JsonFileType.INSTANCE,
            "{\"string\": [{\"name\": \"$key\",\"value\": \"$value\"}]}",
            LocalTimeCounter.currentTime(),
            false
        )
        if (jsonObject != null) {
            val stringJson = jsonObject.findProperty("string")?.value
            if (stringJson != null) {
                val temp = psiFile.getChildOfType<JsonObject>()?.propertyList[0]?.value
                if (temp is JsonArray) {
                    val element = stringJson.addBefore(temp.valueList[0], stringJson.lastChild)
                    if ((stringJson as JsonArray).valueList.isNotEmpty()) {
                        stringJson.addBefore(JsonElementGenerator(project).createComma(), element)
                    }
                }
            } else {
                val temp = psiFile.getChildOfType<JsonObject>()?.findProperty("string")
                if (temp != null) {
                    JsonPsiUtil.addProperty(jsonObject, temp, false)
                }
            }
        } else {
            stringFile.add(psiFile.firstChild)
        }

        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(stringFile.virtualFile)
        if (document != null) {
            manager.saveDocument(document)
        } else {
            manager.saveAllDocuments()
        }
    }
}
