package circlet.cli.confluence

import space.jetbrains.api.runtime.types.TextDocumentContent

interface DocumentConverter {
    fun convertDocument(documentInfo: DocumentInfo): TextDocumentContent
}
