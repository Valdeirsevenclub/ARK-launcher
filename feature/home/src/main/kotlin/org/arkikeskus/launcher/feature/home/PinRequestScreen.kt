package org.arkikeskus.launcher.feature.home

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.data.PinRequest

/**
 * A requested widget's preview: the provider for [WidgetPreview] (its own preview, as the widget
 * picker shows it) and the footprint's size in dp (what the widget will measure on home).
 */
data class WidgetPinPreview(
    val info: AppWidgetProviderInfo,
    val widthDp: Float,
    val heightDp: Float,
)

/** The system wants the user asked before [appWidgetId] is bound: open `ACTION_APPWIDGET_BIND`. */
data class WidgetBindRequest(val appWidgetId: Int, val info: AppWidgetProviderInfo)

/** What the confirmation of another app's pin request shows ([PinRequestViewModel.uiState]). */
sealed interface PinRequestUiState {
    /** The decision and the preview are still being read; nothing is shown yet. */
    data object Loading : PinRequestUiState

    /** Ask the user. [appLabel] is the requesting app, [label] and [icon] the item as home will
     *  show it ([widget] instead of [icon] for a widget), [busy] while an accept is under way (the
     *  buttons are disabled), [bindRequest] while the system's bind dialog must be opened. */
    data class Confirm(
        val appLabel: String,
        val label: String,
        val icon: ImageBitmap?,
        val spanX: Int,
        val spanY: Int,
        val busy: Boolean = false,
        val widget: WidgetPinPreview? = null,
        val bindRequest: WidgetBindRequest? = null,
    ) : PinRequestUiState

    /** The desktop is locked: say so, offer only Close. */
    data object Locked : PinRequestUiState

    /** Every page up to the limit is full: say so, offer only Close. */
    data object NoSpace : PinRequestUiState

    /** A work-profile widget, which the home screen can't hold yet: say so, offer only Close. */
    data object WorkProfile : PinRequestUiState

    /** Even the widget's smallest size doesn't fit the grid: say so, offer only Close. */
    data object TooBig : PinRequestUiState

    /** Close the confirmation. [addedLabel] names what was added (announced for TalkBack);
     *  [failed] shows "Couldn't add to home screen" first. */
    data class Finished(val addedLabel: String? = null, val failed: Boolean = false) : PinRequestUiState
}

/**
 * The confirmation sheet itself, stateless: the requesting app's name as the title, a preview card
 * of the item as home will show it (a widget with the picker's preview), and the buttons — or,
 * when nothing can be added (locked, full, a work-profile or an oversized widget), the explanation
 * and Close. Cancel, a swipe down, Back and a tap outside all end in [onCancel]: nothing is accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PinRequestSheet(
    state: PinRequestUiState,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    // Fully open at once: the sheet is short, and a half state would only hide the buttons.
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A widget's card can outgrow a short window (a phone on its side): the content
                // scrolls then, and the sheet still follows a drag down from its top.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            when (state) {
                is PinRequestUiState.Confirm -> ConfirmContent(state, onAdd, onCancel)
                PinRequestUiState.Locked -> NoticeContent(
                    title = stringResource(R.string.pin_request_locked_title),
                    text = stringResource(R.string.pin_request_locked_text),
                    onClose = onCancel,
                )
                PinRequestUiState.NoSpace -> NoticeContent(
                    title = stringResource(R.string.pin_request_full_title),
                    text = stringResource(R.string.pin_request_full_text),
                    onClose = onCancel,
                )
                PinRequestUiState.WorkProfile -> NoticeContent(
                    title = stringResource(R.string.pin_request_work_profile),
                    text = null,
                    onClose = onCancel,
                )
                PinRequestUiState.TooBig -> NoticeContent(
                    title = stringResource(R.string.pin_request_too_big),
                    text = null,
                    onClose = onCancel,
                )
                PinRequestUiState.Loading, is PinRequestUiState.Finished -> Unit
            }
        }
    }
}

@Composable
private fun ConfirmContent(state: PinRequestUiState.Confirm, onAdd: () -> Unit, onCancel: () -> Unit) {
    Text(
        text = state.appLabel,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.pin_request_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        ) {
            val widget = state.widget
            val icon = state.icon
            if (widget != null) {
                // The picker's preview at the footprint's own proportions (bounded as the picker
                // bounds them), no taller than PREVIEW_MAX_HEIGHT so the buttons stay on a short
                // screen. No fillMaxWidth in front: it fixes the width, and the ratio would then
                // measure past the height cap (see WidgetCard). Decorative: the name below says what it is.
                Box(
                    modifier = Modifier
                        .heightIn(max = PREVIEW_MAX_HEIGHT)
                        .aspectRatio((widget.widthDp / widget.heightDp).coerceIn(1.5f, 3f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .testTag(PIN_WIDGET_PREVIEW_TAG),
                    contentAlignment = Alignment.Center,
                ) {
                    WidgetPreview(
                        WidgetChoice.App(widget.info), Modifier.fillMaxSize().padding(12.dp),
                        widget.widthDp, widget.heightDp, onSnapshot = {},
                    )
                }
            } else if (icon != null) {
                // Decorative: the name right below says what it is.
                Image(icon, contentDescription = null, modifier = Modifier.size(PREVIEW_ICON_SIZE))
            } else {
                Box(Modifier.size(PREVIEW_ICON_SIZE))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.widget_size, state.spanX, state.spanY),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        // Neutral, not the theme's secondary container: that is green in both of ARK's schemes,
        // and the agreed look has a quiet Cancel next to a filled Add.
        FilledTonalButton(
            onClick = onCancel,
            enabled = !state.busy,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.cancel))
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onAdd, enabled = !state.busy) {
            Text(stringResource(R.string.pin_request_add))
        }
    }
}

@Composable
private fun NoticeContent(title: String, text: String?, onClose: () -> Unit) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall)
    if (text != null) {
        Spacer(Modifier.height(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = onClose) { Text(stringResource(R.string.pin_request_close)) }
    }
}

private val PREVIEW_ICON_SIZE = 64.dp

/** The most a widget's preview may take of the sheet's height. */
private val PREVIEW_MAX_HEIGHT = 140.dp

