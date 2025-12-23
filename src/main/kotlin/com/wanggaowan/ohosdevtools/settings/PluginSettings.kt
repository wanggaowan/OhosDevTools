package com.wanggaowan.ohosdevtools.settings

import com.intellij.openapi.project.Project
import com.wanggaowan.ohosdevtools.utils.PropertiesSerializeUtils

/**
 * 插件设置
 *
 * @author Created by wanggaowan on 2025/12/22 14:51
 */
object PluginSettings {
    private const val EXTRACT_STR_2_L10N_SHOW_RENAME_DIALOG = "extractStr2L10nShowRenameDialog"

    private fun formatPath(path: String): String {
        var mapPath = path
        if (mapPath.startsWith("/")) {
            mapPath = mapPath.substring(1)
        }
        if (mapPath.endsWith("/")) {
            mapPath = mapPath.dropLast(1)
        }
        return mapPath
    }

    fun getExtractStr2L10nShowRenameDialog(project: Project? = null): Boolean {
        return getValue(project, EXTRACT_STR_2_L10N_SHOW_RENAME_DIALOG, "1") == "1"
    }

    fun setExtractStr2L10nShowRenameDialog(project: Project? = null, value: Boolean) {
        setValue(project, EXTRACT_STR_2_L10N_SHOW_RENAME_DIALOG, if (value) "1" else "0")
    }

    private fun getValue(project: Project?, key: String, defValue: String): String {
        return if (project == null) {
            var dir = PropertiesSerializeUtils.getString(key, defValue)
            if (dir.isEmpty()) {
                dir = defValue
            }
            dir
        } else {
            var dir = PropertiesSerializeUtils.getString(project, key)
            if (dir.isEmpty()) {
                dir = PropertiesSerializeUtils.getString(key, defValue)
                if (dir.isEmpty()) {
                    dir = defValue
                }
            }
            dir
        }
    }

    private fun setValue(project: Project? = null, key: String, value: String) {
        if (project == null) {
            PropertiesSerializeUtils.putString(key, value)
        } else {
            PropertiesSerializeUtils.putString(project, key, value)
        }
    }
}
