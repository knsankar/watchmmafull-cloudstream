package com.watchmmafull

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class WatchMMAFullProvider : MainAPI() {
    override var mainUrl = "https://watchmmafull.com"
    override var name = "WatchMMAFull"
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "" to "Latest Fights",
        "/search/ufc" to "UFC",
        "/search/boxing" to "Boxing",
        "/search/pfl" to "PFL",
        "/search/bellator" to "Bellator",
        "/search/one" to "ONE Championship",
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val document = app.get("$mainUrl/search/$encoded").document
        return document.toSearchResults()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = when {
            request.data.isBlank() && page == 1 -> mainUrl
            request.data.isBlank() -> "$mainUrl/page/$page/"
            page == 1 -> "$mainUrl${request.data}"
            else -> "$mainUrl${request.data}/$page"
        }

        val document = app.get(targetUrl).document
        return newHomePageResponse(request.name, document.toSearchResults())
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = listOfNotNull(
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.selectFirst("h1")?.text(),
            document.selectFirst("title")?.text()
        ).firstOrNull { !it.isNullOrBlank() }?.cleanTitle() ?: return null

        val poster = listOfNotNull(
            document.selectFirst("meta[property=og:image]")?.attr("content"),
            document.selectFirst("img[src]")?.imageUrl()
        ).firstOrNull { !it.isNullOrBlank() }

        val plot = listOfNotNull(
            document.selectFirst("meta[name=description]")?.attr("content"),
            document.selectFirst("meta[property=og:description]")?.attr("content"),
            document.select("article p, .entry-content p, .post-content p").firstOrNull()?.text()
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        val tags = document.select("a[rel=tag], .post-tags a, .tags a, .cat-links a")
            .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
            .distinct()

        val recommendations = document.toSearchResults().take(20)

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster
            year = Regex("""(19|20)\d{2}""").find(title)?.value?.toIntOrNull()
            this.plot = plot
            this.tags = tags.ifEmpty { null }
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = mapOf("Referer" to "$mainUrl/")).document
        val html = document.html()
        val seen = linkedSetOf<String>()

        suspend fun register(rawUrl: String?) {
            val normalized = rawUrl.normalizeWatchUrl() ?: return
            if (!seen.add(normalized)) return

            if (normalized.endsWith(".vtt") || normalized.endsWith(".srt")) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        URI(normalized).host ?: "Subtitle",
                        normalized
                    )
                )
                return
            }

            val directMedia = normalized.contains(".m3u8") ||
                normalized.contains(".mp4") ||
                normalized.contains(".mpd")

            if (directMedia) {
                val type = when {
                    normalized.contains(".m3u8") -> ExtractorLinkType.M3U8
                    normalized.contains(".mpd") -> ExtractorLinkType.DASH
                    else -> ExtractorLinkType.VIDEO
                }
                callback.invoke(
                    newExtractorLink(
                        name,
                        "${name} ${URI(normalized).host ?: "stream"}",
                        normalized,
                        type
                    ) {
                        referer = data
                        quality = getQualityFromName(normalized)
                    }
                )
            } else {
                loadExtractor(normalized, data, subtitleCallback, callback)
            }
        }

        for (element in document.select(
            "iframe[src], iframe[data-src], video[src], video source[src], source[src], meta[itemprop=embedUrl]"
        )) {
            register(
                when {
                    element.hasAttr("src") -> element.attr("src")
                    element.hasAttr("data-src") -> element.attr("data-src")
                    else -> element.attr("content")
                }
            )
        }

        for (match in Regex("""https?:\\?/\\?/[^"'\\s<>()]+""").findAll(html)) {
            register(match.value)
        }

        return seen.isNotEmpty()
    }

    private fun Document.toSearchResults(): List<SearchResponse> {
        val seen = linkedSetOf<String>()
        return select("a[href]")
            .mapNotNull { anchor ->
                val href = anchor.attr("href").normalizeWatchUrl() ?: return@mapNotNull null
                if (!href.startsWith(mainUrl) || !href.endsWith(".html")) return@mapNotNull null
                if (!seen.add(href)) return@mapNotNull null

                val title = listOfNotNull(
                    anchor.attr("title"),
                    anchor.text(),
                    anchor.selectFirst("img")?.attr("alt"),
                    anchor.parent()?.selectFirst("img")?.attr("alt")
                ).firstOrNull { !it.isNullOrBlank() }?.cleanTitle() ?: return@mapNotNull null

                val poster = anchor.selectFirst("img")?.imageUrl()
                    ?: anchor.parent()?.selectFirst("img")?.imageUrl()

                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                }
            }
    }

    private fun Element.imageUrl(): String? {
        return listOf("data-src", "data-lazy-src", "src")
            .mapNotNull { attr ->
                this.attr(attr).takeIf { it.isNotBlank() }?.normalizeWatchUrl()
            }
            .firstOrNull()
    }

    private fun String?.normalizeWatchUrl(): String? {
        if (this.isNullOrBlank()) return null
        val cleaned = this
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u002F", "/")
            .replace("\\u003A", ":")
            .trim()
            .trim('"', '\'')
        return fixUrlNull(cleaned)
    }

    private fun String.cleanTitle(): String {
        return replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*[|»-].*$"""), "")
            .trim()
    }
}
