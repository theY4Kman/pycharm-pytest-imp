package com.y4kstudios.pytestimp.fixtures

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.references.PyReferenceCustomTargetChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixtureReference

class LambdaFixtureTargetChecker : PyReferenceCustomTargetChecker {
    override fun isReferenceTo(reference: PsiReference, to: PsiElement): Boolean {
        if (!(to is PyTargetExpression || to is PyFunction)) return false

        val module = ModuleUtilCore.findModuleForPsiElement(to) ?: return false
        if (!isPyTestEnabled(module)) return false

        val isFixture = when(to) {
            is PyTargetExpression -> to.isAnyLambdaFixture()
            is PyFunction -> to.isFixture()
            else -> false
        }
        if (!isFixture) return false

        val resolveTarget = reference.resolve()

        if (resolveTarget is PyTargetExpression) {
            if (resolveTarget.findAssignmentCall()?.isLambdaFixtureImplicitRef() != true) {
                return false
            }

            val context = TypeEvalContext.codeAnalysis(resolveTarget.project, resolveTarget.containingFile)
            return getFixtures(module, resolveTarget, context)
                .filterNot { it.resolveTarget == resolveTarget }
                .firstOrNull()?.resolveTarget == to
        }

        if (resolveTarget is PyNamedParameter) {
            val ref =
                resolveTarget.references
                    .firstOrNull { it is LambdaFixtureReference || it is PyTestFixtureReference }
                    ?: return false
            return ref.isReferenceTo(to)
        }

        return false
    }
}
