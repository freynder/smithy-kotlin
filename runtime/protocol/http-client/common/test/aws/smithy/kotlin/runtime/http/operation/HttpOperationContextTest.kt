/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.util.get
import kotlin.test.*

class HttpOperationContextTest {

    @Test
    fun testBuilder() {
        val op = HttpOperationContext.build {
            operationName = "operation"
            expectedHttpStatus = 418
        }

        assertEquals("operation", op[SdkClientOption.OperationName])
        assertEquals(418, op[HttpOperationContext.ExpectedHttpStatus])
    }

    @Test
    fun testMissingRequiredProperties() {
        val ex = assertFailsWith<IllegalArgumentException> {
            HttpOperationContext.build {
            }
        }

        assertTrue(ex.message!!.contains("OperationName is a required property"))
    }
}
