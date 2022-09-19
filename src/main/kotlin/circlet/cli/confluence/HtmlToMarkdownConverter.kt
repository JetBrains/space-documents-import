package circlet.cli.confluence

import com.vladsch.flexmark.html.renderer.ResolvedLink
import com.vladsch.flexmark.html2md.converter.*
import com.vladsch.flexmark.util.format.TableFormatOptions
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Node
import space.jetbrains.api.runtime.SpaceClient
import space.jetbrains.api.runtime.resources.teamDirectory
import space.jetbrains.api.runtime.types.MdTextDocumentContent
import space.jetbrains.api.runtime.types.TextDocumentContent
import java.net.URL

class HtmlToMarkdownConverter(
    private val linkResolverFactory: HtmlLinkResolverFactory
) : DocumentConverter {
    override fun convertDocument(documentInfo: DocumentInfo): TextDocumentContent {
        val converter = FlexmarkHtmlConverter
            .builder()
            .linkResolverFactory(linkResolverFactory)
            .also {
                it.set(FlexmarkHtmlConverter.EXT_INLINE_INS, ExtensionConversion.TEXT)
                it.set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false)
                it.set(TableFormatOptions.FORMAT_TABLE_ADJUST_COLUMN_WIDTH, false)
            }
            .build()
        val documentBody = documentInfo.body?.exportView?.value ?: throw IllegalArgumentException("Can't convert document without export view")
        val mdContent = converter.convert(documentBody)
        return MdTextDocumentContent(mdContent)
    }
}

class SpaceDocumentsLinkResolver(
    private val confluenceClient: ConfluenceClient,
    private val confluenceHost: String,
    private val confluenceSpaceKey: String,
    private val spaceClient: SpaceClient,
    private val spaceUrl: String,
    private val spaceProjectKey: String,
    private val aliasMapping: Map<String, String>
) : HtmlLinkResolver {
    class Factory(
        private val confluenceClient: ConfluenceClient,
        private val confluenceHost: String,
        private val confluenceSpaceKey: String,
        private val spaceClient: SpaceClient,
        private val spaceUrl: String,
        private val spaceProjectKey: String,
        private val aliasMapping: Map<String, String>
    ) : HtmlLinkResolverFactory {
        override fun getAfterDependents() = null
        override fun getBeforeDependents() = null
        override fun affectsGlobalScope() = false

        override fun apply(context: HtmlNodeConverterContext): HtmlLinkResolver {
            return SpaceDocumentsLinkResolver(
                confluenceClient, confluenceHost, confluenceSpaceKey, spaceClient, spaceUrl, spaceProjectKey, aliasMapping
            )
        }
    }

    override fun resolveLink(node: Node, context: HtmlNodeConverterContext, link: ResolvedLink): ResolvedLink {
        val path = when {
            link.url.startsWith("http") -> {
                val parsedUrl = URL(link.url)
                if (parsedUrl.host != confluenceHost) return link
                parsedUrl.path
            }
            link.url.startsWith("/") -> link.url.substringBefore("?").substringBefore("#")
            else -> return link
        }
        if (path == "/pages/viewpage.action") return resolveViewPageLink(link)
        if (path.startsWith("/display/~")) return runBlocking { resolveUserLink(link) }

        val pathParts = path.removePrefix("/").removeSuffix("/").split("/")
        if(pathParts.size < 3) return link
        if(pathParts[0] != "display") return link
        if(pathParts[1] != confluenceSpaceKey) return link
        val spaceDocumentAlias = aliasMapping[pathParts[2]] ?: return link
        return link.withUrl(spaceDocumentUrl(spaceDocumentAlias))
    }

    private suspend fun resolveUserLink(link: ResolvedLink): ResolvedLink {
        val userName = link.url.substringAfterLast("/").removePrefix("~")
        val userData = confluenceClient.getUserData(userName)
        val resolvedUser = if(userData.email == null) {
            val profiles = spaceClient.teamDirectory.profiles.getAllProfiles(query = userData.username).data
            if(profiles.size == 1) profiles[0] else null
        } else {
            spaceClient.teamDirectory.profiles.getProfileByEmail(userData.email)
        }
        return resolvedUser?.username?.let { link.withUrl(spaceUserUrl(it)) } ?: link
    }

    private fun resolveViewPageLink(link: ResolvedLink): ResolvedLink {
        val spaceDocumentAlias = aliasMapping[link.url.substringAfterLast("/")] ?: return link
        return link.withUrl(spaceDocumentUrl(spaceDocumentAlias))
    }

    private fun spaceDocumentUrl(documentAlias: String) = URLBuilder().apply {
        takeFrom(spaceUrl)
        appendPathSegments("p", spaceProjectKey, "documents", confluenceSpaceKey, "a", documentAlias)
    }.buildString()

    private fun spaceUserUrl(userName: String) = URLBuilder().apply {
        takeFrom(spaceUrl)
        appendPathSegments("m", userName)
    }.buildString()
}