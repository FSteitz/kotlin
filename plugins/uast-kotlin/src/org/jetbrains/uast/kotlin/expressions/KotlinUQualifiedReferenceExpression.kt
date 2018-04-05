/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastQualifiedExpressionAccessType

class KotlinUQualifiedReferenceExpression(
        override val psi: KtDotQualifiedExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression,
        KotlinUElementWithType, KotlinEvaluatableUElement {
    override val receiver: UExpression by lz { KotlinConverter.convertOrEmpty(psi.receiverExpression, this) }
    override val selector: UExpression by lz { KotlinConverter.convertOrEmpty(psi.selectorExpression, this) }
    override val accessType: UastQualifiedExpressionAccessType = UastQualifiedExpressionAccessType.SIMPLE
    
    override fun resolve(): PsiElement? = psi.selectorExpression?.resolveCallToDeclaration(this)

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name
}

class KotlinUComponentQualifiedReferenceExpression(
        override val psi: KtDestructuringDeclarationEntry,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression,
        KotlinUElementWithType, KotlinEvaluatableUElement {
    override val accessType: UastQualifiedExpressionAccessType = UastQualifiedExpressionAccessType.SIMPLE
    
    override lateinit var receiver: UExpression
        internal set

    override lateinit var selector: UExpression
        internal set

    override val resolvedName: String?
        get() = psi.analyze()[BindingContext.COMPONENT_RESOLVED_CALL, psi]?.resultingDescriptor?.name?.asString()
    
    override fun resolve(): PsiElement? {
        val bindingContext = psi.analyze()
        val descriptor = bindingContext[BindingContext.COMPONENT_RESOLVED_CALL, psi]?.resultingDescriptor ?: return null
        return descriptor.toSource()?.getMaybeLightElement(this)
    }
}