/** Test tag of the widget preview in the confirmation's card. */
internal const val PIN_WIDGET_PREVIEW_TAG = "pinWidgetPreview"

/**
 * The confirmation another app's "Add to home screen" opens, over that app. [request] is handed to
 * the ViewModel once; a recreated activity hands a fresh wrapper of the same request, which the
 * ViewModel ignores, so neither the request nor an accept under way is lost. [onFinish] closes the
 * activity — after an accept, a cancel, or an error. A widget the launcher may not bind by itself
 * goes through the system's bind dialog from here; the activity stays up for its answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinRequestScreen(
    request: PinRequest,
    onFinish: () -> Unit,
    viewModel: PinRequestViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        viewModel.attach(request, activity?.let(::homeWindowOf))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Registered under a saved key, so the dialog's answer reaches a recreated screen as well.
    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onBindResult(result.resultCode == Activity.RESULT_OK)
    }
    val bind = (state as? PinRequestUiState.Confirm)?.bindRequest
    LaunchedEffect(bind) {
        if (bind == null) return@LaunchedEffect
        // Same request as the home screen's own add path (HomeScreen.startBind).
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, bind.appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, bind.info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, bind.info.profile)
        val launched = runCatching { bindLauncher.launch(intent) }.isSuccess
        viewModel.bindDialogOpened()
        if (!launched) viewModel.onBindResult(false)
    }
    // What the sheet keeps showing while it slides away once the outcome is known.
    var lastShown by remember { mutableStateOf<PinRequestUiState?>(null) }
    val s = state
    val showable = s is PinRequestUiState.Confirm || s is PinRequestUiState.Locked || s is PinRequestUiState.NoSpace ||
        s is PinRequestUiState.WorkProfile || s is PinRequestUiState.TooBig
    SideEffect { if (showable) lastShown = s }
    if (s is PinRequestUiState.Finished) {
        LaunchedEffect(s) {
            if (s.failed) Toast.makeText(context, R.string.pin_request_failed, Toast.LENGTH_SHORT).show()
            // Said before the window goes, as Launcher3's AddItemActivity does: the user stays in
            // the requesting app and would otherwise hear nothing of the outcome.
            if (s.addedLabel != null) {
                @Suppress("DEPRECATION")
                view.announceForAccessibility(context.getString(R.string.pin_request_added, s.addedLabel))
            }
            runCatching { sheetState.hide() }
            onFinish()
        }
    }
    val shown = if (showable) s else lastShown
    if (shown != null) {
        PinRequestSheet(state = shown, onAdd = viewModel::add, onCancel = viewModel::cancel, sheetState = sheetState)
    }
}
