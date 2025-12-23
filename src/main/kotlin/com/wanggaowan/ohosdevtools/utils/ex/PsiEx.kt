package com.wanggaowan.ohosdevtools.utils.ex

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/*
 * Psi相关扩展
 *
 * @author Created by wanggaowan on 2025/12/22 09:59
 */

/**
 * VirtualFile 转化为 PsiFile
 */
fun VirtualFile.toPsiFile(project: Project): PsiFile? {
    return PsiManager.getInstance(project).findFile(this)
}

inline fun <reified T : PsiElement> PsiElement.getChildOfType():T? {
    val children = this.children
    for (child in children) {
        if(child is T) {
            return child
        }
    }

    return null
}
