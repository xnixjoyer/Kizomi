package com.anisync.android.data.util

import com.anisync.android.domain.Result
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import kotlinx.coroutines.CancellationException

/**
 * Executes an Apollo GraphQL call with comprehensive, sanitized error handling.
 *
 * Cancellation is always rethrown. Known status and retry metadata are retained, while exception
 * messages and GraphQL body text are never promoted to user-visible or diagnostic strings.
 */
suspend fun <T> safeApiCall(
    apiCall: suspend () -> T
): Result<T> {
    return try {
        Result.Success(apiCall())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: ApiError.RateLimited) {
        Result.Error(
            "Too many requests. Please wait ${e.retryAfterSeconds} seconds.",
            429,
            e.retryAfterSeconds,
            e,
        )
    } catch (e: ApiError.Unauthorized) {
        Result.Error("Your session has expired. Please log in again.", 401, null, e)
    } catch (e: ApiError.Forbidden) {
        Result.Error("You don't have permission to do that.", 403, null, e)
    } catch (e: ApiError.ServerError) {
        Result.Error("Server error (${e.statusCode}). Please try again later.", e.statusCode, null, e)
    } catch (e: ApiError.NetworkError) {
        Result.Error("No internet connection. Check your network and try again.", null, null, e)
    } catch (e: ApiError.GraphQLError) {
        Result.Error("The provider rejected the request.", e.statusCode, null, e)
    } catch (e: ApiError) {
        Result.Error("An API error occurred.", null, null, e)
    } catch (e: ApolloHttpException) {
        val message = when (e.statusCode) {
            429 -> "Too many requests. Please try again later."
            401 -> "Session expired. Please log in again."
            403 -> "Access denied."
            in 500..599 -> "Server error. Please try again later."
            else -> "HTTP request failed (${e.statusCode})."
        }
        Result.Error(message, e.statusCode, null, e)
    } catch (e: ApolloNetworkException) {
        Result.Error("No internet connection. Check your network and try again.", null, null, e)
    } catch (e: ApolloException) {
        Result.Error("Network request failed. Please try again.", null, null, e)
    } catch (e: Exception) {
        Result.Error("An unexpected error occurred.", null, null, e)
    }
}

/**
 * Executes an Apollo GraphQL query/mutation and checks for GraphQL-level errors.
 *
 * The response body is inspected only to classify the failure. Provider messages remain attached to
 * the internal exception and are not copied into [Result.Error.message] or its string representation.
 */
suspend fun <D : Operation.Data, T> safeApiCallWithResponse(
    action: String,
    execute: suspend () -> ApolloResponse<D>,
    transform: (ApolloResponse<D>) -> T
): Result<T> {
    return safeApiCall {
        val response = execute()
        if (response.hasErrors()) {
            val messages = response.errors?.map { it.message } ?: listOf("Unknown error")
            val statusCode = response.errors?.firstOrNull()?.let { error ->
                (error.extensions?.get("status") as? Number)?.toInt()
            }
            throw ApiError.GraphQLError(messages, statusCode)
        }
        transform(response)
    }
}
