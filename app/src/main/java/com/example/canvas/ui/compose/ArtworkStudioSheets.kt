package com.example.canvas.ui.compose

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.artwork.ArtworkRepository
import com.example.canvas.artwork.SavedArtwork
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberCardBorder
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.CyberSurfaceVariant
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.FaintText
import com.example.canvas.ui.theme.InkText
import com.example.canvas.ui.theme.MutedText
import com.example.canvas.ui.theme.NeonCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PaperBackgroundPresets = listOf(
    "Giấy trắng" to AndroidColor.WHITE,
    "Kem ấm" to AndroidColor.parseColor("#F5EFE2"),
    "Xám phác" to AndroidColor.parseColor("#D8D5CE"),
    "Đen tuyền" to AndroidColor.parseColor("#161514")
)

/**
 * Dialog to save the current 2048x2048 canvas to disk (either updating the open artwork or creating a new
 * saved artwork) and immediately purge the Undo/Redo history cache, or export a PNG to device Photos.
 */
@Composable
fun SaveArtworkDialog(
    initialTitle: String,
    isExistingArtwork: Boolean,
    undoStepsToClear: Int,
    onSaveOverwrite: (title: String) -> Unit,
    onSaveAsNew: (title: String) -> Unit,
    onExportPng: (title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        titleContentColor = InkText,
        textContentColor = MutedText,
        title = {
            Text(
                if (isExistingArtwork) "Lưu bản tranh vẽ" else "Lưu thành tranh đã vẽ",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Canvas 2048×2048 sẽ được chụp nguyên bản xuống ổ đĩa và xóa toàn bộ lịch sử Undo/Redo" +
                        (if (undoStepsToClear > 0) " ($undoStepsToClear bước hiện tại)" else "") +
                        " để giải phóng sạch RAM & bộ đệm Cold.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Tên bức tranh") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = InkText,
                        unfocusedTextColor = InkText,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = MutedText,
                        cursorColor = NeonCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyberSurfaceVariant,
                    border = BorderStroke(1.dp, CyberCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onExportPng(title.ifBlank { initialTitle }) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Xuất ảnh PNG ra Thư viện máy", color = InkText, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Text("Lưu vào Pictures/ChronosCanvas", color = MutedText, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isExistingArtwork) {
                    TextButton(onClick = { onSaveAsNew(title.ifBlank { initialTitle }) }) {
                        Text("Lưu bản mới", color = NeonCyan, fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = { onSaveOverwrite(title.ifBlank { initialTitle }) }) {
                        Text("Cập nhật bản này", color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(onClick = { onSaveAsNew(title.ifBlank { initialTitle }) }) {
                        Text("Lưu tranh", color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = MutedText)
            }
        }
    )
}

/**
 * Studio Gallery BottomSheet showing all saved and auto-captured artworks, plus New Canvas creation
 * with paper color presets, Rename, Duplicate, Export PNG, and Delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkGallerySheet(
    artworks: List<SavedArtwork>,
    activeArtworkId: String?,
    repository: ArtworkRepository,
    onSelectArtwork: (SavedArtwork) -> Unit,
    onCreateNewCanvas: (paperColor: Int) -> Unit,
    onSaveCurrentFirst: () -> Unit,
    hasUnsavedChanges: Boolean,
    onRenameArtwork: (SavedArtwork, String) -> Unit,
    onDuplicateArtwork: (SavedArtwork) -> Unit,
    onExportArtwork: (SavedArtwork) -> Unit,
    onDeleteArtwork: (SavedArtwork) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renamingTarget by remember { mutableStateOf<SavedArtwork?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingTarget by remember { mutableStateOf<SavedArtwork?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberSurface,
        contentColor = InkText,
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bộ sưu tập tranh vẽ", color = InkText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${artworks.size} bản đã lưu · Chạm để mở, tự xóa lịch sử cũ",
                        color = MutedText,
                        fontSize = 12.5.sp
                    )
                }
                if (hasUnsavedChanges) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldGreen.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onDismiss()
                                onSaveCurrentFirst()
                            }
                    ) {
                        Text(
                            "Lưu bản đang vẽ",
                            color = EmeraldGreen,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // New Canvas row with paper presets
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Tạo trang vẽ mới (2048×2048)", color = FaintText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((label, bgInt) in PaperBackgroundPresets) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyberSurfaceVariant,
                            border = BorderStroke(1.dp, CyberCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onCreateNewCanvas(bgInt)
                                    onDismiss()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(bgInt))
                                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = if (bgInt == AndroidColor.parseColor("#161514")) Color.White else Color(0xFF1C1B1A),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(label, color = InkText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(CyberCardBorder))
            Spacer(Modifier.height(12.dp))

            if (artworks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Image, null, tint = FaintText, modifier = Modifier.size(36.dp))
                        Text("Chưa có bản tranh nào được lưu", color = InkText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Nhấn biểu tượng Lưu trên thanh công cụ hoặc khi đóng ứng dụng bản vẽ cuối sẽ tự được chụp lại tại đây.",
                            color = MutedText,
                            fontSize = 12.5.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 460.dp)
                ) {
                    items(artworks, key = { it.id }) { art ->
                        ArtworkCard(
                            artwork = art,
                            isActive = art.id == activeArtworkId,
                            repository = repository,
                            onOpen = {
                                onSelectArtwork(art)
                                onDismiss()
                            },
                            onRename = {
                                renamingTarget = art
                                renameText = art.title
                            },
                            onDuplicate = { onDuplicateArtwork(art) },
                            onExport = { onExportArtwork(art) },
                            onDelete = { deletingTarget = art }
                        )
                    }
                }
            }
        }
    }

    if (renamingTarget != null) {
        val target = renamingTarget!!
        AlertDialog(
            onDismissRequest = { renamingTarget = null },
            containerColor = CyberSurface,
            titleContentColor = InkText,
            textContentColor = MutedText,
            title = { Text("Đổi tên tranh") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = InkText,
                        unfocusedTextColor = InkText,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRenameArtwork(target, renameText)
                    renamingTarget = null
                }) { Text("Lưu tên", color = NeonCyan, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { renamingTarget = null }) { Text("Hủy", color = MutedText) }
            }
        )
    }

    if (deletingTarget != null) {
        val target = deletingTarget!!
        AlertDialog(
            onDismissRequest = { deletingTarget = null },
            containerColor = CyberSurface,
            titleContentColor = InkText,
            textContentColor = MutedText,
            title = { Text("Xóa '${target.title}'?") },
            text = { Text("Tệp ảnh của bản vẽ này sẽ bị xóa vĩnh viễn khỏi ổ đĩa.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteArtwork(target)
                    deletingTarget = null
                }) { Text("Xóa", color = CoralRose, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTarget = null }) { Text("Hủy", color = MutedText) }
            }
        )
    }
}

@Composable
private fun ArtworkCard(
    artwork: SavedArtwork,
    isActive: Boolean,
    repository: ArtworkRepository,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var thumbBitmap by remember(artwork.id, artwork.updatedAtMs) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(artwork.id, artwork.updatedAtMs) {
        thumbBitmap = withContext(Dispatchers.IO) {
            repository.loadThumbnailBitmap(artwork.id)
        }
    }

    val dateStr = remember(artwork.updatedAtMs) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(artwork.updatedAtMs))
    }
    val sizeStr = remember(artwork.sizeBytes) {
        val kb = artwork.sizeBytes / 1024.0
        if (kb >= 1024.0) String.format(Locale.US, "%.1f MB", kb / 1024.0)
        else String.format(Locale.US, "%.0f KB", kb)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberSurfaceVariant,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) NeonCyan else CyberCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .background(Color.White)
            ) {
                val bmp = thumbBitmap
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = artwork.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isActive) {
                        CardBadge("Đang mở", NeonCyan)
                    }
                    if (artwork.autoCapturedOnExit) {
                        CardBadge("Chụp khi đóng", AmberGold)
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = artwork.title,
                    color = InkText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dateStr · $sizeStr",
                    color = MutedText,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallCardAction(Icons.Rounded.DriveFileRenameOutline, "Đổi tên", MutedText, onRename)
                    SmallCardAction(Icons.Rounded.ContentCopy, "Nhân bản", MutedText, onDuplicate)
                    SmallCardAction(Icons.Rounded.Download, "Xuất PNG", EmeraldGreen, onExport)
                    SmallCardAction(Icons.Rounded.DeleteOutline, "Xóa", CoralRose, onDelete)
                }
            }
        }
    }
}

@Composable
private fun CardBadge(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CyberSurface.copy(alpha = 0.88f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallCardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/**
 * Confirmation dialog shown when pressing Back while drawing: lets the user save a named painting
 * or exit immediately (still auto-capturing the final canvas and clearing all Undo/Redo cache).
 */
@Composable
fun ExitStudioDialog(
    hasUnsavedChanges: Boolean,
    undoSteps: Int,
    onSaveAndExit: () -> Unit,
    onAutoCaptureAndExit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        titleContentColor = InkText,
        textContentColor = MutedText,
        title = { Text("Đóng phiên vẽ hiện tại?", fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                if (hasUnsavedChanges) {
                    "Ứng dụng sẽ chụp lại bản vẽ cuối xuống ổ đĩa và dọn sạch toàn bộ cache lịch sử Undo/Redo" +
                        (if (undoSteps > 0) " ($undoSteps bước)" else "") + "."
                } else {
                    "Toàn bộ cache lịch sử Undo/Redo hiện tại sẽ được dọn sạch khi thoát."
                },
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hasUnsavedChanges) {
                    TextButton(onClick = onSaveAndExit) {
                        Text("Lưu đặt tên & Thoát", color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onAutoCaptureAndExit) {
                    Text(
                        if (hasUnsavedChanges) "Chụp bản cuối & Thoát" else "Thoát",
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ở lại vẽ", color = MutedText)
            }
        }
    )
}
