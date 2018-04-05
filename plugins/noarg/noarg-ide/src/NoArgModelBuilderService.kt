/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.noarg.ide

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.annotation.plugin.ide.AnnotationBasedPluginModel
import org.jetbrains.kotlin.annotation.plugin.ide.AnnotationBasedPluginModelBuilderService
import org.jetbrains.kotlin.annotation.plugin.ide.AnnotationBasedPluginProjectResolverExtension

interface NoArgModel : AnnotationBasedPluginModel {
    val invokeInitializers: Boolean
}

class NoArgModelImpl(
        override val annotations: List<String>,
        override val presets: List<String>,
        override val invokeInitializers: Boolean
) : NoArgModel

class NoArgModelBuilderService : AnnotationBasedPluginModelBuilderService<NoArgModel>() {
    override val gradlePluginNames: List<String> get() = listOf("org.jetbrains.kotlin.plugin.noarg", "kotlin-noarg")
    override val extensionName: String get() = "noArg"
    override val modelClass: Class<NoArgModel> get() = NoArgModel::class.java

    override fun createModel(annotations: List<String>, presets: List<String>, extension: Any?): NoArgModel {
        val invokeInitializers = extension?.getFieldValue("invokeInitializers") as? Boolean ?: false
        return NoArgModelImpl(annotations, presets, invokeInitializers)
    }
}

class NoArgProjectResolverExtension : AnnotationBasedPluginProjectResolverExtension<NoArgModel>() {
    companion object {
        val KEY: Key<NoArgModel> = Key("NoArgModel")
    }

    override val modelClass: Class<NoArgModel> get() = NoArgModel::class.java
    override val userDataKey: Key<NoArgModel> get() = KEY
}