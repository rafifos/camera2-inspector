package dev.rafifos.camera2inspector.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.camera.CameraReport
import dev.rafifos.camera2inspector.camera.KeyCategory
import dev.rafifos.camera2inspector.camera.KeyEntry
import dev.rafifos.camera2inspector.camera.KeyQuery
import dev.rafifos.camera2inspector.camera.categoryCount
import dev.rafifos.camera2inspector.camera.distinctNamespaces
import dev.rafifos.camera2inspector.camera.entriesForCategory
import dev.rafifos.camera2inspector.camera.filterKeyEntries
import dev.rafifos.camera2inspector.ui.availabilityNote
import dev.rafifos.camera2inspector.ui.components.KeyRow
import dev.rafifos.camera2inspector.ui.text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDetailScreen(
    camera: CameraReport,
    onBack: () -> Unit,
    onKeyClick: (KeyEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf(KeyCategory.CHARACTERISTICS.name) }
    var namespace by rememberSaveable { mutableStateOf<String?>(null) }
    var vendorOnly by rememberSaveable { mutableStateOf(false) }

    val category = KeyCategory.entries.firstOrNull { it.name == categoryName }
        ?: KeyCategory.CHARACTERISTICS
    val allEntries = remember(camera, category) { entriesForCategory(camera, category) }
    val namespaces = remember(camera, category) { distinctNamespaces(allEntries) }
    val filtered = remember(allEntries, query, namespace, vendorOnly) {
        filterKeyEntries(
            allEntries,
            KeyQuery(text = query, category = category, namespace = namespace, vendorOnly = vendorOnly),
        )
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(category) { namespace = null }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.camera_id, camera.id)) },
                subtitle = {
                    Text(
                        text = stringResource(
                            R.string.camera_detail_subtitle,
                            camera.lensFacing,
                            camera.hardwareLevel,
                        ),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "overview") { CameraOverviewCard(camera) }

            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_search),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = OutlinedTextFieldDefaults.roundedShape,
                    colors = OutlinedTextFieldDefaults.tonalColors(),
                )
            }

            item(key = "category-chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(KeyCategory.entries, key = { it.name }) { option ->
                        FilterChip(
                            selected = option == category,
                            onClick = { categoryName = option.name },
                            label = { Text("${option.label} (${categoryCount(camera, option)})") },
                        )
                    }
                }
            }

            item(key = "namespace-chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "vendor-only") {
                        FilterChip(
                            selected = vendorOnly,
                            onClick = { vendorOnly = !vendorOnly },
                            label = { Text(stringResource(R.string.filter_vendor_only)) },
                        )
                    }
                    item(key = "namespace-all") {
                        FilterChip(
                            selected = namespace == null,
                            onClick = { namespace = null },
                            label = { Text(stringResource(R.string.filter_all_namespaces)) },
                        )
                    }
                    items(namespaces, key = { it.first }) { (ns, count) ->
                        FilterChip(
                            selected = namespace == ns,
                            onClick = { namespace = if (namespace == ns) null else ns },
                            label = { Text("$ns ($count)") },
                        )
                    }
                }
            }

            item(key = "result-count") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            R.string.keys_result_count,
                            filtered.size,
                            allEntries.size,
                            category.label,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = availabilityNote(category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Card {
                        Text(
                            text = stringResource(R.string.keys_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            items(filtered, key = { "${it.info.category.name}:${it.info.name}" }) { entry ->
                KeyRow(
                    entry = entry,
                    subtitle = physicalSubtitle(camera, entry),
                    onClick = { onKeyClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun CameraOverviewCard(camera: CameraReport) {
    val noMessage = stringResource(R.string.no_message)

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.overview_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            OverviewLine(
                stringResource(R.string.overview_logical_multi_camera),
                stringResource(if (camera.logicalMultiCamera) R.string.yes else R.string.no),
            )
            OverviewLine(
                stringResource(R.string.overview_physical_ids),
                if (camera.physicalCameraIds.isEmpty()) {
                    stringResource(R.string.value_none_reported)
                } else {
                    camera.physicalCameraIds.joinToString(", ")
                },
            )
            OverviewLine(stringResource(R.string.overview_hardware_level), camera.hardwareLevel)
            OverviewLine(stringResource(R.string.overview_lens_facing), camera.lensFacing)
            OverviewLine(
                stringResource(R.string.overview_sensor_orientation),
                camera.sensorOrientation?.toString() ?: stringResource(R.string.value_not_reported),
            )

            if (camera.notes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.notes_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                camera.notes.forEach { note ->
                    Text(
                        text = stringResource(R.string.note_bullet, note.text()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (camera.errors.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                camera.errors.forEach { error ->
                    Text(
                        text = "${error.type}: ${error.message ?: noMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun physicalSubtitle(camera: CameraReport, entry: KeyEntry): String? {
    if (entry.info.category != KeyCategory.PHYSICAL_CAPTURE_REQUEST) return null
    if (camera.physicalCameraIds.isEmpty()) {
        return stringResource(R.string.physical_ids_not_reported)
    }
    return stringResource(
        R.string.physical_ids_associated,
        camera.physicalCameraIds.joinToString(", "),
    )
}
