package circlet.cli.confluence

import circlet.cli.SpaceCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import space.jetbrains.api.runtime.resources.projects
import space.jetbrains.api.runtime.types.*
import java.util.*

class ConfluenceCommand : SpaceCommand() {
    private val confluenceHost by option("--confluence-host", help = "host of confluence instance").required()
    private val confluenceSpaceKey by option("--confluence-space-key", help = "key of space in confluence").required()
    private val confluenceUserName by option("--confluence-username", help = "username to authorize in confluence")
    private val confluencePassword by option("--confluence-password", help = "password to authorize in confluence")

    private val client by lazy { ConfluenceClient(confluenceHost, getAuth()) }

    private val confluenceIdToSpaceId = hashMapOf<Int, String>()
    private val confluenceAliasToSpaceAlias = hashMapOf<String, String>()

    private val documentConverter by lazy {
        HtmlToMarkdownConverter(
            SpaceDocumentsLinkResolver.Factory(
                client, confluenceHost, confluenceSpaceKey, spaceClient, spaceUrl, spaceProjectKey, confluenceAliasToSpaceAlias
            )
        )
    }

    override suspend fun migrate() {
        val documents = client.getDocuments(confluenceSpaceKey)
        documents.collect { migrateDocument(it) }
        confluenceIdToSpaceId.map { (confluenceId, spaceId) ->
            migrateDocumentContent(confluenceId, spaceId)
        }
    }

    private suspend fun migrateDocument(document: DocumentInfo) {
        val isFolderIntroduction = client.getDocumentChild(document.id).isNotEmpty()
        val folder = getOrCreateFolder(document, isFolderIntroduction)
        val uiLink = document.links["webui"]
            ?: throw IllegalArgumentException("Can't migrate document without webui link")
        val createdDocument = spaceClient.projects.documents.createDocument(
            project = projectIdentifier,
            name = document.title,
            folder = FolderIdentifier.Id(folder.id),
            bodyIn = TextDocumentBodyCreateTypedIn(MdTextDocumentContent("Intermediate content"))
        )
        if (isFolderIntroduction) {
            spaceClient.projects.documents.folders.introduction.addFolderIntroduction(
                project = projectIdentifier,
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
            projectIdentifier,
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
            projectIdentifier, parent
        ).data
        return subFolders.find { it.name == folderName } ?: spaceClient.projects.documents.folders.createFolder(
            project = projectIdentifier,
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
}

internal data class UsernamePasswordCredentials(val username: String, val password: String) : Credentials {
    override fun renderHeader() = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}

interface Credentials {
    fun renderHeader(): String
}

