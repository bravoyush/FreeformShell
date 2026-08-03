package com.example.freeformshell

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EditTilesSubPanel(
    activeTiles: List<String>,
    onUpdateActiveTiles: (List<String>) -> Unit,
    availableTiles: List<QSAvailableTile>,
    onBack: () -> Unit,
    cardBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    inactiveTileBg: Color,
    context: Context,
    draftLayouts: SnapshotStateList<TileLayout>,
    selectedKey: String?,
    onSelectedKeyChange: (String?) -> Unit,
    draggingKey: String?,
    onDraggingKeyChange: (String?) -> Unit,
    dragOffset: Offset,
    onDragOffsetChange: (Offset) -> Unit,
    resizingKey: String?,
    onResizingKeyChange: (String?) -> Unit,
    currentPageIndex: Int,
    onCurrentPageIndexChange: (Int) -> Unit
) {
    val isDark = cardBg.red < 0.5f
    
    var resizeStartW by remember { mutableStateOf(2) }
    var resizeStartH by remember { mutableStateOf(1) }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val cardWidth = when {
        screenWidthDp < 360 -> (screenWidthDp - 24).dp
        screenWidthDp < 480 -> (screenWidthDp * 0.88f).dp
        screenWidthDp < 720 -> 380.dp
        else -> 420.dp
    }
    val gap = 8.dp
    val calculatedCellWidth = (cardWidth - 40.dp - gap * 3) / 4
    val cellHeight = (calculatedCellWidth * 0.65f).coerceIn(48.dp, 60.dp)
    val density = LocalDensity.current.density

    val maxRowIdx = draftLayouts.maxOfOrNull { it.row + it.h } ?: 0
    // Keep a fixed height representing a 5-row page size limit
    val gridRows = 5
    val totalHeight = cellHeight * gridRows + gap * (gridRows - 1)
    
    // Calculate pages (always show at least 2 pages in edit mode)
    val maxPage = draftLayouts.maxOfOrNull { it.page } ?: 0
    val pageCount = (maxPage + 1).coerceAtLeast(2)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { pageCount }
    )
    val scope = rememberCoroutineScope()
    
    // Sync external page state with pager
    LaunchedEffect(pagerState.currentPage) {
        onCurrentPageIndexChange(pagerState.currentPage)
    }
    
    // Sync external change to pager
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex && currentPageIndex < pageCount) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    // Checking if the page is full (no room for even a 1x1 tile on current page)
    val isPageFull = remember(draftLayouts, pagerState.currentPage) {
        findFirstAvailableSlot(1, 1, pagerState.currentPage, draftLayouts) == null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Done / Cancel Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { onBack() }) {
                Text(
                    "Cancel",
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                "Arrange Tiles",
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = {
                ThemeManager.setTileLayouts(context, draftLayouts.toList())
                onUpdateActiveTiles(draftLayouts.map { it.key })
                onBack()
            }) {
                Text(
                    "Done",
                    color = activeTileBg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Overflow Suggestion Banner
        if (isPageFull) {
            val unassignedTileOnThisPage = draftLayouts.firstOrNull { it.page == pagerState.currentPage }
            Card(
                colors = CardDefaults.cardColors(containerColor = activeTileBg.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, activeTileBg.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = activeTileBg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Page ${pagerState.currentPage + 1} is full!",
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Move tiles to Page ${if (pagerState.currentPage == 0) "2" else "1"} to free up space.",
                            color = contentColor.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                    if (unassignedTileOnThisPage != null) {
                        val targetPage = if (pagerState.currentPage == 0) 1 else 0
                        TextButton(
                            onClick = {
                                val tile = unassignedTileOnThisPage
                                val slot = findFirstAvailableSlot(tile.w, tile.h, targetPage, draftLayouts)
                                if (slot != null) {
                                    val idx = draftLayouts.indexOfFirst { it.key == tile.key }
                                    if (idx >= 0) {
                                        draftLayouts[idx] = tile.copy(col = slot.first, row = slot.second, page = targetPage)
                                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                                    }
                                } else {
                                    Toast.makeText(context, "No space on Page ${targetPage + 1}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Move Tile", color = activeTileBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Grid Pages",
                color = activeTileBg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            // Page text indicator
            Text(
                text = "Page ${pagerState.currentPage + 1} of $pageCount",
                color = contentColor.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cellWidth = (maxWidth - gap * 3) / 4
            
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(totalHeight)
            ) { pageIndex ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Dashed Border Empty Slots
                    val occupiedCells = mutableSetOf<Pair<Int, Int>>()
                    draftLayouts.filter { it.page == pageIndex }.forEach { layout ->
                        for (c in 0 until layout.w) {
                            for (r in 0 until layout.h) {
                                occupiedCells.add(Pair(layout.col + c, layout.row + r))
                            }
                        }
                    }
                    
                    for (r in 0 until gridRows) {
                        for (c in 0..3) {
                            if (!occupiedCells.contains(Pair(c, r))) {
                                val x = (cellWidth + gap) * c
                                val y = (cellHeight + gap) * r
                                
                                Box(
                                    modifier = Modifier
                                        .offset(x = x, y = y)
                                        .size(width = cellWidth, height = cellHeight)
                                        .drawBehind {
                                            val stroke = Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                            )
                                            drawRoundRect(
                                                color = contentColor.copy(alpha = 0.12f),
                                                cornerRadius = CornerRadius(12.dp.toPx()),
                                                style = stroke
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = contentColor.copy(alpha = 0.1f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Dragging Preview block
                    if (draggingKey != null) {
                        val layout = draftLayouts.firstOrNull { it.key == draggingKey }
                        if (layout != null && layout.page == pageIndex) {
                            val cellW = cellWidth.value * density
                            val cellH = cellHeight.value * density
                            val gapPx = gap.value * density
                            val dragX = layout.col * (cellW + gapPx) + dragOffset.x
                            val dragY = layout.row * (cellH + gapPx) + dragOffset.y
                            val targetCol = ((dragX + cellW / 2) / (cellW + gapPx)).roundToInt().coerceIn(0, 4 - layout.w)
                            val targetRow = ((dragY + cellH / 2) / (cellH + gapPx)).roundToInt().coerceIn(0, gridRows - layout.h)
                            
                            val testLayout = layout.copy(col = targetCol, row = targetRow)
                            val overlapping = draftLayouts.filter { other ->
                                other.key != layout.key && other.page == pageIndex && intersects(testLayout, other)
                            }
                            
                            val isValid = overlapping.isEmpty() || (overlapping.size == 1 && overlapping[0].w == layout.w && overlapping[0].h == layout.h)
                            val previewColor = if (!isValid) Color.Red.copy(alpha = 0.2f) else activeTileBg.copy(alpha = 0.15f)
                            val borderColor = if (!isValid) Color.Red.copy(alpha = 0.7f) else activeTileBg.copy(alpha = 0.6f)
                            
                            val wPx = cellWidth * layout.w + gap * (layout.w - 1)
                            val hPx = cellHeight * layout.h + gap * (layout.h - 1)
                            
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (cellWidth + gap) * targetCol,
                                        y = (cellHeight + gap) * targetRow
                                    )
                                    .size(width = wPx, height = hPx)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(previewColor)
                                    .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                            )
                        }
                    }

                    // 3. Render Active tiles
                    draftLayouts.filter { it.page == pageIndex }.forEach { layout ->
                        EditTileItem(
                            layout = layout,
                            selectedKey = selectedKey,
                            onSelectedKeyChange = onSelectedKeyChange,
                            draggingKey = draggingKey,
                            onDraggingKeyChange = onDraggingKeyChange,
                            dragOffset = dragOffset,
                            onDragOffsetChange = onDragOffsetChange,
                            resizingKey = resizingKey,
                            onResizingKeyChange = onResizingKeyChange,
                            pageIndex = pageIndex,
                            availableTiles = availableTiles,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            gap = gap,
                            gridRows = gridRows,
                            density = density,
                            draftLayouts = draftLayouts,
                            context = context,
                            activeTileBg = activeTileBg,
                            isDark = isDark,
                            scope = scope,
                            pagerState = pagerState
                        )
                    }

                    // 6. Floating toolbar for selected tile (on top of current page grid)
                    selectedKey?.let { selKey ->
                        val layout = draftLayouts.firstOrNull { it.key == selKey }
                        if (layout != null && layout.page == pageIndex) {
                            val wPx = cellWidth * layout.w + gap * (layout.w - 1)
                            val hPx = cellHeight * layout.h + gap * (layout.h - 1)
                            
                            val x = (cellWidth + gap) * layout.col
                            val y = (cellHeight + gap) * layout.row
                            
                            val toolbarW = 190.dp
                            val itemW = wPx
                            val toolbarX = x + (itemW - toolbarW) / 2
                            val toolbarY = if (layout.row == 0) {
                                y + hPx + 6.dp
                            } else {
                                y - 44.dp
                            }
                            
                            Box(
                                modifier = Modifier
                                    .offset(x = toolbarX, y = toolbarY)
                                    .size(width = toolbarW, height = 36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF2C2C2C))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                                    .clickable(enabled = false) {},
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Delete button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935).copy(alpha = 0.9f))
                                            .clickable {
                                                draftLayouts.removeAt(draftLayouts.indexOfFirst { it.key == layout.key })
                                                onSelectedKeyChange(null)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                    
                                    // Size presets
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .padding(1.dp)
                                    ) {
                                        listOf(Pair(1, 1), Pair(2, 1), Pair(2, 2), Pair(4, 1)).forEach { (wChoice, hChoice) ->
                                            val isCurrent = layout.w == wChoice && layout.h == hChoice
                                            val label = when {
                                                wChoice == 1 && hChoice == 1 -> "1x1"
                                                wChoice == 2 && hChoice == 1 -> "2x1"
                                                wChoice == 2 && hChoice == 2 -> "2x2"
                                                wChoice == 4 && hChoice == 1 -> "4x1"
                                                else -> "${wChoice}x${hChoice}"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isCurrent) activeTileBg else Color.Transparent)
                                                    .clickable {
                                                        val testLayout = layout.copy(w = wChoice, h = hChoice)
                                                        if (testLayout.col + testLayout.w <= 4) {
                                                            val hasOverlap = draftLayouts.any { other ->
                                                                other.key != layout.key && other.page == pageIndex && intersects(testLayout, other)
                                                            }
                                                            if (!hasOverlap) {
                                                                val idx = draftLayouts.indexOfFirst { it.key == layout.key }
                                                                if (idx >= 0) {
                                                                    draftLayouts[idx] = testLayout
                                                                }
                                                            } else {
                                                                val foundPos = findValidPosition(layout.key, wChoice, hChoice, pageIndex, draftLayouts)
                                                                if (foundPos != null) {
                                                                    val idx = draftLayouts.indexOfFirst { it.key == layout.key }
                                                                    if (idx >= 0) {
                                                                        draftLayouts[idx] = layout.copy(
                                                                            col = foundPos.first,
                                                                            row = foundPos.second,
                                                                            w = wChoice,
                                                                            h = hChoice
                                                                        )
                                                                    }
                                                                } else {
                                                                    Toast.makeText(context, "No space for $label", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(label, color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    
                                    // Move to next/prev page button in toolbar
                                    val otherPage = if (pageIndex == 0) 1 else 0
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(activeTileBg)
                                            .clickable {
                                                val slot = findFirstAvailableSlot(layout.w, layout.h, otherPage, draftLayouts)
                                                if (slot != null) {
                                                    val idx = draftLayouts.indexOfFirst { it.key == layout.key }
                                                    if (idx >= 0) {
                                                        draftLayouts[idx] = layout.copy(col = slot.first, row = slot.second, page = otherPage)
                                                        onSelectedKeyChange(null)
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(otherPage)
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Page ${otherPage + 1} is full!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (pageIndex == 0) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                                            contentDescription = "Move Page",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Floating toolbar for selected tile (on top of current page grid)
                    selectedKey?.let { selKey ->
                        val layout = draftLayouts.firstOrNull { it.key == selKey }
                        if (layout != null && layout.page == pageIndex) {
                            val x = (cellWidth + gap) * layout.col
                            val y = (cellHeight + gap) * layout.row
                            val wPx = cellWidth * layout.w + gap * (layout.w - 1)
                            val hPx = cellHeight * layout.h + gap * (layout.h - 1)
                            
                            val toolbarW = 190.dp
                            val toolbarX = x + (wPx - toolbarW) / 2
                            val toolbarY = if (layout.row == 0) {
                                y + hPx + 6.dp
                            } else {
                                y - 44.dp
                            }
                            
                            Box(
                                modifier = Modifier
                                    .offset(x = toolbarX, y = toolbarY)
                                    .size(width = toolbarW, height = 36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF2C2C2C))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                                    .clickable(enabled = false) {},
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Delete button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935).copy(alpha = 0.9f))
                                            .clickable {
                                                draftLayouts.removeAt(draftLayouts.indexOfFirst { it.key == layout.key })
                                                onSelectedKeyChange(null)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                    
                                    // Size presets
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .padding(1.dp)
                                    ) {
                                        listOf(Pair(1, 1), Pair(2, 1), Pair(2, 2), Pair(4, 1)).forEach { (wChoice, hChoice) ->
                                            val isCurrent = layout.w == wChoice && layout.h == hChoice
                                            val label = when {
                                                wChoice == 1 && hChoice == 1 -> "1x1"
                                                wChoice == 2 && hChoice == 1 -> "2x1"
                                                wChoice == 2 && hChoice == 2 -> "2x2"
                                                wChoice == 4 && hChoice == 1 -> "4x1"
                                                else -> "${wChoice}x${hChoice}"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isCurrent) activeTileBg else Color.Transparent)
                                                    .clickable {
                                                        val testLayout = layout.copy(w = wChoice, h = hChoice)
                                                        if (testLayout.col + testLayout.w <= 4) {
                                                            val hasOverlap = draftLayouts.any { other ->
                                                                other.key != layout.key && other.page == pageIndex && intersects(testLayout, other)
                                                            }
                                                            if (!hasOverlap) {
                                                                val idx = draftLayouts.indexOfFirst { it.key == layout.key }
                                                                if (idx >= 0) {
                                                                    draftLayouts[idx] = testLayout
                                                                }
                                                            } else {
                                                                val foundPos = findValidPosition(layout.key, wChoice, hChoice, pageIndex, draftLayouts)
                                                                if (foundPos != null) {
                                                                    val idx = draftLayouts.indexOfFirst { it.key == layout.key }
                                                                    if (idx >= 0) {
                                                                        draftLayouts[idx] = layout.copy(
                                                                            col = foundPos.first,
                                                                            row = foundPos.second,
                                                                            w = wChoice,
                                                                            h = hChoice
                                                                        )
                                                                    }
                                                                } else {
                                                                    Toast.makeText(context, "No space for $label", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(label, color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    
                                    // Move to next/prev page button in toolbar
                                    val otherPage = if (pageIndex == 0) 1 else 0
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(activeTileBg)
                                            .clickable {
                                                val slot = findFirstAvailableSlot(layout.w, layout.h, otherPage, draftLayouts)
                                                if (slot != null) {
                                                    val idx = draftLayouts.indexOfFirst { it.key == layout.key }
                                                    if (idx >= 0) {
                                                        draftLayouts[idx] = layout.copy(col = slot.first, row = slot.second, page = otherPage)
                                                        onSelectedKeyChange(null)
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(otherPage)
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Page ${otherPage + 1} is full!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (pageIndex == 0) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                                            contentDescription = "Move Page",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Page Dot Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isSelected) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) activeTileBg else contentColor.copy(alpha = 0.4f))
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        
        // Reset to Defaults
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Reset to defaults",
                color = contentColor.copy(alpha = 0.38f),
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        draftLayouts.clear()
                        draftLayouts.addAll(ThemeManager.DEFAULT_TILE_LAYOUTS)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun TileLibraryCard(
    availableTiles: List<QSAvailableTile>,
    draftLayouts: SnapshotStateList<TileLayout>,
    currentPageIndex: Int,
    onNavigateToPage: (Int) -> Unit,
    cardBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    inactiveTileBg: Color,
    cardWidth: Dp,
    cardHeight: Dp,
    getLocalPos: (LayoutCoordinates) -> Offset,
    onLibraryDragStart: (QSAvailableTile, Offset) -> Unit,
    onLibraryDragUpdate: (Offset) -> Unit,
    onLibraryDragEnd: (QSAvailableTile?, Offset) -> Unit,
    onInputActiveChanged: (Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Tile Library",
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tiles...", fontSize = 12.sp, color = contentColor.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = contentColor.copy(alpha = 0.4f), modifier = Modifier.size(16.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .onFocusChanged { focusState ->
                        onInputActiveChanged(focusState.isFocused)
                    },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = activeTileBg,
                    unfocusedBorderColor = contentColor.copy(alpha = 0.12f),
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor
                ),
                singleLine = true
            )
            
            Spacer(Modifier.height(12.dp))
            
            val activeKeys = remember(draftLayouts) { draftLayouts.map { it.key } }
            val inactiveList = availableTiles.filter { it.key !in activeKeys }
            val filteredList = inactiveList.filter { it.label.contains(searchQuery, ignoreCase = true) }
            
            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isEmpty()) "All tiles are active." else "No matching tiles found.",
                        color = contentColor.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.key }) { tileInfo ->
                        var dragOffset by remember { mutableStateOf(Offset.Zero) }
                        var isDraggingThis by remember { mutableStateOf(false) }
                        var tileCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .onGloballyPositioned { coords ->
                                    tileCoordinates = coords
                                }
                                .clip(RoundedCornerShape(21.dp))
                                .background(inactiveTileBg.copy(alpha = 0.55f))
                                .pointerInput(tileInfo.key) {
                                    detectDragGestures(
                                        onDragStart = {
                                            isDraggingThis = true
                                            dragOffset = Offset.Zero
                                            val startPos = tileCoordinates?.let { getLocalPos(it) } ?: Offset.Zero
                                            onLibraryDragStart(tileInfo, startPos)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                            onLibraryDragUpdate(dragOffset)
                                        },
                                        onDragEnd = {
                                            isDraggingThis = false
                                            onLibraryDragEnd(tileInfo, dragOffset)
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            isDraggingThis = false
                                            onLibraryDragEnd(null, Offset.Zero)
                                            dragOffset = Offset.Zero
                                        }
                                    )
                                }
                                .clickable {
                                    // Click auto-add to current page (or next page if full)
                                    val defaultW = if (tileInfo.key == "music") 4 else 2
                                    val defaultH = 1
                                    
                                    val slotOnCurrent = findFirstAvailableSlot(defaultW, defaultH, currentPageIndex, draftLayouts)
                                    if (slotOnCurrent != null) {
                                        draftLayouts.add(TileLayout(tileInfo.key, slotOnCurrent.first, slotOnCurrent.second, defaultW, defaultH, currentPageIndex))
                                    } else {
                                        val otherPage = if (currentPageIndex == 0) 1 else 0
                                        val slotOnOther = findFirstAvailableSlot(defaultW, defaultH, otherPage, draftLayouts)
                                        if (slotOnOther != null) {
                                            draftLayouts.add(TileLayout(tileInfo.key, slotOnOther.first, slotOnOther.second, defaultW, defaultH, otherPage))
                                            onNavigateToPage(otherPage)
                                            Toast.makeText(context, "Page ${currentPageIndex + 1} is full. Added to Page ${otherPage + 1}.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No space available on any page!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .height(42.dp)
                                .graphicsLayer {
                                    alpha = if (isDraggingThis) 0.5f else 1f
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(activeTileBg.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tileInfo.customIcon != null) {
                                    Image(
                                        bitmap = tileInfo.customIcon,
                                        contentDescription = tileInfo.label,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = tileInfo.icon ?: Icons.Default.Settings,
                                        contentDescription = tileInfo.label,
                                        tint = activeTileBg,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tileInfo.label,
                                color = contentColor.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add",
                                tint = activeTileBg,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared Layout Helpers ───────────────────────────────────────────────────

fun intersects(a: TileLayout, b: TileLayout): Boolean {
    return a.page == b.page &&
           a.col < b.col + b.w && a.col + a.w > b.col &&
           a.row < b.row + b.h && a.row + a.h > b.row
}

fun findFirstAvailableSlot(w: Int, h: Int, page: Int, layouts: List<TileLayout>): Pair<Int, Int>? {
    val occupied = mutableSetOf<Pair<Int, Int>>()
    layouts.filter { it.page == page }.forEach { layout ->
        for (c in 0 until layout.w) {
            for (r in 0 until layout.h) {
                occupied.add(Pair(layout.col + c, layout.row + r))
            }
        }
    }
    // Limit grid height in page to 5 rows
    for (r in 0..5 - h) {
        for (c in 0..4 - w) {
            var fits = true
            for (sw in 0 until w) {
                for (sh in 0 until h) {
                    if (occupied.contains(Pair(c + sw, r + sh))) {
                        fits = false
                        break
                    }
                }
                if (!fits) break
            }
            if (fits) {
                return Pair(c, r)
            }
        }
    }
    return null
}

fun findValidPosition(excludeKey: String, w: Int, h: Int, page: Int, layouts: List<TileLayout>): Pair<Int, Int>? {
    val occupied = mutableSetOf<Pair<Int, Int>>()
    layouts.forEach { layout ->
        if (layout.key != excludeKey && layout.page == page) {
            for (c in 0 until layout.w) {
                for (r in 0 until layout.h) {
                    occupied.add(Pair(layout.col + c, layout.row + r))
                }
            }
        }
    }
    // Limit grid height in page to 5 rows
    for (r in 0..5 - h) {
        for (c in 0..4 - w) {
            var fits = true
            for (sw in 0 until w) {
                for (sh in 0 until h) {
                    if (occupied.contains(Pair(c + sw, r + sh))) {
                        fits = false
                        break
                    }
                }
                if (!fits) break
            }
            if (fits) {
                return Pair(c, r)
            }
        }
    }
    return null
}

fun finalizeTileMovement(
    layout: TileLayout,
    cellWidth: Dp,
    cellHeight: Dp,
    gap: Dp,
    pageIndex: Int,
    gridRows: Int,
    dragOffset: Offset,
    density: Float,
    draftLayouts: SnapshotStateList<TileLayout>
) {
    val cellW = cellWidth.value * density
    val cellH = cellHeight.value * density
    val gapPx = gap.value * density
    
    val dragX = layout.col * (cellW + gapPx) + dragOffset.x
    val dragY = layout.row * (cellH + gapPx) + dragOffset.y
    
    val targetCol = ((dragX + cellW / 2) / (cellW + gapPx)).roundToInt().coerceIn(0, 4 - layout.w)
    val targetRow = ((dragY + cellH / 2) / (cellH + gapPx)).roundToInt().coerceIn(0, gridRows - layout.h)
    
    val testLayout = layout.copy(col = targetCol, row = targetRow, page = pageIndex)
    
    val overlappingTiles = draftLayouts.filter { other ->
        other.key != layout.key && other.page == pageIndex && intersects(testLayout, other)
    }
    
    if (overlappingTiles.isEmpty()) {
        val idx = draftLayouts.indexOfFirst { it.key == layout.key }
        if (idx >= 0) {
            draftLayouts[idx] = testLayout
        }
    } else if (overlappingTiles.size == 1 && overlappingTiles[0].w == layout.w && overlappingTiles[0].h == layout.h) {
        val targetTile = overlappingTiles[0]
        val idxThis = draftLayouts.indexOfFirst { it.key == layout.key }
        val idxTarget = draftLayouts.indexOfFirst { it.key == targetTile.key }
        if (idxThis >= 0 && idxTarget >= 0) {
            draftLayouts[idxThis] = layout.copy(col = targetTile.col, row = targetTile.row, page = pageIndex)
            draftLayouts[idxTarget] = targetTile.copy(col = layout.col, row = layout.row, page = pageIndex)
        }
    }
}

fun Modifier.drawCornerBrackets(
    color: Color,
    radius: Float,
    strokeWidth: Float,
    length: Float
): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    
    // Top-Left
    val pathTL = Path().apply {
        moveTo(0f, length)
        lineTo(0f, radius)
        quadraticBezierTo(0f, 0f, radius, 0f)
        lineTo(length, 0f)
    }
    drawPath(
        path = pathTL,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    
    // Top-Right
    val pathTR = Path().apply {
        moveTo(w - length, 0f)
        lineTo(w - radius, 0f)
        quadraticBezierTo(w, 0f, w, radius)
        lineTo(w, length)
    }
    drawPath(
        path = pathTR,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    
    // Bottom-Left
    val pathBL = Path().apply {
        moveTo(0f, h - length)
        lineTo(0f, h - radius)
        quadraticBezierTo(0f, h, radius, h)
        lineTo(length, h)
    }
    drawPath(
        path = pathBL,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    
    // Bottom-Right
    val pathBR = Path().apply {
        moveTo(w - length, h)
        lineTo(w - radius, h)
        quadraticBezierTo(w, h, w, h - radius)
        lineTo(w, h - length)
    }
    drawPath(
        path = pathBR,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EditTileItem(
    layout: TileLayout,
    selectedKey: String?,
    onSelectedKeyChange: (String?) -> Unit,
    draggingKey: String?,
    onDraggingKeyChange: (String?) -> Unit,
    dragOffset: Offset,
    onDragOffsetChange: (Offset) -> Unit,
    resizingKey: String?,
    onResizingKeyChange: (String?) -> Unit,
    pageIndex: Int,
    availableTiles: List<QSAvailableTile>,
    cellWidth: Dp,
    cellHeight: Dp,
    gap: Dp,
    gridRows: Int,
    density: Float,
    draftLayouts: SnapshotStateList<TileLayout>,
    context: Context,
    activeTileBg: Color,
    isDark: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    pagerState: androidx.compose.foundation.pager.PagerState
) {
    val latestLayout by rememberUpdatedState(layout)
    val latestDragOffset by rememberUpdatedState(dragOffset)
    val latestSelectedKey by rememberUpdatedState(selectedKey)
    val latestDraggingKey by rememberUpdatedState(draggingKey)
    val latestResizingKey by rememberUpdatedState(resizingKey)

    val isSelected = latestSelectedKey == latestLayout.key
    val isDraggingThis = latestDraggingKey == latestLayout.key
    val isResizingThis = latestResizingKey == latestLayout.key

    val wPx = cellWidth * latestLayout.w + gap * (latestLayout.w - 1)
    val hPx = cellHeight * latestLayout.h + gap * (latestLayout.h - 1)

    val itemWidth = wPx
    val itemHeight = hPx

    val x = (cellWidth + gap) * latestLayout.col
    val y = (cellHeight + gap) * latestLayout.row

    val finalDragX = if (isDraggingThis) latestDragOffset.x else 0f
    val finalDragY = if (isDraggingThis) latestDragOffset.y else 0f

    var resizeStartW by remember { mutableStateOf(2) }
    var resizeStartH by remember { mutableStateOf(1) }

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width = itemWidth, height = itemHeight)
            .graphicsLayer {
                translationX = finalDragX
                translationY = finalDragY
                alpha = if (isDraggingThis) 0.5f else 1f
                shadowElevation = if (isDraggingThis) 8.dp.toPx() else 0f
            }
            .then(
                if (isSelected || isDraggingThis || isResizingThis) {
                    Modifier
                        .border(1.5.dp, activeTileBg, RoundedCornerShape(30.dp))
                        .drawCornerBrackets(
                            color = activeTileBg,
                            radius = 30.dp.value * density,
                            strokeWidth = 2.5.dp.value * density,
                            length = 38.dp.value * density
                        )
                } else Modifier
            )
            .pointerInput(latestLayout.key) {
                detectTapGestures(
                    onTap = {
                        onSelectedKeyChange(if (latestSelectedKey == latestLayout.key) null else latestLayout.key)
                    }
                )
            }
            .pointerInput(latestLayout.key) {
                detectDragGestures(
                    onDragStart = {
                        onDraggingKeyChange(latestLayout.key)
                        onDragOffsetChange(Offset.Zero)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragOffsetChange(latestDragOffset + dragAmount)
                    },
                    onDragEnd = {
                        val dist = latestDragOffset.getDistance()
                        val maxClickDistance = 24f * density
                        if (dist < maxClickDistance) {
                            onSelectedKeyChange(if (latestSelectedKey == latestLayout.key) null else latestLayout.key)
                            onDraggingKeyChange(null)
                            onDragOffsetChange(Offset.Zero)
                        } else {
                            finalizeTileMovement(latestLayout, cellWidth, cellHeight, gap, pageIndex, gridRows, latestDragOffset, density, draftLayouts)
                            onDraggingKeyChange(null)
                            onDragOffsetChange(Offset.Zero)
                        }
                    },
                    onDragCancel = {
                        onDraggingKeyChange(null)
                        onDragOffsetChange(Offset.Zero)
                    }
                )
            }
    ) {
        CompositionLocalProvider(LocalTileSize provides Pair(latestLayout.w, latestLayout.h)) {
            RenderQSGridTile(
                tileKey = latestLayout.key,
                isDark = isDark,
                isWifiOn = false,
                wifiSsid = "",
                isBtOn = false,
                btDeviceName = "",
                currentRingerMode = 2,
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
                isFlashlightOn = false,
                toggleFlashlight = {},
                isAutoRotateOn = false,
                toggleAutoRotate = {},
                isRecording = false,
                recordingDuration = 0,
                toggleScreenRecord = {},
                isDndOn = false,
                toggleDnd = {},
                isHotspotOn = false,
                toggleHotspot = {},
                isAirplaneModeOn = false,
                toggleAirplaneMode = {},
                isLocationOn = false,
                toggleLocation = {},
                isExtraDimOn = false,
                toggleExtraDim = {},
                isWirelessDebugOn = false,
                toggleWirelessDebug = {},
                isNfcOn = false,
                toggleNfc = {},
                thirdPartyTiles = emptyList(),
                onDismiss = {},
                launchOptions = android.os.Bundle(),
                onShowWifiListDetail = {},
                onShowBtListDetail = {},
                displayId = 0,
                isEditMode = true // disable clicks
            )
        }

        // 4. Circular minus remove button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF29B6F6)) // light blue
                .clickable {
                    draftLayouts.removeAt(draftLayouts.indexOfFirst { it.key == latestLayout.key })
                    onSelectedKeyChange(null)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Remove",
                tint = Color.Black,
                modifier = Modifier.size(12.dp)
            )
        }

        // 5. Bottom-Right Resize Handle
        if (isSelected && latestDraggingKey == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(activeTileBg)
                    .pointerInput(latestLayout.key) {
                        detectDragGestures(
                            onDragStart = {
                                onResizingKeyChange(latestLayout.key)
                                resizeStartW = latestLayout.w
                                resizeStartH = latestLayout.h
                                onDragOffsetChange(Offset.Zero)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragOffsetChange(latestDragOffset + dragAmount)

                                val cellW = cellWidth.value * density
                                val cellH = cellHeight.value * density
                                val gapPx = gap.value * density

                                val colDelta = (latestDragOffset.x / (cellW + gapPx)).roundToInt()
                                val rowDelta = (latestDragOffset.y / (cellH + gapPx)).roundToInt()

                                val targetW = (resizeStartW + colDelta).coerceIn(1, 4 - latestLayout.col)
                                val targetH = (resizeStartH + rowDelta).coerceIn(1, 2)

                                val testLayout = latestLayout.copy(w = targetW, h = targetH)
                                val hasOverlap = draftLayouts.any { other ->
                                    other.key != latestLayout.key && other.page == pageIndex && intersects(testLayout, other)
                                }

                                if (!hasOverlap) {
                                    val idx = draftLayouts.indexOfFirst { it.key == latestLayout.key }
                                    if (idx >= 0) {
                                        draftLayouts[idx] = testLayout
                                    }
                                }
                            },
                            onDragEnd = {
                                onResizingKeyChange(null)
                                onDragOffsetChange(Offset.Zero)
                            },
                            onDragCancel = {
                                onResizingKeyChange(null)
                                onDragOffsetChange(Offset.Zero)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Resize",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

