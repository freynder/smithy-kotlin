package software.aws.ktlint.rules

import com.pinterest.ktlint.core.Rule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

class CopyrightHeaderRule : Rule("copyright-header") {
    companion object {
        private val header = """
            /*
             * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
             * SPDX-License-Identifier: Apache-2.0
             */
        """.trimIndent()
    }
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (!node.text.startsWith(header)) {
            emit(node.startOffset, "Missing or incorrect file header", false)
        }
        stopTraversalOfAST() // traverse the root node only
    }
}
