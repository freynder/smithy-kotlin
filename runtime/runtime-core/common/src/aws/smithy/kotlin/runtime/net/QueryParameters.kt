/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.*
import aws.smithy.kotlin.runtime.util.text.splitAsQueryString
import aws.smithy.kotlin.runtime.util.text.urlEncodeComponent

/**
 * Container for HTTP query parameters
 */
public interface QueryParameters : ValuesMap<String> {
    public companion object {
        public operator fun invoke(block: QueryParametersBuilder.() -> Unit): QueryParameters = QueryParametersBuilder()
            .apply(block).build()

        /**
         * Empty [QueryParameters] instance
         */
        public val Empty: QueryParameters = EmptyQueryParameters
    }
}

private object EmptyQueryParameters : QueryParameters {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<String> = emptyList()
    override fun names(): Set<String> = emptySet()
    override fun entries(): Set<Map.Entry<String, List<String>>> = emptySet()
    override fun contains(name: String): Boolean = false
    override fun isEmpty(): Boolean = true
}

public class QueryParametersBuilder : ValuesMapBuilder<String>(true, 8), CanDeepCopy<QueryParametersBuilder> {
    override fun toString(): String = "QueryParametersBuilder ${entries()} "
    override fun build(): QueryParameters = QueryParametersImpl(values)
    override fun deepCopy(): QueryParametersBuilder {
        val originalValues = values.deepCopy()
        return QueryParametersBuilder().apply { values.putAll(originalValues) }
    }
}

public fun Map<String, String>.toQueryParameters(): QueryParameters {
    val builder = QueryParametersBuilder()
    entries.forEach { entry -> builder.append(entry.key, entry.value) }
    return builder.build()
}

private class QueryParametersImpl(values: Map<String, List<String>> = emptyMap()) : QueryParameters, ValuesMapImpl<String>(true, values) {
    override fun toString(): String = "QueryParameters ${entries()}"

    override fun equals(other: Any?): Boolean = other is QueryParameters && entries() == other.entries()
}

/**
 * Return the encoded query parameter string (without leading '?')
 */
public fun QueryParameters.urlEncode(): String = buildString {
    urlEncodeTo(this)
}

/**
 * URL encode the query parameters components to the appendable output (without the leading '?')
 */
public fun QueryParameters.urlEncodeTo(out: Appendable): Unit = urlEncodeQueryParametersTo(entries(), out)

internal fun urlEncodeQueryParametersTo(entries: Set<Map.Entry<String, List<String>>>, out: Appendable, encodeFn: (String) -> String = { it.urlEncodeComponent() }) {
    entries.sortedBy { it.key }.forEachIndexed { i, entry ->
        entry.value.forEachIndexed { j, value ->
            if (i > 0 || j > 0) {
                out.append("&")
            }
            // FIXME keys should be %-encoded
            out.append(entry.key)
            out.append("=")
            out.append(encodeFn(value))
        }
    }
}

/**
 * Parse a set of [QueryParameters] out of a full URI. If the URI does not contain a `?` (or contains nothing after the
 * `?`) then the result is null.
 */
@InternalApi
public fun CharSequence.fullUriToQueryParameters(): QueryParameters? {
    val idx = indexOf("?")
    if (idx < 0 || idx + 1 >= length) return null

    val fragmentIdx = indexOf("#", startIndex = idx)
    val rawQueryString = if (fragmentIdx > 0) substring(idx + 1, fragmentIdx) else substring(idx + 1)
    return rawQueryString.splitAsQueryParameters()
}

/**
 * Split a (decoded) query string "foo=baz&bar=quux" into it's component parts
 */
@InternalApi
public fun String.splitAsQueryParameters(): QueryParameters {
    val builder = QueryParametersBuilder()
    splitAsQueryString().entries.forEach { entry ->
        builder.appendAll(entry.key, entry.value)
    }
    return builder.build()
}
