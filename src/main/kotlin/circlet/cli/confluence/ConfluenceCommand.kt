package circlet.cli.confluence

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.runBlocking
import space.jetbrains.api.runtime.SpaceClient
import space.jetbrains.api.runtime.ktorClientForSpace
import space.jetbrains.api.runtime.resources.projects
import space.jetbrains.api.runtime.types.*
import java.util.*

class ConfluenceCommand : CliktCommand(printHelpOnEmptyArgs = true) {
    private val confluenceHost by option("--confluence-host", help = "host of confluence instance").required()
    private val confluenceSpaceKey by option("--confluence-space-key", help = "key of space in confluence").required()
    private val confluenceUserName by option("--confluence-username", help = "username to authorize in confluence")
    private val confluencePassword by option("--confluence-password", help = "password to authorize in confluence")
    private val spaceProjectKey by option("--space-project-key", help = "key of project in space").required()
    private val spaceServer by option("--space-server",
        help = "URL of the Space instance that you want to import into."
    ).required()
    private val spaceToken by option(
        "--space-token",
        help = "personal token for a Space account that has the Import Issues permission."
    ).required()

    private val client by lazy { ConfluenceClient(confluenceHost, getAuth()) }
    private val spaceClient by lazy { initializeSpaceClient() }

    private val confluenceIdToSpaceId = hashMapOf<Int, String>()
    private val confluenceAliasToSpaceAlias = hashMapOf<String, String>()

    private val spaceUrl by lazy {
        spaceServer.let {
            if (it.startsWith("http").not()) {
                "https://$it"
            } else {
                it
            }
        }
    }

    private val documentConverter by lazy {
        HtmlToMarkdownConverter(
            SpaceDocumentsLinkResolver.Factory(
                client, confluenceHost, confluenceSpaceKey, spaceClient, spaceUrl, spaceProjectKey, confluenceAliasToSpaceAlias
            )
        )
    }

    override fun run() {
        runBlocking {
            val documents = client.getDocuments(confluenceSpaceKey)
            documents.collect { migrateDocument(it) }
            confluenceIdToSpaceId.map { (confluenceId, spaceId) ->
                migrateDocumentContent(confluenceId, spaceId)
            }
        }
    }

    private suspend fun migrateDocument(document: DocumentInfo) {
        val isFolderIntroduction = client.getDocumentChild(document.id).isNotEmpty()
        val folder = getOrCreateFolder(document, isFolderIntroduction)
        val uiLink = document.links["webui"] ?: throw IllegalArgumentException("Can't migrate document without webui link")
        val createdDocument = spaceClient.projects.documents.createDocument(
            project = ProjectIdentifier.Key(spaceProjectKey),
            name = document.title,
            folder = FolderIdentifier.Id(folder.id),
            bodyIn = TextDocumentBodyCreateTypedIn(MdTextDocumentContent("Intermediate content"))
        )
        if (isFolderIntroduction) {
            spaceClient.projects.documents.folders.introduction.addFolderIntroduction(
                project = ProjectIdentifier.Key(spaceProjectKey),
                folder = FolderIdentifier.Id(folder.id),
                documentId = createdDocument.id
            )
        }
        confluenceIdToSpaceId[document.id] = createdDocument.id
        confluenceAliasToSpaceAlias[uiLink.substringAfterLast("/")] = createdDocument.alias
    }

    private suspend fun migrateDocumentContent(confluenceId: Int, spaceId: String) {
        val confluenceDocument = client.getDocumentById(confluenceId)
        val convertedContent = documentConverter.convertDocument(confluenceDocument)
        spaceClient.projects.documents.updateDocument(
            ProjectIdentifier.Key(spaceProjectKey),
            spaceId,
            updateIn = TextDocumentBodyUpdateIn(convertedContent)
        )
    }

    private suspend fun getOrCreateFolder(documentInfo: DocumentInfo, createFolderForDoc: Boolean): DocumentFolder {
        var currentFolder = getOrCreateFolder(confluenceSpaceKey, FolderIdentifier.Root)
        documentInfo.ancestors?.forEach {
            currentFolder = getOrCreateFolder(it.title, FolderIdentifier.Id(currentFolder.id))
        }
        if (createFolderForDoc) {
            currentFolder = getOrCreateFolder(documentInfo.title, FolderIdentifier.Id(currentFolder.id))
        }
        return currentFolder
    }

    private suspend fun getOrCreateFolder(folderName: String, parent: FolderIdentifier): DocumentFolder {
        val subFolders = spaceClient.projects.documents.folders.subfolders.listSubfolders(
            ProjectIdentifier.Key(spaceProjectKey), parent
        ).data
        return subFolders.find { it.name == folderName } ?: spaceClient.projects.documents.folders.createFolder(
            project = ProjectIdentifier.Key(spaceProjectKey),
            folderName,
            parent
        )
    }

    private fun getAuth(): Credentials? {
        val confluenceUserName = confluenceUserName
        val confluencePassword = confluencePassword
        return when {
            confluenceUserName != null && confluencePassword != null -> {
                UsernamePasswordCredentials(confluenceUserName, confluencePassword)
            }
            confluenceUserName == null -> null
            else -> throw IllegalArgumentException("Confluence username and password must be specified together")
        }
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
}

internal data class UsernamePasswordCredentials(val username: String, val password: String) : Credentials {
    override fun renderHeader() = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}

interface Credentials {
    fun renderHeader(): String
}

