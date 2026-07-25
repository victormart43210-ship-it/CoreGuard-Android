package com.coldboar.coreguard.ui

import androidx.compose.runtime.Composable
import com.coldboar.coreguard.ui.nav.CoreGuardNavGraph

/**
 * Compatibility shim for the root composable.
 *
 * The navigation graph has moved to [CoreGuardNavGraph] in the `ui.nav` package.
 * This delegation keeps any call-sites that still reference [CoreGuardApp] working
 * without changes.
 */
@Composable
fun CoreGuardApp() = CoreGuardNavGraph()
