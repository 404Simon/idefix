package dev.idefix.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.idefix.app.ui.theme.MonoStyle
import dev.idefix.app.util.Frontmatter
import dev.idefix.app.util.FrontmatterField
import dev.idefix.app.util.FrontmatterParser
import androidx.compose.ui.platform.LocalUriHandler as PlatformLocalUriHandler

@Composable
fun MarkdownPreview(
    content: String,
    onInternalLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialScroll: Int = 0,
    onScrollChanged: (Int) -> Unit = {},
) {
    val platformHandler = LocalUriHandler.current
    val currentOnInternalLink = rememberUpdatedState(onInternalLink)
    val scrollState = rememberScrollState(initial = initialScroll)
    val frontmatter = remember(content) { FrontmatterParser.parse(content) }
    val handler = remember(platformHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val lower = uri.lowercase()
                if (lower.startsWith("http://") || lower.startsWith("https://") ||
                    lower.startsWith("mailto:") || lower.startsWith("tel:")
                ) {
                    runCatching { platformHandler.openUri(uri) }
                } else {
                    currentOnInternalLink.value(uri)
                }
            }
        }
    }
    val markdownState = rememberMarkdownState(frontmatter?.body ?: content)
    val colors = markdownColor(
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
        tableBackground = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineMedium.copy(
            fontSize = 27.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        h2 = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        h3 = MaterialTheme.typography.titleLarge.copy(
            fontSize = 19.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        h4 = MaterialTheme.typography.titleMedium.copy(
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        h5 = MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        h6 = MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        text = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 26.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        paragraph = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 26.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        code = MonoStyle.copy(fontSize = 13.sp, lineHeight = 20.sp),
        inlineCode = MonoStyle.copy(fontSize = 13.sp, lineHeight = 20.sp),
        quote = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 25.sp,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        ordered = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
        bullet = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
        list = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
        table = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
        alertTitle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            if (frontmatter != null && frontmatter.fields.isNotEmpty()) {
                FrontmatterCard(frontmatter)
                Spacer(Modifier.height(4.dp))
            }
            CompositionLocalProvider(PlatformLocalUriHandler provides handler) {
                Markdown(
                    markdownState,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect(onScrollChanged)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrontmatterCard(frontmatter: Frontmatter) {
    var expanded by remember(frontmatter.raw) { mutableStateOf(true) }
    val tags = frontmatter.tags
    val detailFields = frontmatter.fields.filterNot {
        it.key.equals("tags", ignoreCase = true) || it.key.equals("tag", ignoreCase = true)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Frontmatter",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${frontmatter.fields.size} ${if (frontmatter.fields.size == 1) "field" else "fields"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse frontmatter" else "Expand frontmatter",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    tags.take(30).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            if (expanded && detailFields.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                detailFields.forEachIndexed { index, field ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    FrontmatterRow(field)
                }
            }
            if (expanded && detailFields.isEmpty() && tags.isEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Empty metadata block",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FrontmatterRow(field: FrontmatterField) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = field.key,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 92.dp, max = 124.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = field.displayValue,
            style = if (field.isLongValue) {
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            } else {
                MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (field.isLongValue) 1 else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
