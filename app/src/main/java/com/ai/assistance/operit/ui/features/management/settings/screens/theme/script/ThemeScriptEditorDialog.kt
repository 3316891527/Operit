package com.ai.assistance.operit.ui.features.management.settings.screens.theme.script

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import com.ai.assistance.operit.data.model.theme.ThemeScriptManifest
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDialog
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchPreview
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.resolveThemeColorScheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAssets
import com.ai.assistance.operit.ui.theme.script.ThemeScriptResolver
import com.ai.assistance.operit.ui.theme.script.themeScriptContext
import com.ai.assistance.operit.util.files.FileUtils
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ThemeScriptEditorDialog(session: ThemeEditorSession, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val values by session.values.collectAsState()
    val snapshot = LocalThemePreferenceSnapshot.current.copy(values = values)
    val colors = resolveThemeColorScheme(context, snapshot)
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = if (snapshot.useSystemTheme) systemDark else snapshot.themeMode == "dark"
    var source by remember { mutableStateOf("") }
    var manifest by remember { mutableStateOf(ThemeScriptManifest(1, "assets/main.js")) }
    var assets by remember { mutableStateOf<Map<String, ByteArray>>(emptyMap()) }
    var assetName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("visual") }
    var preview by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(if (dark) "dark" else "light") }

    fun loadPreset(id: String) {
        scope.launch {
            busy = true
            error = null
            try {
                val bundle = withContext(Dispatchers.IO) { ThemeScriptBundleStore.readPreset(context.assets, id) }
                manifest = bundle.manifest
                source = bundle.source
                assets = bundle.assets
                preview = true
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                AppLogger.e("ThemeScriptEditor", "主题模板读取失败", failure)
                error = failure.message.orEmpty().take(4096)
            } finally { busy = false }
        }
    }

    fun report(failure: Exception) {
        AppLogger.e("ThemeScriptEditor", "主题脚本编辑失败", failure)
        error = failure.message.orEmpty().take(4096)
    }
    LaunchedEffect(session) {
        try {
            val bundle = withContext(Dispatchers.IO) {
                val reference = session.currentValues.string(ThemePreferenceValues.SCRIPT_URI_KEY)
                if (reference == null) {
                    ThemeScriptBundle(manifest, context.assets.open("theme-script/example.js").bufferedReader().use { it.readText() }, emptyMap())
                } else {
                    ThemeScriptBundleStore.read(ThemeScriptBundleStore.fileForReference(context.filesDir, reference))
                }
            }
            source = bundle.source
            manifest = bundle.manifest
            assets = bundle.assets
            loaded = true
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            report(failure)
        } finally { busy = false }
    }

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try {
                source = withContext(Dispatchers.IO) {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                        ThemeScriptBundleStore.readLimited(it, ThemeScriptContract.MAX_SOURCE_BYTES)
                    }
                    Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                report(failure)
            } finally { busy = false }
        }
    }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try {
                val name = assetName.trim()
                val (path, bytes) = withContext(Dispatchers.IO) {
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    require(extension in setOf("png", "jpg", "jpeg", "webp", "svg")) { "Expected PNG, JPEG, WebP or SVG" }
                    val path = "assets/image-${UUID.randomUUID()}.$extension"
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                        ThemeScriptBundleStore.readLimited(it, ThemeScriptContract.MAX_ASSET_BYTES)
                    }
                    path to bytes
                }
                val updated = manifest.copy(assets = manifest.assets + (name to path))
                val updatedAssets = (assets + (path to bytes)).filterKeys { it in updated.assets.values }
                val bundle = ThemeScriptBundle(updated, source, updatedAssets)
                withContext(Dispatchers.IO) {
                    ThemeScriptBundleStore.validate(bundle)
                    ThemeScriptAssets.prepare(bundle)
                }
                manifest = updated
                assets = updatedAssets
                assetName = ""
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                report(failure)
            } finally { busy = false }
        }
    }
    ThemeWorkbenchDialog(stringResource(R.string.theme_script_title), { if (!busy) onClose() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.theme_script_draft_hint), style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (loaded && !busy) {
                Text(stringResource(R.string.theme_appearance_preset_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { loadPreset("forest") }) { Text(stringResource(R.string.theme_appearance_forest)) }
                    TextButton(onClick = { loadPreset("paper") }) { Text(stringResource(R.string.theme_appearance_paper)) }
                }
                ThemeAppearanceChoice(
                    stringResource(R.string.theme_appearance_category), tab,
                    listOf("visual" to stringResource(R.string.theme_appearance_visual), "source" to stringResource(R.string.theme_appearance_source_tab), "assets" to stringResource(R.string.theme_appearance_assets_tab)),
                ) { tab = it }
                when (tab) {
                    "visual" -> ThemeAppearanceEditor(
                        appearance = manifest.appearance,
                        assets = manifest.assets.keys,
                        onChange = { manifest = manifest.copy(appearance = it); error = null },
                        previewMode = previewMode,
                        onPreviewModeChange = { previewMode = it },
                    )
                    "source" -> {
                        Text(stringResource(R.string.theme_appearance_source_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it; error = null },
                            enabled = loaded && !busy,
                            label = { Text(stringResource(R.string.theme_script_source)) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 10,
                            maxLines = 20,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { sourcePicker.launch("*/*") }, enabled = loaded && !busy) {
                            Text(stringResource(R.string.theme_script_import_source))
                        }
                    }
                    "assets" -> {
                        manifest.assets.toSortedMap().forEach { (name, _) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    manifest = manifest.copy(assets = manifest.assets - name)
                                    assets = assets.filterKeys { it in manifest.assets.values }
                                }, enabled = !busy) { Text(stringResource(R.string.theme_script_remove_asset)) }
                            }
                        }
                        OutlinedTextField(
                            value = assetName, onValueChange = { assetName = it }, enabled = loaded && !busy,
                            label = { Text(stringResource(R.string.theme_script_asset_name)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { assetPicker.launch("*/*") },
                            enabled = loaded && !busy && Regex("[A-Za-z][A-Za-z0-9_-]{0,63}").matches(assetName.trim()),
                        ) { Text(stringResource(R.string.theme_script_import_asset)) }
                    }
                }
                TextButton(onClick = { preview = !preview }) { Text(stringResource(R.string.theme_appearance_preview)) }
                if (preview) {
                    ThemeAppearanceChoice(
                        stringResource(R.string.theme_appearance_preview_mode), previewMode,
                        listOf("light" to stringResource(R.string.theme_appearance_light), "dark" to stringResource(R.string.theme_appearance_dark)),
                    ) { previewMode = it }
                    ThemeWorkbenchPreview(
                        session, full = true, showDescription = false,
                        draft = ThemeScriptBundle(manifest, source, assets), previewMode = previewMode,
                        showComponentSamples = true,
                    )
                }
            }
            error?.let { Text(stringResource(R.string.theme_script_error, it), color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = loaded && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        var staged: File? = null
                        try {
                            val bundle = ThemeScriptBundle(manifest, source, assets)
                            val images = withContext(Dispatchers.IO) {
                                ThemeScriptBundleStore.validate(bundle)
                                ThemeScriptAssets.prepare(bundle)
                            }
                            val appearance = ThemeScriptResolver.resolve(bundle, themeScriptContext(values, colors, dark, manifest.appearance))
                            ThemeScriptAssets.validateAppearance(appearance, images)
                            val file = withContext(Dispatchers.IO) {
                                ThemeScriptBundleStore.create(context.filesDir, bundle).also { staged = it }
                            }
                            val reference = Uri.fromFile(file).toString()
                            session.registerStagedAsset(reference)
                            session.setString(ThemePreferenceValues.SCRIPT_URI_KEY, reference)
                            staged = null
                            onClose()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            report(failure)
                        } finally {
                            staged?.delete()
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.theme_script_apply_draft)) }
        }
    }
}