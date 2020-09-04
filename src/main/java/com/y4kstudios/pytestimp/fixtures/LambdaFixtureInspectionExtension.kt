package com.y4kstudios.pytestimp.fixtures

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyTargetExpression
import com.y4kstudios.pytestimp.fixtures.isAnyLambdaFixture

object LambdaFixtureInspectionExtension : PyInspectionExtension() {
    override fun ignoreShadowed(element: PsiElement): Boolean = element is PyTargetExpression && element.isAnyLambdaFixture()
}
