/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.kotlin.codegen.rendering.util.RuntimeConfigProperty
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import kotlin.test.Test

class ServiceClientConfigGeneratorTest {
    private fun getModel(): Model = loadModelFromResource("idempotent-token-test-model.smithy")

    private fun createWriter() =
        KotlinWriter(TestModelDefault.NAMESPACE).apply { putContext("service.name", TestModelDefault.SERVICE_NAME) }

    @Test
    fun `it detects default properties`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        ServiceClientConfigGenerator(serviceShape).render(renderingCtx, renderingCtx.writer)
        val contents = writer.toString()

        contents.assertBalancedBracesAndParens()

        val expectedCtor = """
public class Config private constructor(builder: Builder) : HttpAuthConfig, HttpClientConfig, IdempotencyTokenConfig, SdkClientConfig, TracingClientConfig {
"""
        contents.shouldContainWithDiff(expectedCtor)

        val expectedProps = """
    override val clientName: String = builder.clientName
    override val httpClientEngine: HttpClientEngine = builder.httpClientEngine ?: DefaultHttpEngine().manage()
    override val authSchemes: kotlin.collections.List<aws.smithy.kotlin.runtime.http.auth.HttpAuthScheme> = builder.authSchemes
    public val endpointProvider: EndpointProvider = requireNotNull(builder.endpointProvider) { "endpointProvider is a required configuration property" }
    override val idempotencyTokenProvider: IdempotencyTokenProvider = builder.idempotencyTokenProvider ?: IdempotencyTokenProvider.Default
    override val interceptors: kotlin.collections.List<aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor> = builder.interceptors
    override val retryPolicy: RetryPolicy<Any?> = builder.retryPolicy ?: StandardRetryPolicy.Default
    override val retryStrategy: RetryStrategy = builder.retryStrategy ?: StandardRetryStrategy()
    override val sdkLogMode: SdkLogMode = builder.sdkLogMode
    override val tracer: Tracer = builder.tracer ?: DefaultTracer(LoggingTraceProbe, clientName)
"""
        contents.shouldContainWithDiff(expectedProps)

        val expectedBuilder = """
    public class Builder : HttpAuthConfig.Builder, HttpClientConfig.Builder, IdempotencyTokenConfig.Builder, SdkClientConfig.Builder<Config>, TracingClientConfig.Builder {
        /**
         * A reader-friendly name for the client.
         */
        override var clientName: String = "Test"

        /**
         * Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts, concurrency, etc).
         * NOTE: The caller is responsible for managing the lifetime of the engine when set. The SDK
         * client will not close it when the client is closed.
         */
        override var httpClientEngine: HttpClientEngine? = null

        /**
         * Register new or override default [HttpAuthScheme]s configured for this client. By default, the set
         * of auth schemes configured comes from the service model. An auth scheme configured explicitly takes
         * precedence over the defaults and can be used to customize identity resolution and signing for specific
         * authentication schemes.
         */
        override var authSchemes: kotlin.collections.List<aws.smithy.kotlin.runtime.http.auth.HttpAuthScheme> = emptyList()

        /**
         * The endpoint provider used to determine where to make service requests. **This is an advanced config
         * option.**
         *
         * Endpoint resolution occurs as part of the workflow for every request made via the service client.
         *
         * The inputs to endpoint resolution are defined on a per-service basis (see [EndpointParameters]).
         */
        public var endpointProvider: EndpointProvider? = null

        /**
         * Override the default idempotency token generator. SDK clients will generate tokens for members
         * that represent idempotent tokens when not explicitly set by the caller using this generator.
         */
        override var idempotencyTokenProvider: IdempotencyTokenProvider? = null

        /**
         * Add an [aws.smithy.kotlin.runtime.client.Interceptor] that will have access to read and modify
         * the request and response objects as they are processed by the SDK.
         * Interceptors added using this method are executed in the order they are configured and are always
         * later than any added automatically by the SDK.
         */
        override var interceptors: kotlin.collections.MutableList<aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor> = kotlin.collections.mutableListOf()

        /**
         * The policy to use for evaluating operation results and determining whether/how to retry.
         */
        override var retryPolicy: RetryPolicy<Any?>? = null

        /**
         * The [RetryStrategy] implementation to use for service calls. All API calls will be wrapped by the
         * strategy.
         */
        override var retryStrategy: RetryStrategy? = null

        /**
         * Configure events that will be logged. By default clients will not output
         * raw requests or responses. Use this setting to opt-in to additional debug logging.
         *
         * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
         *
         * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
         * performance considerations when dumping the request/response body. This is primarily a tool for
         * debug purposes.
         */
        override var sdkLogMode: SdkLogMode = SdkLogMode.Default

        /**
         * The tracer that is responsible for creating trace spans and wiring them up to a tracing backend (e.g.,
         * a trace probe). By default, this will create a standard tracer that uses the service name for the root
         * trace span and delegates to a logging trace probe (i.e.,
         * `DefaultTracer(LoggingTraceProbe, "<service-name>")`).
         */
        override var tracer: Tracer? = null

        override fun build(): Config = Config(this)
    }
"""
        contents.shouldContainWithDiff(expectedBuilder)

