package com.y4kstudios.pytestimp.fixtures

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.y4kstudios.pytestimp.fixtures.isAnyLambdaFixture

object LambdaFixtureInspectionExtension : PyInspectionExtension() {
    override fun ignoreShadowed(element: PsiElement) =
        when (element) {
            is PyTargetExpression -> element.isAnyLambdaFixture()
            is PyFunction -> element.isFixture()
            else -> false
        }
}
