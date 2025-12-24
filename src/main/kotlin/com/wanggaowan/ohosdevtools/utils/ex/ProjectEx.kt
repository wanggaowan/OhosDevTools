package com.wanggaowan.ohosdevtools.utils.ex

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem

/*
 * Project扩展
 */

// <editor-fold desc="project">

fun Project?.getModules(): Array<Module>? {
    if (this == null) {
        return null
    }
    return ModuleManager.getInstance(this).modules
}


/**
 * 是否是Ohos项目
 */
val Project?.isOhosProject: Boolean
    get() {
        if (this == null) {
            return false
        }

        val isFP = isOhosProjectInner(this.basePath)
        if (isFP) {
            return true
        }

        val modules = getModules()
        if (modules.isNullOrEmpty()) {
            return false
        }

        for (module in modules) {
            if (module.isOhosProject) {
                return true
            }
        }
        return false
    }

fun isOhosProjectInner(basePath: String?): Boolean {
    if (basePath.isNullOrEmpty()) {
        return false
    }

    val file = VirtualFileManager.getInstance().findFileByUrl("file://$basePath/oh-package.json5")
    return file != null
}

/**
 * [Project]根目录
 */
val Project.rootDir: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByUrl("file://${this.basePath}")

/**
 * 获取项目根目录下的指定[name]文件
 */
fun Project.findChild(name: String) = rootDir?.findChild(name)

val Project.ohosModules: List<Module>?
    get() {
        val modules = getModules()
        if (modules.isNullOrEmpty()) {
            return null
        }
        val list = mutableListOf<Module>()
        for (module in modules) {
            if (module.isOhosProject) {
                list.add(module)
            }
        }
        return list
    }

/**
 * 项目资源根目录
 */
val Project.resRootDir: VirtualFile?
    get() {
        return VirtualFileManager.getInstance()
            .findFileByUrl("file://${basePath}/AppScope/resources")
    }

// </editor-fold>


// <editor-fold desc="Module">
/**
 * 是否是鸿蒙项目
 */
val Module?.isOhosProject: Boolean
    get() {
        if (this == null) {
            return false
        }

        val files = ModuleRootManager.getInstance(this).contentRoots
        if (files.isEmpty()) {
            return false
        }
        return isOhosProjectInner(files[0].path)
    }

val Module?.rootDir: VirtualFile?
    get() {
        if (this == null) {
            return null
        }

        val files = ModuleRootManager.getInstance(this).contentRoots
        return if (files.isEmpty()) null else files[0]
    }

val Module?.basePath: String?
    get() = rootDir?.path

/**
 * 获取模块根目录下的指定[name]文件
 */
fun Module.findChild(name: String) = rootDir?.findChild(name)

/**
 * 资源根目录路径
 */
val Module.resRootPath: String
    get() {
        return "${basePath}/src/main/resources"
    }

/**
 * 资源根目录
 */
val Module.resRootDir: VirtualFile?
    get() {
        return VirtualFileManager.getInstance()
            .findFileByUrl("file://${resRootPath}")
    }

// </editor-fold>


// <editor-fold desc="VirtualFile">

/**
 * 通过文件获取所属的模块
 */
val VirtualFile.module: Module?
    get() {
        // ModuleUtilCore.findModuleForFile()
        var inModule: Module? = null
        var inModuleBasePath: String? = null
        for (project in ProjectManager.getInstance().openProjects) {
            val modules = project.getModules()
            if (modules.isNullOrEmpty()) {
                continue
            }

            for (module in modules) {
                val basePath = module.basePath
                if (basePath != null && path.startsWith(basePath)) {
                    // 此处不能找到第一个就返回，因为可能path路径上存在多个module，
                    // 需要匹配最长路径的module
                    if (inModuleBasePath == null) {
                        inModule = module
                        inModuleBasePath = basePath
                    } else if (inModuleBasePath.length < basePath.length) {
                        inModule = module
                        inModuleBasePath = basePath
                    }
                }
            }
        }
        return inModule
    }

// </editor-fold>

// <editor-fold desc="AnActionEvent">

val AnActionEvent?.isOhosProject: Boolean
    get() {
        if (this == null) {
            return false
        }

        val module = getData(LangDataKeys.MODULE)
        return module.isOhosProject
    }

// </editor-fold>


fun PsiElement?.findModule(): Module? {
    if (this == null) {
        return null
    }

    val file = containingFile
    if (file != null) {
        return file.virtualFile?.module
    }

    if (this is PsiFileSystemItem) {
        return virtualFile?.module
    }

    return null
}

