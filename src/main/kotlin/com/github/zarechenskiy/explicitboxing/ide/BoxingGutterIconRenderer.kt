package com.github.zarechenskiy.explicitboxing.ide

import com.github.zarechenskiy.explicitboxing.BoxingBundle
import com.github.zarechenskiy.explicitboxing.bytecode.BoxingSite
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import javax.swing.Icon

class BoxingGutterIconRenderer(
    private val line: Int,
    private val sites: List<BoxingSite>,
    private val outdated: Boolean,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = if (outdated) OUTDATED_ICON else ICON

    override fun getAccessibleName(): String = BoxingBundle["gutter.accessible.name"]

    override fun getTooltipText(): String {
        val builder = HtmlBuilder()
            .append(HtmlChunk.text(BoxingBundle["gutter.tooltip.title", line]).bold())

        val groups = sites.groupBy { Triple(it.description, it.method, it.inlinedFrom) }.values
        for (group in groups) {
            val site = group.first()
            val details = buildList {
                add(site.call)
                add(BoxingBundle["gutter.tooltip.site.in", site.method])
                site.inlinedFrom?.let { add(BoxingBundle["gutter.tooltip.site.inlined", it]) }
            }.joinToString(", ")

            builder.br()
                .append("• ")
                .append(HtmlChunk.text(site.description).code())
                .append(if (group.size > 1) " ×${group.size}" else "")
                .append(HtmlChunk.text(" ($details)").wrapWith("span").style("color: gray"))
        }
        if (outdated) {
            builder.br().append(HtmlChunk.text(BoxingBundle["gutter.tooltip.outdated"]).italic())
        }
        return builder.wrapWithHtmlBody().toString()
    }

    override fun getAlignment(): Alignment = Alignment.RIGHT

    override fun isNavigateAction(): Boolean = false

    override fun equals(other: Any?): Boolean =
        other is BoxingGutterIconRenderer && other.line == line && other.sites == sites && other.outdated == outdated

    override fun hashCode(): Int = (line * 31 + sites.hashCode()) * 31 + outdated.hashCode()

    companion object {
        private val ICON: Icon = IconLoader.getIcon("/icons/boxing.svg", BoxingGutterIconRenderer::class.java)
        private val OUTDATED_ICON: Icon = IconLoader.getDisabledIcon(ICON)
    }
}
