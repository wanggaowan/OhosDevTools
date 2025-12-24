package com.wanggaowan.ohosdevtools.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.wanggaowan.ohosdevtools.utils.ex.isOhosProject

/**
 * 工具栏Action分组
 *
 * @author Created by wanggaowan on 2025/12/24 14:01
 */
class ToolsActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.getData(LangDataKeys.PROJECT)
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        if (!project.isOhosProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }
}
