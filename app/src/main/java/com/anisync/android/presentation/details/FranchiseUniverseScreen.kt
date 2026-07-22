package com.anisync.android.presentation.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anisync.android.R
import com.anisync.android.domain.FranchiseGraph
import com.anisync.android.domain.FranchiseGraphEdge
import com.anisync.android.domain.FranchiseGraphNode
import com.anisync.android.domain.insights
import com.anisync.android.domain.watchOrder
import com.anisync.android.presentation.components.AppCircularProgressIndicator
import kotlin.math.roundToInt

private enum class FranchiseMode(val titleRes: Int) {
    UNIVERSE(R.string.franchise_tab_universe),
    WATCH_ORDER(R.string.franchise_tab_watch_order),
    SCORE_TIMELINE(R.string.franchise_tab_score_timeline),
    INSIGHTS(R.string.franchise_tab_insights)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FranchiseUniverseScreen(
    mediaTitle: String,
    onBackClick: () -> Unit,
    onMediaClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FranchiseUniverseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var modeName by rememberSaveable { mutableStateOf(FranchiseMode.UNIVERSE.name) }
    val mode = FranchiseMode.valueOf(modeName)
    var resetViewportSignal by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.franchise_universe_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (mediaTitle.isNotBlank()) {
                            Text(
                                text = mediaTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    if (mode == FranchiseMode.UNIVERSE && state.graph != null) {
                        IconButton(onClick = { resetViewportSignal++ }) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = stringResource(R.string.franchise_recenter)
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.refresh(force = true) },
                        enabled = !state.isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = mode.ordinal,
                edgePadding = 8.dp,
                divider = {}
            ) {
                FranchiseMode.entries.forEach { tab ->
                    Tab(
                        selected = mode == tab,
                        onClick = { modeName = tab.name },
                        text = { Text(stringResource(tab.titleRes)) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    FranchiseMode.UNIVERSE -> Icons.Default.AccountTree
                                    FranchiseMode.WATCH_ORDER -> Icons.Default.Route
                                    FranchiseMode.SCORE_TIMELINE -> Icons.AutoMirrored.Filled.ShowChart
                                    FranchiseMode.INSIGHTS -> Icons.Default.Insights
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            if (state.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (state.graph?.isTruncated == true) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        text = stringResource(R.string.franchise_graph_incomplete),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            when {
                state.graph != null -> when (mode) {
                    FranchiseMode.UNIVERSE -> FranchiseGraphCanvas(
                        graph = state.graph!!,
                        resetViewportSignal = resetViewportSignal,
                        onMediaClick = onMediaClick,
                        modifier = Modifier.fillMaxSize()
                    )
                    FranchiseMode.WATCH_ORDER -> WatchOrderContent(
                        graph = state.graph!!,
                        onMediaClick = onMediaClick,
                        modifier = Modifier.fillMaxSize()
                    )
                    FranchiseMode.SCORE_TIMELINE -> ScoreTimelineContent(
                        graph = state.graph!!,
                        onMediaClick = onMediaClick,
                        modifier = Modifier.fillMaxSize()
                    )
                    FranchiseMode.INSIGHTS -> FranchiseInsightsContent(
                        graph = state.graph!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.isLoading || state.isRefreshing -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppCircularProgressIndicator()
                }
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.franchise_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class GraphPlacement(
    val node: FranchiseGraphNode,
    val x: Float,
    val y: Float,
    val layer: Int
)

private data class GraphLayout(
    val placements: List<GraphPlacement>,
    val width: Float,
    val height: Float
) {
    val byId: Map<Int, GraphPlacement> = placements.associateBy { it.node.mediaId }
}

@Composable
internal fun FranchiseGraphCanvas(
    graph: FranchiseGraph,
    resetViewportSignal: Int,
    onMediaClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var relationFilter by rememberSaveable { mutableStateOf("ALL") }
    val visibleEdges = remember(graph.edges, relationFilter) {
        if (relationFilter == "ALL") graph.edges else graph.edges.filter { it.relationType == relationFilter }
    }
    val visibleNodeIds = remember(graph.rootMediaId, visibleEdges, relationFilter) {
        if (relationFilter == "ALL") graph.nodes.mapTo(hashSetOf(), FranchiseGraphNode::mediaId)
        else buildSet {
            add(graph.rootMediaId)
            visibleEdges.forEach { edge -> add(edge.sourceMediaId); add(edge.targetMediaId) }
        }
    }
    val filteredGraph = remember(graph, visibleEdges, visibleNodeIds) {
        graph.copy(
            nodes = graph.nodes.filter { it.mediaId in visibleNodeIds },
            edges = visibleEdges
        )
    }
    val layout = remember(filteredGraph) { buildGraphLayout(filteredGraph) }
    val density = LocalDensity.current
    val initialTranslationX = with(density) { 12.dp.toPx() }
    val initialTranslationY = with(density) { 18.dp.toPx() }
    var scale by rememberSaveable(resetViewportSignal, density.density) { mutableFloatStateOf(0.72f) }
    var translationX by rememberSaveable(resetViewportSignal, density.density) {
        mutableFloatStateOf(initialTranslationX)
    }
    var translationY by rememberSaveable(resetViewportSignal, density.density) {
        mutableFloatStateOf(initialTranslationY)
    }
    var selectedId by rememberSaveable(filteredGraph.rootMediaId) {
        mutableStateOf<Int?>(filteredGraph.rootMediaId)
    }
    val selected = filteredGraph.nodes.firstOrNull { it.mediaId == selectedId }
    val filters = remember(graph.edges) {
        listOf("ALL") + graph.edges.map(FranchiseGraphEdge::relationType).distinct().sorted()
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val graphWidthPx = with(density) { layout.width.dp.toPx() }
    val graphHeightPx = with(density) { layout.height.dp.toPx() }

    fun clampViewport() {
        if (viewportSize == IntSize.Zero) return
        val clamped = clampGraphTranslation(
            translation = Offset(translationX, translationY),
            viewportWidth = viewportSize.width.toFloat(),
            viewportHeight = viewportSize.height.toFloat(),
            graphWidth = graphWidthPx,
            graphHeight = graphHeightPx,
            scale = scale
        )
        translationX = clamped.x
        translationY = clamped.y
    }

    LaunchedEffect(layout, viewportSize, scale, relationFilter, resetViewportSignal) {
        clampViewport()
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("franchise_relation_filters")
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = relationFilter == filter,
                        onClick = { relationFilter = filter },
                        label = {
                            Text(
                                if (filter == "ALL") stringResource(R.string.franchise_relation_all)
                                else relationLabel(filter)
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("franchise_graph_viewport")
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(filteredGraph, resetViewportSignal) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.38f, 1.55f)
                            translationX += pan.x
                            translationY += pan.y
                            clampViewport()
                        }
                    }
            ) {
                val edgeColors = relationColors()
                Box(
                    modifier = Modifier
                        .width(layout.width.dp)
                        .height(layout.height.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.translationX = translationX
                            this.translationY = translationY
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        filteredGraph.edges.forEach { edge ->
                            val from = layout.byId[edge.sourceMediaId] ?: return@forEach
                            val to = layout.byId[edge.targetMediaId] ?: return@forEach
                            val start = Offset(
                                x = (from.x + NODE_WIDTH / 2f).dp.toPx(),
                                y = (from.y + NODE_HEIGHT / 2f).dp.toPx()
                            )
                            val end = Offset(
                                x = (to.x + NODE_WIDTH / 2f).dp.toPx(),
                                y = (to.y + NODE_HEIGHT / 2f).dp.toPx()
                            )
                            val bend = (end.x - start.x) * 0.52f
                            val path = Path().apply {
                                moveTo(start.x, start.y)
                                cubicTo(start.x + bend, start.y, end.x - bend, end.y, end.x, end.y)
                            }
                            drawPath(
                                path = path,
                                color = edgeColors[edge.relationType] ?: edgeColors.getValue("OTHER"),
                                style = Stroke(width = 4f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    layout.placements.forEach { placement ->
                        val xPx = with(density) { placement.x.dp.roundToPx() }
                        val yPx = with(density) { placement.y.dp.roundToPx() }
                        FranchiseNodeCard(
                            node = placement.node,
                            relationDepth = placement.layer,
                            isRoot = placement.node.mediaId == graph.rootMediaId,
                            isSelected = placement.node.mediaId == selectedId,
                            onClick = { selectedId = placement.node.mediaId },
                            modifier = Modifier.offset {
                                IntOffset(xPx, yPx)
                            }
                        )
                    }
                }
            }

            RelationLegend(graph.edges)
        }

        if (selected != null) {
            SelectedNodePanel(
                node = selected,
                onOpen = { onMediaClick(selected.mediaId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 44.dp)
            )
        }
    }
}

internal fun clampGraphTranslation(
    translation: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    graphWidth: Float,
    graphHeight: Float,
    scale: Float
): Offset {
    val minX = (viewportWidth - graphWidth * scale).coerceAtMost(0f)
    val minY = (viewportHeight - graphHeight * scale).coerceAtMost(0f)
    return Offset(
        translation.x.coerceIn(minX, 0f),
        translation.y.coerceIn(minY, 0f)
    )
}

@Composable
private fun FranchiseNodeCard(
    node: FranchiseGraphNode,
    relationDepth: Int,
    isRoot: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation = if (isSelected) 18.dp else (10 - relationDepth.coerceIn(0, 7)).dp
    Card(
        modifier = modifier
            .width(NODE_WIDTH.dp)
            .height(NODE_HEIGHT.dp)
            .shadow(elevation, RoundedCornerShape(18.dp))
            .zIndex(if (isSelected) 1f else 0f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRoot -> MaterialTheme.colorScheme.primaryContainer
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = node.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = node.titleUserPreferred,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(node.format?.replace('_', ' '), node.startYear?.toString())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    node.averageScore?.let { score ->
                        Text(
                            text = "AL $score%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    node.listStatus?.let { status ->
                        Text(
                            text = status.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedNodePanel(
    node: FranchiseGraphNode,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.titleUserPreferred,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        node.format?.replace('_', ' '),
                        node.startYear?.toString(),
                        node.episodes?.let { "$it Ep" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.franchise_open_details))
            }
        }
    }
}

@Composable
private fun RelationLegend(edges: List<FranchiseGraphEdge>) {
    val colors = relationColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        edges.map(FranchiseGraphEdge::relationType).distinct().sorted().forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(colors[type] ?: colors.getValue("OTHER"))
                )
                Spacer(Modifier.width(5.dp))
                Text(relationLabel(type), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WatchOrderContent(
    graph: FranchiseGraph,
    onMediaClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var animeOnly by rememberSaveable { mutableStateOf(true) }
    val order = remember(graph, animeOnly) { graph.watchOrder(animeOnly) }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.franchise_watch_order_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FilterChip(
                selected = animeOnly,
                onClick = { animeOnly = !animeOnly },
                label = { Text(stringResource(R.string.franchise_anime_only)) }
            )
        }
        items(order, key = FranchiseGraphNode::mediaId) { node ->
            val number = order.indexOf(node) + 1
            Card(onClick = { onMediaClick(node.mediaId) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (node.mediaId == graph.rootMediaId) {
                            MaterialTheme.colorScheme.primary
                        } else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = number.toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (node.mediaId == graph.rootMediaId) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    AsyncImage(
                        model = node.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(52.dp)
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.titleUserPreferred,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(
                                node.startYear?.toString(),
                                node.format?.replace('_', ' '),
                                node.episodes?.let { stringResource(R.string.franchise_episode_count, it) }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (node.listStatus != null) {
                            Text(
                                text = "${node.listStatus.replace('_', ' ')} · ${node.listProgress ?: 0}/${node.episodes ?: "?"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    node.averageScore?.let { Text("$it%", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ScoreTimelineContent(
    graph: FranchiseGraph,
    onMediaClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes = remember(graph.nodes) {
        graph.nodes.filter { it.averageScore != null }.sortedWith(
            compareBy({ it.startYear ?: Int.MAX_VALUE }, { it.startMonth ?: 13 }, { it.mediaId })
        )
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.franchise_score_timeline_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (nodes.isNotEmpty()) {
            item { ScoreTimelineChart(nodes) }
        }
        items(nodes, key = FranchiseGraphNode::mediaId) { node ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onMediaClick(node.mediaId) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = node.startYear?.toString() ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = node.titleUserPreferred,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                Text("${node.averageScore}%", fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ScoreTimelineChart(nodes: List<FranchiseGraphNode>) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val chartDescription = stringResource(R.string.franchise_score_chart_a11y, nodes.size)
    Card(colors = CardDefaults.cardColors(containerColor = surface)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(24.dp)
                .semantics { contentDescription = chartDescription }
        ) {
            if (nodes.isEmpty()) return@Canvas
            val scores = nodes.map { it.averageScore!!.toFloat() }
            val minScore = (scores.minOrNull() ?: 0f).minus(5f).coerceAtLeast(0f)
            val maxScore = (scores.maxOrNull() ?: 100f).plus(5f).coerceAtMost(100f)
            val range = (maxScore - minScore).coerceAtLeast(1f)
            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(onSurface.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            val points = scores.mapIndexed { index, score ->
                val x = if (scores.size == 1) size.width / 2f else size.width * index / (scores.lastIndex.toFloat())
                val y = size.height - ((score - minScore) / range) * size.height
                Offset(x, y)
            }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, primary, style = Stroke(width = 6f, cap = StrokeCap.Round))
            }
            points.forEach { point ->
                drawCircle(surface, radius = 10f, center = point)
                drawCircle(primary, radius = 7f, center = point)
            }
        }
    }
}

@Composable
private fun FranchiseInsightsContent(graph: FranchiseGraph, modifier: Modifier = Modifier) {
    val insights = remember(graph) { graph.insights() }
    val completion = if (insights.animeNodes > 0) {
        insights.completedNodes.toFloat() / insights.animeNodes
    } else 0f
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    CompletionRing(completion)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.franchise_completion),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                R.string.franchise_completion_value,
                                insights.completedNodes,
                                insights.animeNodes
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.franchise_untouched_value, insights.untouchedNodes),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightMetric(
                    label = stringResource(R.string.franchise_total_entries),
                    value = insights.totalNodes.toString(),
                    modifier = Modifier.weight(1f)
                )
                InsightMetric(
                    label = stringResource(R.string.franchise_score_spread),
                    value = insights.scoreSpread?.let { "$it pts" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        insights.highestRated?.let { highest ->
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.franchise_highest_rated),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(highest.titleUserPreferred, fontWeight = FontWeight.Bold)
                        Text("AniList ${highest.averageScore}%")
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.franchise_relation_mix),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(insights.relationCounts.toList().sortedByDescending { it.second }) { (type, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(relationColors()[type] ?: relationColors().getValue("OTHER"))
                )
                Spacer(Modifier.width(10.dp))
                Text(relationLabel(type), modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(count.toString()) })
            }
        }
    }
}

@Composable
private fun CompletionRing(completion: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(track, -90f, 360f, false, style = Stroke(12f, cap = StrokeCap.Round))
            drawArc(primary, -90f, 360f * completion, false, style = Stroke(12f, cap = StrokeCap.Round))
        }
        Text("${(completion * 100).roundToInt()}%", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun InsightMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildGraphLayout(graph: FranchiseGraph): GraphLayout {
    if (graph.nodes.isEmpty()) return GraphLayout(emptyList(), 600f, 600f)
    val nodeIds = graph.nodes.mapTo(hashSetOf(), FranchiseGraphNode::mediaId)
    val layers = mutableMapOf(graph.rootMediaId to 0)
    val queue = ArrayDeque<Int>().apply { add(graph.rootMediaId) }
    while (queue.isNotEmpty()) {
        val source = queue.removeFirst()
        val currentLayer = layers.getValue(source)
        graph.edges.forEach { edge ->
            val next: Int
            val delta: Int
            when (source) {
                edge.sourceMediaId -> {
                    next = edge.targetMediaId
                    delta = relationLayerDelta(edge.relationType)
                }
                edge.targetMediaId -> {
                    next = edge.sourceMediaId
                    delta = -relationLayerDelta(edge.relationType)
                }
                else -> return@forEach
            }
            if (next in nodeIds && next !in layers) {
                layers[next] = currentLayer + delta
                queue.add(next)
            }
        }
    }
    graph.nodes.filterNot { it.mediaId in layers }.forEach { layers[it.mediaId] = 0 }
    val minLayer = layers.values.minOrNull() ?: 0
    val normalized = layers.mapValues { (_, value) -> value - minLayer }
    val grouped = graph.nodes.groupBy { normalized.getValue(it.mediaId) }
    val placements = buildList {
        grouped.toSortedMap().forEach { (layer, nodes) ->
            nodes.sortedWith(compareBy({ it.startYear ?: Int.MAX_VALUE }, { it.mediaId }))
                .forEachIndexed { index, node ->
                    add(
                        GraphPlacement(
                            node = node,
                            x = GRAPH_MARGIN + layer * LAYER_WIDTH,
                            y = GRAPH_MARGIN + index * LAYER_HEIGHT,
                            layer = layer
                        )
                    )
                }
        }
    }
    val width = (placements.maxOfOrNull { it.x } ?: 0f) + NODE_WIDTH + GRAPH_MARGIN
    val height = (placements.maxOfOrNull { it.y } ?: 0f) + NODE_HEIGHT + GRAPH_MARGIN + 160f
    return GraphLayout(placements, width.coerceAtLeast(720f), height.coerceAtLeast(720f))
}

private fun relationLayerDelta(type: String): Int = when (type) {
    "PREQUEL", "PARENT" -> -1
    "SEQUEL" -> 1
    "ALTERNATIVE", "SIDE_STORY", "SPIN_OFF", "CHARACTER" -> 0
    "ADAPTATION", "SOURCE" -> 0
    else -> 0
}

@Composable
private fun relationColors(): Map<String, Color> = mapOf(
    "PREQUEL" to Color(0xFF42A5F5),
    "SEQUEL" to Color(0xFF66BB6A),
    "ADAPTATION" to Color(0xFFAB47BC),
    "SOURCE" to Color(0xFF7E57C2),
    "SIDE_STORY" to Color(0xFFFFA726),
    "SPIN_OFF" to Color(0xFFEC407A),
    "ALTERNATIVE" to Color(0xFF26A69A),
    "OTHER" to MaterialTheme.colorScheme.outline
)

private fun relationLabel(type: String): String = type.lowercase()
    .replace('_', ' ')
    .replaceFirstChar { it.titlecase() }

private const val NODE_WIDTH = 235f
private const val NODE_HEIGHT = 126f
private const val LAYER_WIDTH = 330f
private const val LAYER_HEIGHT = 170f
private const val GRAPH_MARGIN = 80f
