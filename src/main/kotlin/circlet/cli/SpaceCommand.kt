package circlet.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.cio.*
import kotlinx.coroutines.runBlocking
import space.jetbrains.api.runtime.SpaceClient
import space.jetbrains.api.runtime.ktorClientForSpace
import space.jetbrains.api.runtime.types.ProjectIdentifier
import java.nio.file.Path

abstract class SpaceCommand : CliktCommand(printHelpOnEmptyArgs = true) {

    protected val spaceProjectKey by option("--space-project-key", help = "key of project in space").required()

    private val spaceServer by option("--space-server",
        help = "URL of the Space instance that you want to import into."
    ).required()

    private val spaceToken by option(
        "--space-token",
        help = "personal token for a Space account that has the Import Issues permission."
    ).required()

    protected val spaceUrl by lazy {
        spaceServer.let {
            if (it.startsWith("http").not()) {
                "https://$it"
            } else {
                it
            }
        }
    }

    protected val spaceClient by lazy {
        initializeSpaceClient()
    }

    protected val projectIdentifier by lazy {
        ProjectIdentifier.Key(spaceProjectKey)
    }

    private val documentsUrl by lazy {
        URLBuilder().apply {
            takeFrom(spaceUrl)
            appendPathSegments("p", spaceProjectKey, "documents")
        }.buildString()
    }

    private fun initializeSpaceClient(): SpaceClient {
        return SpaceClient(ktorClientForSpace(Apache) {
            engine {
                socketTimeout = 120_000
                connectTimeout = 30_000
                connectionRequestTimeout = 30_000
            }
            install(UserAgent) {
                agent = "Space CLI client"
            }
        }, spaceUrl, spaceToken)
    }

    override fun run() = runBlocking {
        migrate()
        println("Import was successful. Open Space documents: $documentsUrl")
    }

    abstract suspend fun migrate()
}

suspend fun SpaceClient.uploadBlob(filePath: Path): String {
    val uploadUrl = URLBuilder().apply {
        takeFrom(server.serverUrl)
        appendPathSegments("storage/blobs")
    }.build()

    val response = ktorClient.post(uploadUrl) {
        bearerAuth(auth.token(ktorClient, appInstance).accessToken)
        setBody(filePath.readChannel())
    }

    return response.body()
}

suspend fun SpaceClient.uploadImage(filePath: Path, fileName: String): String {
    val uploadUrl = URLBuilder().apply {
        takeFrom(server.serverUrl)
        appendPathSegments("uploads")
        parameters.append("name", fileName)
    }.build()

    val response = ktorClient.post(uploadUrl) {
        bearerAuth(auth.token(ktorClient, appInstance).accessToken)
        setBody(filePath.readChannel())
    }

    return response.body()
}