        val expectedImports = listOf(
            "import ${RuntimeTypes.HttpClient.Engine.HttpClientEngine.fullName}",
            "import ${KotlinDependency.HTTP.namespace}.config.HttpClientConfig",
            "import ${KotlinDependency.CORE.namespace}.client.IdempotencyTokenConfig",
            "import ${KotlinDependency.CORE.namespace}.client.IdempotencyTokenProvider",
            "import ${KotlinDependency.CORE.namespace}.client.SdkClientConfig",
            "import ${KotlinDependency.CORE.namespace}.client.SdkLogMode",
        )
        expectedImports.forEach {
            contents.shouldContainWithDiff(it)
        }
    }

    @Test
    fun `it handles additional props`() {
        val model = getModel()

        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        val customProps = listOf(
            ConfigProperty.Int("intProp", 1, documentation = "non-null-int"),
            ConfigProperty.Int("nullIntProp"),
            ConfigProperty.String("stringProp"),
            ConfigProperty.Boolean("boolProp"),
        )

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, customProps, renderingCtx.writer)
        val contents = writer.toString()

        // we should have no base classes when not using the default and no inheritFrom specified
        val expectedCtor = """
public class Config private constructor(builder: Builder) {
"""
        contents.shouldContain(expectedCtor)

        val expectedProps = """
    public val boolProp: Boolean? = builder.boolProp
    public val intProp: Int = builder.intProp
    public val nullIntProp: Int? = builder.nullIntProp
    public val stringProp: String? = builder.stringProp
"""
        contents.shouldContainWithDiff(expectedProps)

        val expectedBuilderProps = """
        public var boolProp: Boolean? = null

        /**
         * non-null-int
         */
        public var intProp: Int = 1

        public var nullIntProp: Int? = null

        public var stringProp: String? = null
"""
        contents.shouldContainWithDiff(expectedBuilderProps)
    }

    @Test
    fun `it registers integration props`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val customIntegration = object : KotlinIntegration {

            override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> =
                listOf(ConfigProperty.Int("customProp"))
        }

        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)
            .copy(integrations = listOf(customIntegration))

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, renderingCtx.writer)
        val contents = writer.toString()

        val expectedProps = """
    public val customProp: Int? = builder.customProp
"""
        contents.shouldContain(expectedProps)
    }

    @Test
    fun `it overrides props by name`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val customIntegration = object : KotlinIntegration {
            private val overriddenLogMode = RuntimeConfigProperty.SdkLogMode.toBuilder().apply {
                symbol = symbol!!.toBuilder().apply {
                    defaultValue("SdkLogMode.LogRequest") // replaces SdkLogMode.Default
                }.build()
            }.build()

            override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> = listOf(
                ConfigProperty.Int("customProp"),
                overriddenLogMode,
            )
        }

        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)
            .copy(integrations = listOf(customIntegration))

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = true).render(renderingCtx, renderingCtx.writer)
        val contents = writer.toString()

        val expectedBuilderProps = """
        /**
         * A reader-friendly name for the client.
         */
        override var clientName: String = "Test"

        /**
         * Override the default HTTP client engine used to make SDK requests (e.g. configure proxy behavior, timeouts, concurrency, etc).
         * NOTE: The caller is responsible for managing the lifetime of the engine when set. The SDK
         * client will not close it when the client is closed.
         */
        override var httpClientEngine: HttpClientEngine? = null

        /**
         * Register new or override default [HttpAuthScheme]s configured for this client. By default, the set
         * of auth schemes configured comes from the service model. An auth scheme configured explicitly takes
         * precedence over the defaults and can be used to customize identity resolution and signing for specific
         * authentication schemes.
         */
        override var authSchemes: kotlin.collections.List<aws.smithy.kotlin.runtime.http.auth.HttpAuthScheme> = emptyList()

        public var customProp: Int? = null

        /**
         * The endpoint provider used to determine where to make service requests. **This is an advanced config
         * option.**
         *
         * Endpoint resolution occurs as part of the workflow for every request made via the service client.
         *
         * The inputs to endpoint resolution are defined on a per-service basis (see [EndpointParameters]).
         */
        public var endpointProvider: EndpointProvider? = null

        /**
         * Override the default idempotency token generator. SDK clients will generate tokens for members
         * that represent idempotent tokens when not explicitly set by the caller using this generator.
         */
        override var idempotencyTokenProvider: IdempotencyTokenProvider? = null

        /**
         * Add an [aws.smithy.kotlin.runtime.client.Interceptor] that will have access to read and modify
         * the request and response objects as they are processed by the SDK.
         * Interceptors added using this method are executed in the order they are configured and are always
         * later than any added automatically by the SDK.
         */
        override var interceptors: kotlin.collections.MutableList<aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor> = kotlin.collections.mutableListOf()

        /**
         * The policy to use for evaluating operation results and determining whether/how to retry.
         */
        override var retryPolicy: RetryPolicy<Any?>? = null

        /**
         * The [RetryStrategy] implementation to use for service calls. All API calls will be wrapped by the
         * strategy.
         */
        override var retryStrategy: RetryStrategy? = null

        /**
         * Configure events that will be logged. By default clients will not output
         * raw requests or responses. Use this setting to opt-in to additional debug logging.
         *
         * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
         *
         * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
         * performance considerations when dumping the request/response body. This is primarily a tool for
         * debug purposes.
         */
        override var sdkLogMode: SdkLogMode = SdkLogMode.LogRequest

        /**
         * The tracer that is responsible for creating trace spans and wiring them up to a tracing backend (e.g.,
         * a trace probe). By default, this will create a standard tracer that uses the service name for the root
         * trace span and delegates to a logging trace probe (i.e.,
         * `DefaultTracer(LoggingTraceProbe, "<service-name>")`).
         */
        override var tracer: Tracer? = null
"""
        contents.shouldContainWithDiff(expectedBuilderProps)
    }

    @Test
    fun `it finds idempotency token via resources`() {
        val model = """
            namespace com.test
            
            service ResourceService {
                resources: [Resource],
                version: "1"
            }
            resource Resource {
                operations: [CreateResource]
            }
            operation CreateResource {
                input: IdempotentInput
            }
            structure IdempotentInput {
                @idempotencyToken
                tok: String
            }
        """.toSmithyModel()

        model.expectShape<ServiceShape>("com.test#ResourceService").hasIdempotentTokenMember(model) shouldBe true
    }

    @Test
    fun `it imports references`() {
        val model = getModel()

        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        val customProps = listOf(
            ConfigProperty {
                name = "complexProp"
                symbol = buildSymbol {
                    name = "ComplexType"
                    namespace = "test.complex"
                    reference(
                        buildSymbol { name = "SubTypeA"; namespace = "test.complex" },
                        SymbolReference.ContextOption.USE,
                    )
                    reference(
                        buildSymbol { name = "SubTypeB"; namespace = "test.complex" },
                        SymbolReference.ContextOption.USE,
                    )
                }
            },
        )

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, customProps, renderingCtx.writer)
        val contents = writer.toString()

        listOf(
            "test.complex.ComplexType",
            "test.complex.SubTypeA",
            "test.complex.SubTypeB",
        )
            .map { "import $it" }
            .forEach(contents::shouldContain)
    }

    @Test
    fun `it renders a companion object`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, renderingCtx.writer)

        val contents = writer.toString()

        contents.assertBalancedBracesAndParens()

        val expectedCompanion = """
    public companion object {
        public inline operator fun invoke(block: Builder.() -> kotlin.Unit): Config = Builder().apply(block).build()
    }
"""
        contents.shouldContainWithDiff(expectedCompanion)
    }

    @Test
    fun testPropertyTypesRenderCorrectly() {
        val model = getModel()

        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        val customProps = listOf(
            ConfigProperty {
                name = "nullFoo"
                symbol = buildSymbol { name = "Foo" }
            },
            ConfigProperty {
                name = "defaultFoo"
                symbol = buildSymbol { name = "Foo"; defaultValue = "DefaultFoo"; nullable = false }
            },
            ConfigProperty {
                name = "constFoo"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ConfigPropertyType.ConstantValue("ConstantFoo")
            },
            ConfigProperty {
                name = "requiredFoo"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ConfigPropertyType.Required()
            },
            ConfigProperty {
                name = "requiredFoo2"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ConfigPropertyType.Required("override message")
            },
            ConfigProperty {
                name = "requiredDefaultedFoo"
                symbol = buildSymbol { name = "Foo" }
                propertyType = ConfigPropertyType.RequiredWithDefault("DefaultedFoo()")
            },
            ConfigProperty {
                name = "builderSymbolDiffers"
                symbol = buildSymbol { name = "List<Foo>" }
                builderSymbol = buildSymbol { name = "MutableList<Foo>" }
                toBuilderExpression = ".toMutableList()"
                propertyType = ConfigPropertyType.SymbolDefault
            },
            ConfigProperty {
                name = "builderSymbolImmutable"
                symbol = buildSymbol {
                    name = "List<Foo>"
                    nullable = false
                }
                builderSymbol = buildSymbol {
                    name = "MutableList<Foo>"
                    defaultValue = "mutableListOf()"
                    nullable = false
                    setProperty(SymbolProperty.PROPERTY_TYPE_MUTABILITY, PropertyTypeMutability.IMMUTABLE)
                }
                toBuilderExpression = ".toMutableList()"
                propertyType = ConfigPropertyType.SymbolDefault
            },
        )

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, customProps, renderingCtx.writer)
        val contents = writer.toString()

        val expectedProps = """
    public val builderSymbolDiffers: List<Foo>? = builder.builderSymbolDiffers
    public val builderSymbolImmutable: List<Foo> = builder.builderSymbolImmutable
    public val constFoo: Foo = ConstantFoo
    public val defaultFoo: Foo = builder.defaultFoo
    public val nullFoo: Foo? = builder.nullFoo
    public val requiredDefaultedFoo: Foo = builder.requiredDefaultedFoo ?: DefaultedFoo()
    public val requiredFoo: Foo = requireNotNull(builder.requiredFoo) { "requiredFoo is a required configuration property" }
    public val requiredFoo2: Foo = requireNotNull(builder.requiredFoo2) { "override message" }
"""
        contents.shouldContainWithDiff(expectedProps)

        val expectedImplProps = """
        public var builderSymbolDiffers: MutableList<Foo>? = null

        public val builderSymbolImmutable: MutableList<Foo> = mutableListOf()

        public var defaultFoo: Foo = DefaultFoo

        public var nullFoo: Foo? = null

        public var requiredDefaultedFoo: Foo? = null

        public var requiredFoo: Foo? = null

        public var requiredFoo2: Foo? = null
"""
        contents.shouldContainWithDiff(expectedImplProps)
    }

    @Test
    fun `it renders toBuilder impl`() {
        val model = getModel()
        val serviceShape = model.expectShape<ServiceShape>(TestModelDefault.SERVICE_SHAPE_ID)

        val testCtx = model.newTestContext()
        val writer = createWriter()
        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)

        val additionalProps = listOf(
            // constant values should be omitted
            ConfigProperty {
                name = "testConstantBooleanField"
                symbol = KotlinTypes.Boolean
                propertyType = ConfigPropertyType.ConstantValue("true")
            },
            ConfigProperty {
                name = "testListField"
                symbol = KotlinTypes.Collections.List
                builderSymbol = KotlinTypes.Collections.MutableList
                toBuilderExpression = ".toMutableList()"
            },
        )

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, additionalProps, renderingCtx.writer)
        val contents = writer.toString()

        val expectedProps = """
            public fun toBuilder(): Builder = Builder().apply {
                testListField = this@Config.testListField.toMutableList()
            }""".formatForTest()
        contents.shouldContainWithDiff(expectedProps)
    }
}
