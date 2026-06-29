package com.toby.walkman

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AppEntry(val pkg: String, val label: String)

private val Green = Color(0xFF1DB954)
private val Panel = Color(0xFF0C0C0C)
private val Card = Color(0xFF1A1A1A)

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current

    // The Walkman runs landscape; the settings menu is nicer upright. Flip to portrait while
    // it's open and restore the previous orientation when it closes.
    DisposableEffect(Unit) {
        val activity = ctx.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    val apps = remember { loadLaunchableApps(ctx) }

    var mode by remember { mutableStateOf(LockController.mode) }
    var minutes by remember { mutableStateOf(LockController.minutes) }
    var songs by remember { mutableStateOf(LockController.songs) }
    var scope by remember { mutableStateOf(LockController.scope) }
    var locked by remember { mutableStateOf(LockController.locked) }

    var permTick by remember { mutableStateOf(0) }
    val usageOk = remember(permTick) { hasUsageAccess(ctx) }
    val overlayOk = remember(permTick) { Settings.canDrawOverlays(ctx) }
    val active = LockController.active

    Surface(Modifier.fillMaxSize(), color = Panel) {
        // Lay the menu out as a centered, portrait-shaped column. This keeps it upright and
        // tidy even on big-screen devices that ignore the portrait orientation request above.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          Column(Modifier.widthIn(max = 480.dp).fillMaxHeight().padding(18.dp)) {

            // --- Pinned header ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lock Away Apps", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { permTick++ }) { Text("Refresh", color = Color.Gray) }
                TextButton(onClick = onClose) { Text("Done", color = Green) }
            }

            // --- Scrollable body ---
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))

                // End the session after: normal time, or a number of songs.
                Text("End after", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    chip("Time", mode == LockController.Mode.TIME, Modifier.weight(1f)) {
                        mode = LockController.Mode.TIME
                    }
                    chip("Songs", mode == LockController.Mode.SONGS, Modifier.weight(1f)) {
                        mode = LockController.Mode.SONGS
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (mode == LockController.Mode.TIME) {
                    stepper("Minutes", minutes, "min") { minutes = (minutes + it).coerceIn(1, 600) }
                } else {
                    stepper("Songs", songs, "songs") { songs = (songs + it).coerceIn(1, 200) }
                }

                Spacer(Modifier.height(16.dp))

                // What to lock.
                Text("Lock away", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    chip("Everything", scope == LockController.Scope.EVERYTHING, Modifier.weight(1f)) {
                        scope = LockController.Scope.EVERYTHING
                    }
                    chip("Only selected", scope == LockController.Scope.SELECTED, Modifier.weight(1f)) {
                        scope = LockController.Scope.SELECTED
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (!usageOk) {
                    permRow("Allow “Usage access” so we can see the foreground app") {
                        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                if (!overlayOk) {
                    permRow("Allow “Display over other apps” so we can show the lock") {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (scope == LockController.Scope.EVERYTHING) {
                    Text("Every app is locked away.\nSpotify, Walkman, your home screen and phone " +
                            "calls stay open.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    Text("Tick the apps to lock away:", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    apps.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(a.label, color = Color.White, fontSize = 15.sp,
                                modifier = Modifier.weight(1f))
                            Text(if (a.pkg in locked) "locked" else "open",
                                color = if (a.pkg in locked) Green else Color(0xFF777777),
                                fontSize = 12.sp)
                            Checkbox(
                                checked = a.pkg in locked,
                                onCheckedChange = { on -> locked = if (on) locked + a.pkg else locked - a.pkg }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // --- Pinned footer ---
            if (active) {
                Button(
                    onClick = { LockController.stop(ctx) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB23030))
                ) { Text("Stop lock — ${LockController.remainingLabel()}") }
            } else {
                Button(
                    onClick = {
                        LockController.mode = mode
                        LockController.minutes = minutes
                        LockController.songs = songs
                        LockController.scope = scope
                        LockController.locked = locked
                        LockController.start(ctx)   // persists settings + live session
                        ctx.startForegroundService(
                            Intent(ctx, LockService::class.java).setAction(LockService.ACTION_START)
                        )
                        onClose()
                    },
                    enabled = usageOk && overlayOk &&
                        (scope == LockController.Scope.EVERYTHING || locked.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Text(if (mode == LockController.Mode.TIME) "Lock for $minutes min"
                         else "Lock for $songs songs")
                }
            }
          }
        }
    }
}

@Composable
private fun chip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) Green else Card
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bg)
    ) { Text(label, color = if (selected) Color.Black else Color.White) }
}

@Composable
private fun stepper(label: String, value: Int, unit: String, onDelta: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onDelta(if (unit == "min") -5 else -1) }) { Text("–") }
        Text("$value $unit", color = Green, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp))
        OutlinedButton(onClick = { onDelta(if (unit == "min") 5 else 1) }) { Text("+") }
    }
}

@Composable
private fun permRow(text: String, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A2A12)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color(0xFFFFC777), fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onGrant) { Text("Grant", color = Green) }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun loadLaunchableApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .distinctBy { it.first }
        .filter { it.first != ctx.packageName && it.first !in LockController.alwaysAllowed }
        .map { AppEntry(it.first, it.second) }
        .sortedBy { it.label.lowercase() }
}

private fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
