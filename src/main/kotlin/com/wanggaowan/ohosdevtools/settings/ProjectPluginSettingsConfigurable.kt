package com.wanggaowan.ohosdevtools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.wanggaowan.ohosdevtools.utils.ex.isOhosProject
import javax.swing.JComponent

/**
 * 项目插件设置界面配置
 *
 * @author Created by wanggaowan on 2025/12/22 14:51
 */
class ProjectPluginSettingsConfigurable(val project: Project) : Configurable {
    private var mSettingsView: PluginSettingsView? = null

    override fun getDisplayName(): String {
        return "OhosDevTools"
    }

    override fun createComponent(): JComponent {
        mSettingsView = PluginSettingsView()
        return mSettingsView!!.panel
    }

    override fun isModified(): Boolean {
        return isExtractStr2L10nModified()
    }

    private fun isExtractStr2L10nModified(): Boolean {
        if (PluginSettings.getAliAk() != mSettingsView?.aliAk?.text) {
            return true
        }

        if (PluginSettings.getAliSk() != mSettingsView?.aliSk?.text) {
            return true
        }

        if (PluginSettings.getExtractStr2L10nShowRenameDialog(getProjectWrapper()) != mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected) {
            return true
        }

        return false
    }

    override fun apply() {
        applyExtractStr2L10n()
    }

    private fun applyExtractStr2L10n() {
        PluginSettings.setAliAk(mSettingsView?.aliAk?.text ?: "")

        PluginSettings.setAliSk(mSettingsView?.aliSk?.text ?: "")

        PluginSettings.setExtractStr2L10nShowRenameDialog(
            getProjectWrapper(),
            mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected != false
        )
    }

    override fun reset() {
        resetExtractStr2L10n()
    }

    private fun resetExtractStr2L10n() {
        mSettingsView?.aliAk?.text = PluginSettings.getAliAk()
        mSettingsView?.aliSk?.text = PluginSettings.getAliSk()

        mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected =
            PluginSettings.getExtractStr2L10nShowRenameDialog(getProjectWrapper())
    }

    override fun disposeUIResources() {
        mSettingsView = null
    }

    private fun getProjectWrapper(): Project? {
        if (project.isDefault || !project.isOhosProject) {
            return null
        }
        return project
    }
}
