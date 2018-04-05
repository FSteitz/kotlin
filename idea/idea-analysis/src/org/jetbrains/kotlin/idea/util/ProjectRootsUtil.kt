/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.util

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndex
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.kotlin.idea.KotlinModuleFileType
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesManager
import org.jetbrains.kotlin.idea.decompiler.builtIns.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.decompiler.js.KotlinJavaScriptMetaFileType
import org.jetbrains.kotlin.idea.util.application.runReadAction

abstract class KotlinBinaryExtension(val fileType: FileType) {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinBinaryExtension> =
                ExtensionPointName.create<KotlinBinaryExtension>("org.jetbrains.kotlin.binaryExtension")

        val kotlinBinaries: List<FileType> by lazy(LazyThreadSafetyMode.NONE) {
            EP_NAME.extensions.map { it.fileType }
        }
    }
}

class JavaClassBinary: KotlinBinaryExtension(JavaClassFileType.INSTANCE)
class KotlinBuiltInBinary: KotlinBinaryExtension(KotlinBuiltInFileType)
class KotlinModuleBinary: KotlinBinaryExtension(KotlinModuleFileType.INSTANCE)
class KotlinJsMetaBinary: KotlinBinaryExtension(KotlinJavaScriptMetaFileType)

fun FileType.isKotlinBinary(): Boolean = this in KotlinBinaryExtension.kotlinBinaries

fun FileIndex.isInSourceContentWithoutInjected(file: VirtualFile): Boolean {
    return file !is VirtualFileWindow && isInSourceContent(file)
}

fun VirtualFile.getSourceRoot(project: Project): VirtualFile? = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(this)

val PsiFileSystemItem.sourceRoot: VirtualFile?
    get() = virtualFile?.getSourceRoot(project)

object ProjectRootsUtil {
    @JvmStatic fun isInContent(project: Project, file: VirtualFile, includeProjectSource: Boolean,
                               includeLibrarySource: Boolean, includeLibraryClasses: Boolean,
                               includeScriptDependencies: Boolean,
                               fileIndex: ProjectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)): Boolean {

        if (includeProjectSource && fileIndex.isInSourceContentWithoutInjected(file)) return true

        if (!includeLibraryClasses && !includeLibrarySource) return false

        // NOTE: the following is a workaround for cases when class files are under library source roots and source files are under class roots
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.name)
        val canContainClassFiles = fileType == ArchiveFileType.INSTANCE || file.isDirectory
        val isBinary = fileType.isKotlinBinary()

        val scriptConfigurationManager = if (includeScriptDependencies) ScriptDependenciesManager.getInstance(project) else null

        if (includeLibraryClasses && (isBinary || canContainClassFiles)) {
            if (fileIndex.isInLibraryClasses(file)) return true
            if (scriptConfigurationManager?.getAllScriptsClasspathScope()?.contains(file) == true) return true
        }
        if (includeLibrarySource && !isBinary) {
            if (fileIndex.isInLibrarySource(file)) return true
            if (scriptConfigurationManager?.getAllLibrarySourcesScope()?.contains(file) == true) return true
        }

        return false
    }

    @JvmStatic fun isInContent(
            element: PsiElement,
            includeProjectSource: Boolean,
            includeLibrarySource: Boolean,
            includeLibraryClasses: Boolean,
            includeScriptDependencies: Boolean
    ): Boolean {
        return runReadAction {
            val virtualFile = when (element) {
                                  is PsiDirectory -> element.virtualFile
                                  else -> element.containingFile?.virtualFile
                              } ?: return@runReadAction false

            val project = element.project
            return@runReadAction isInContent(project, virtualFile, includeProjectSource, includeLibrarySource, includeLibraryClasses, includeScriptDependencies)
        }
    }

    @JvmStatic fun isInProjectSource(element: PsiElement): Boolean {
        return isInContent(element, includeProjectSource = true, includeLibrarySource = false, includeLibraryClasses = false, includeScriptDependencies = false)
    }

    @JvmStatic fun isProjectSourceFile(project: Project, file: VirtualFile): Boolean {
        return isInContent(project, file, includeProjectSource = true, includeLibrarySource = false, includeLibraryClasses = false, includeScriptDependencies = false)
    }

    @JvmStatic fun isInProjectOrLibSource(element: PsiElement): Boolean {
        return isInContent(element, includeProjectSource = true, includeLibrarySource = true, includeLibraryClasses = false, includeScriptDependencies = false)
    }

    @JvmStatic fun isInProjectOrLibraryContent(element: PsiElement): Boolean {
        return isInContent(element, includeProjectSource = true, includeLibrarySource = true, includeLibraryClasses = true, includeScriptDependencies = true)
    }

    @JvmStatic fun isInProjectOrLibraryClassFile(element: PsiElement): Boolean {
        return isInContent(element, includeProjectSource = true, includeLibrarySource = false, includeLibraryClasses = true, includeScriptDependencies = false)
    }

    @JvmStatic fun isLibraryClassFile(project: Project, file: VirtualFile): Boolean {
        return isInContent(project, file, includeProjectSource = false, includeLibrarySource = false, includeLibraryClasses = true, includeScriptDependencies = true)
    }

    @JvmStatic fun isLibrarySourceFile(project: Project, file: VirtualFile): Boolean {
        return isInContent(project, file, includeProjectSource = false, includeLibrarySource = true, includeLibraryClasses = false, includeScriptDependencies = true)
    }

    @JvmStatic fun isLibraryFile(project: Project, file: VirtualFile): Boolean {
        return isInContent(project, file, includeProjectSource = false, includeLibrarySource = true, includeLibraryClasses = true, includeScriptDependencies = true)
    }
}

val Module.rootManager: ModuleRootManager
    get() = ModuleRootManager.getInstance(this)

val Module.sourceRoots: Array<VirtualFile>
    get() = rootManager.sourceRoots

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)

fun VirtualFile.findModule(project: Project): Module? = ModuleUtilCore.findModuleForFile(this, project)
