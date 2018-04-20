/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.java.FirJavaModuleBasedSession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.FirProvider
import org.jetbrains.kotlin.fir.resolve.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.resolve.transformers.FirTotalResolveTransformer
import org.jetbrains.kotlin.fir.service
import org.jetbrains.kotlin.idea.actions.internal.IdeFirDependenciesSymbolProvider
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.system.measureNanoTime

class AllKotlinFirResolveTest : AllKotlinTest() {

    override fun setUpModule() {
        super.setUpModule()

        ModuleRootModificationUtil.addContentRoot(module, VfsUtil.findFileByIoFile(rootProjectFile, true)!!)

        ModuleRootModificationUtil.updateModel(module) {
            rootProjectFile.walkTopDown().onEnter {
                it.name.toLowerCase() !in setOf("testdata", "resources")
            }.filter {
                it.isDirectory
            }.forEach { dir ->
                val vdir by lazy { VfsUtil.findFileByIoFile(dir, true)!! }
                if (dir.name in setOf("src", "test", "tests")) {
                    it.contentEntries.single().addSourceFolder(vdir, false)
                } else if (dir.name in setOf("build")) {
                    it.contentEntries.single().addExcludeFolder(vdir)
                }
            }
        }
    }

    private fun createSession(): FirSession {
        val moduleInfo = module.productionSourceInfo()!!
        val sessionProvider = FirProjectSessionProvider(project)

        return FirJavaModuleBasedSession(
            moduleInfo, sessionProvider, moduleInfo.contentScope(),
            IdeFirDependenciesSymbolProvider(moduleInfo, project, sessionProvider)
        )
    }

    override fun doTest(file: File): PerFileTestResult {
        val results = mutableMapOf<String, Long>()
        var totalNs = 0L

        val psiFile = file.toPsiFile() ?: run {
            return PerFileTestResult(results, totalNs, listOf(AssertionError("PsiFile not found for $file")))
        }

        val errors = mutableListOf<Throwable>()

        val session = createSession()
        val builder = RawFirBuilder(session)
        var firFile: FirFile? = null
        val rawResult = measureNanoTime {
            psiFile as? KtFile ?: return@measureNanoTime
            try {
                firFile = builder.buildFirFile(psiFile)
                (session.service<FirProvider>() as FirProviderImpl).recordFile(firFile!!)
            } catch (t: Throwable) {
                t.printStackTrace()
                errors += t
            }
        }
        results["FIR_RawBuild"] = rawResult
        totalNs += rawResult

        if (firFile != null) {
            val transformers = FirTotalResolveTransformer().transformers
            for (transformer in transformers) {
                val resolveResult = measureNanoTime {
                    try {
                        transformer.transformFile(firFile!!, null)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        errors += t
                    }
                }
                results["FIR_Transformer_${transformer::class.java}"] = resolveResult
                totalNs += resolveResult
            }
        }

        return PerFileTestResult(results, totalNs, errors)
    }
}