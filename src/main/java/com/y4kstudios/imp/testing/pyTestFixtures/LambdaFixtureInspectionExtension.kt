package com.y4kstudios.imp.testing.pyTestFixtures

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyTargetExpression

object LambdaFixtureInspectionExtension : PyInspectionExtension() {
    override fun ignoreShadowed(element: PsiElement): Boolean = element is PyTargetExpression && element.isAnyLambdaFixture()
}
