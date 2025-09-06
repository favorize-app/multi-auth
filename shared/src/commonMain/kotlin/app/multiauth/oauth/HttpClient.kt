package app.multiauth.oauth

import kotlinx.coroutines.flow.Flow

/**
 * Simple HTTP client interface for OAuth operations.
 * This is a minimal interface that can be implemented by platform-specific HTTP clients.
 */
interface HttpClient {
    
    /**
     * Makes a POST request to the specified URL.
     * 
     * @param url The URL to send the request to
     * @param block Configuration block for the request
     * @return HttpResponse containing the response data
     */
    suspend fun post(url: String, block: HttpRequestBuilder.() -> Unit): HttpResponse
    
    /**
     * Makes a GET request to the specified URL.
     * 
     * @param url The URL to send the request to
     * @param block Configuration block for the request
     * @return HttpResponse containing the response data
     */
    suspend fun get(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse
}

/**
 * Builder for HTTP requests.
 */
interface HttpRequestBuilder {
    
    /**
     * Sets the request body.
     */
    fun setBody(body: String)
    
    /**
     * Sets a header.
     */
    fun header(name: String, value: String)
    
    /**
     * Sets the content type.
     */
    fun contentType(type: String)
}

/**
 * HTTP response from the client.
 */
interface HttpResponse {
    
    /**
     * The HTTP status of the response.
     */
    val status: HttpStatus
    
    /**
     * The response body as text.
     */
    suspend fun bodyAsText(): String
    
    /**
     * The response body as bytes.
     */
    suspend fun bodyAsBytes(): ByteArray
}

/**
 * HTTP status codes.
 */
enum class HttpStatus(val code: Int) {
    OK(200),
    CREATED(201),
    ACCEPTED(202),
    NO_CONTENT(204),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    TOO_MANY_REQUESTS(429),
    INTERNAL_SERVER_ERROR(500),
    BAD_GATEWAY(502),
    SERVICE_UNAVAILABLE(503);
    
    val isSuccess: Boolean
        get() = code in 200..299
    
    companion object {
        fun fromCode(code: Int): HttpStatus {
            return values().find { it.code == code } ?: INTERNAL_SERVER_ERROR
        }
    }
}
