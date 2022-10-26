package circlet.cli.confluence

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup
import space.jetbrains.api.runtime.ktorClientForSpace

class ConfluenceClient(private val host: String, private val credentials: Credentials?) {
    private val httpClient = ktorClientForSpace(Apache) {
        engine {
            socketTimeout = 120_000
            connectTimeout = 30_000
            connectionRequestTimeout = 30_000
        }
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }

        install(UserAgent) {
            agent = "Space CLI packages client"
        }
    }

    suspend fun getDocuments(spaceKey: String): Flow<DocumentInfo> {
        return flow {
            var page: DocumentsInfoResponse = getDocumentsPage(spaceKey)
            while (page.results.isNotEmpty()) {
                page.results.forEach { emit(it) }
                val nextPageParams = page.links["next"]
                    ?.substringAfter("?")
                    ?.let { parseQueryString(it) }
                    ?: break
                val limit = nextPageParams["limit"]?.toInt()
                val start = nextPageParams["start"]?.toInt() ?: 0
                page = getDocumentsPage(spaceKey, limit, start)
            }
        }
    }

    suspend fun getUserData(username: String): UserData {
        val htmlContent = httpClient
            .get(
                buildUrl("/display/~$username"),
                block = {
                    credentials?.let {
                        header(HttpHeaders.Authorization, it.renderHeader())
                    }
                }
            )
            .bodyAsText()
        val parsedTree = Jsoup.parse(htmlContent)
        val fullName = parsedTree.getElementById("fullName")?.text() ?: throw IllegalArgumentException("User $username not found")
        val email = parsedTree.getElementById("email")?.text()
        return UserData(fullName, if (email == null || email == "hidden") null else email)
    }

    suspend fun getDocumentChild(id: Int): List<DocumentInfo> {
        val documentInfo: DocumentsInfoResponse = httpClient
            .get(
                buildUrl("/rest/api/content/$id/child/page"),
                block = {
                    credentials?.let {
                        header(HttpHeaders.Authorization, it.renderHeader())
                    }
                }
            )
            .body()
        return documentInfo.results
    }

    suspend fun getDocumentById(id: Int): DocumentInfo {
        return httpClient
            .get(
                buildUrl(
                    "/rest/api/content/$id",
                    mapOf("expand" to "body.export_view")
                ),
                block = {
                    credentials?.let {
                        header(HttpHeaders.Authorization, it.renderHeader())
                    }
                }
            )
            .body()
    }

    private suspend fun getDocumentsPage(
        spaceKey: String, limit: Int? = null, start: Int? = null
    ): DocumentsInfoResponse = httpClient
        .get(
            buildUrl(
                "/rest/api/content",
                mapOf(
                    "spaceKey" to spaceKey,
                    "expand" to "ancestors",
                    "limit" to (limit ?: 25),
                    "start" to (start ?: 0)
                )
            ),
            block = {
                credentials?.let {
                    header(HttpHeaders.Authorization, it.renderHeader())
                }
            }
        )
        .body()

    private fun buildUrl(path: String, queryParameters: Map<String, Any> = emptyMap()) = URLBuilder().apply {
        takeFrom(this@ConfluenceClient.host)
        appendPathSegments(path.removePrefix("/").split("/"))
        queryParameters.forEach { (name, value) -> parameters.append(name, value.toString()) }
    }.buildString()
}

data class DocumentsInfoResponse(
    val results: List<DocumentInfo>,
    @JsonProperty("_links")
    val links: Map<String, String>
)

data class DocumentInfo(
    val id: Int,
    val type: String,
    val title: String,
    val body: BodyInfo?,
    val ancestors: List<DocumentInfo>?,
    @JsonProperty("_links")
    val links: Map<String, String>
)

data class BodyInfo(
    val storage: ValueInfo?,
    @JsonProperty("export_view")
    val exportView : ValueInfo?
)

data class ValueInfo(val value: String)

data class UserData(val username: String, val email: String?)


