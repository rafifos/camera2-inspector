package dev.rafifos.camera2inspector.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.camera.CameraReport
import dev.rafifos.camera2inspector.camera.KeyCategory
import dev.rafifos.camera2inspector.camera.KeyEntry
import dev.rafifos.camera2inspector.camera.SerializationMode
import dev.rafifos.camera2inspector.camera.entriesForCategory
import dev.rafifos.camera2inspector.ui.availabilityNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyDetailScreen(
    camera: CameraReport,
    category: KeyCategory,
    keyName: String,
    onBack: () -> Unit,
) {
    val entry = remember(camera, category, keyName) {
        entriesForCategory(camera, category).firstOrNull { it.info.name == keyName }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.key_detail_title)) },
                subtitle = {
                    Text(
                        text = category.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        if (entry == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.key_not_found, keyName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Scaffold
        }

        KeyDetailContent(
            camera = camera,
            category = category,
            entry = entry,
            scrollBehavior = scrollBehavior,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeyDetailContent(
    camera: CameraReport,
    category: KeyCategory,
    entry: KeyEntry,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val noMessage = stringResource(R.string.no_message)
    val clipLabelKey = stringResource(R.string.clip_label_key)
    val clipLabelValue = stringResource(R.string.clip_label_value)
    val clipLabelTostring = stringResource(R.string.clip_label_tostring)
    val buttonShapes = ButtonDefaults.shapes()

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = entry.info.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            context.copyToClipboard(clipLabelKey, entry.info.name)
                        },
                        shapes = buttonShapes,
                    ) {
                        Text(stringResource(R.string.copy_key_name))
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.metadata_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                DetailLine(stringResource(R.string.field_type), entry.info.typeLabel)
                DetailLine(stringResource(R.string.field_type_source), entry.info.typeSource)
                DetailLine(stringResource(R.string.field_category), entry.info.category.label)
                DetailLine(
                    stringResource(R.string.field_vendor),
                    stringResource(if (entry.info.vendor) R.string.yes else R.string.no),
                )
                DetailLine(stringResource(R.string.field_namespace), entry.info.namespace)
                DetailLine(stringResource(R.string.field_camera), camera.id)
                if (category == KeyCategory.PHYSICAL_CAPTURE_REQUEST) {
                    DetailLine(
                        stringResource(R.string.field_physical_ids),
                        if (camera.physicalCameraIds.isEmpty()) {
                            stringResource(R.string.value_not_reported)
                        } else {
                            camera.physicalCameraIds.joinToString(", ")
                        },
                    )
                }
                DetailLine(stringResource(R.string.field_origin), entry.info.origin)
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.availability_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = availabilityNote(category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.info.vendor) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.vendor_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        entry.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.error_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    DetailLine(stringResource(R.string.field_exception_type), error.type)
                    DetailLine(stringResource(R.string.field_message), error.message ?: noMessage)
                }
            }
        }

        if (entry.serialization != SerializationMode.ERROR) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.value_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    val valueText = entry.valueText
                    if (valueText != null) {
                        Text(
                            text = stringResource(R.string.value_serialization, entry.serialization.label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer {
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        OutlinedButton(
                            onClick = { context.copyToClipboard(clipLabelValue, valueText) },
                            shapes = buttonShapes,
                        ) {
                            Text(stringResource(R.string.copy_value))
                        }
                    } else {
                        Text(
                            text = if (entry.serialization == SerializationMode.NOT_READ) {
                                stringResource(R.string.value_none_for_category, category.label)
                            } else {
                                stringResource(R.string.value_unreadable)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        entry.rawToString?.let { raw ->
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.raw_tostring_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    SelectionContainer {
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    OutlinedButton(
                        onClick = { context.copyToClipboard(clipLabelTostring, raw) },
                        shapes = buttonShapes,
                    ) {
                        Text(stringResource(R.string.copy_tostring))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun Context.copyToClipboard(label: String, value: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
