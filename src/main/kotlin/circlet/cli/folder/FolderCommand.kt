package circlet.cli.folder

import circlet.cli.SpaceCommand
import circlet.cli.uploadBlob
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.jetbrains.api.runtime.resources.projects
import space.jetbrains.api.runtime.types.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

class FolderCommand : SpaceCommand() {
    private val folderPath by option("--folder", help = "path to folder with files").required()
    private val baseFolder by lazy {
        Paths.get(folderPath).normalize().toAbsolutePath()
    }
    private val visitedFolders = mutableMapOf<String, FolderIdentifier>()

    override suspend fun migrate() {
        getOrCreateFolder(baseFolder)

        withContext(Dispatchers.IO) {
            Files.walk(baseFolder).use { stream ->
                for (filePath in stream) {
                    when {
                        Files.isRegularFile(filePath) -> {
                            if (filePath.extension == "md") {
                                // import MD document
                                createMdDocument(filePath)
                            } else {
                                // import regular file
                                createFileDocument(filePath)
                            }
                        }

                        Files.isDirectory(filePath) -> getOrCreateFolder(filePath)
                    }
                }
            }
        }
    }

    private suspend fun getOrCreateFolder(folderPath: Path): FolderIdentifier {
        val relativePath = baseFolder.relativize(folderPath).toString()
        return visitedFolders.getOrPut(relativePath) {
            val parentFolder = if (relativePath.isEmpty()) {
                FolderIdentifier.Root
            } else {
                getOrCreateFolder(folderPath.parent)
            }

            val folderName = folderPath.name
            val folder = spaceClient.projects.documents.folders.subfolders.listSubfolders(projectIdentifier, parentFolder).data.firstOrNull { folder ->
                folder.name == folderName
            } ?: spaceClient.projects.documents.folders.createFolder(projectIdentifier, folderName, parentFolder)

            FolderIdentifier.Id(folder.id)
        }
    }

    private suspend fun createMdDocument(filePath: Path) {
        val folderIdentifier = getOrCreateFolder(filePath.parent)
        val fileContent = withContext(Dispatchers.IO) {
            Files.readString(filePath)
        }
        spaceClient.projects.documents.createDocument(
            projectIdentifier,
            filePath.name,
            folderIdentifier,
            TextDocumentBodyCreateTypedIn(MdTextDocumentContent(fileContent))
        )
    }

    private suspend fun createFileDocument(filePath: Path) {
        val folderIdentifier = getOrCreateFolder(filePath.parent)
        val blobId = spaceClient.uploadBlob(filePath)
        spaceClient.projects.documents.createDocument(
            projectIdentifier,
            filePath.name,
            folderIdentifier,
            FileDocumentBodyCreateIn(blobId)
        )
    }
}
