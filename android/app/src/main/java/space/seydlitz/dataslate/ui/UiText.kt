package space.seydlitz.dataslate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import space.seydlitz.dataslate.auth.UiText

@Composable
fun UiText.resolve(): String = stringResource(id, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
