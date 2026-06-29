package com.toby.walkman

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF2E50A3)
private val BlueDark = Color(0xFF1E3680)
private val Silver = Color(0xFFDCE0E4)
private val SilverDark = Color(0xFF8C9096)
private val Cream = Color(0xFFF2EBCC)
private val Orange = Color(0xFFF2851E)

@Composable
fun WalkmanScreen(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val dur = vm.now?.durationMs ?: 0L
    val progress = if (dur > 0) (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f

    val transition = rememberInfiniteTransition(label = "reel")
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "spin"
    )
    val rotation = if (vm.isPlaying) spin else 0f

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // The Walkman is a landscape device: a centered rectangle as tall as the screen.
        Column(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1.5f)
                .padding(14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Blue)
        ) {
            silverTop(vm, Modifier.fillMaxWidth().fillMaxHeight(0.15f))
            blueFace(vm, progress, rotation, Modifier.fillMaxWidth().weight(1f))
        }

        if (!vm.hasAccess) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(32.dp)) {
                    Text("Grant Notification access so the Walkman can mirror and control "
                            + "whatever you're playing (e.g. Spotify).",
                        color = Color.White, textAlign = TextAlign.Center)
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open Settings") }
                }
            }
        }

        // Top-corner Spotify launcher.
        spotifyButton(Modifier.align(Alignment.TopEnd).padding(18.dp))
    }
}

@Composable
private fun spotifyButton(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF1DB954))   // Spotify green
            .clickable { openSpotify(ctx) },
        contentAlignment = Alignment.Center
    ) {
        // The Spotify mark: three nested arcs.
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val arcs = listOf(0.34f to 0.18f, 0.50f to 0.25f, 0.66f to 0.32f)
            for ((y, inset) in arcs) {
                val yy = w * y
                val path = Path().apply {
                    moveTo(w * inset, yy)
                    quadraticBezierTo(w / 2f, yy - w * 0.12f, w * (1 - inset), yy)
                }
                drawPath(path, Color.Black.copy(alpha = 0.92f),
                    style = Stroke(width = w * 0.085f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun openSpotify(ctx: Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage("com.spotify.music")
    if (launch != null) {
        ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    // Spotify not installed — try the open.spotify.com handler, then the Play Store.
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.recoverCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(ctx, "Spotify isn't installed", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun silverTop(vm: PlayerViewModel, modifier: Modifier) {
    Box(modifier.background(Brush.verticalGradient(listOf(Silver, SilverDark)))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(34.dp, 16.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.10f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(54.dp, 14.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.15f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50))
                .background(if (vm.isPlaying) Color.Red else Color(0xFF707070)))
        }
    }
}

@Composable
private fun blueFace(vm: PlayerViewModel, progress: Float, rotation: Float, modifier: Modifier) {
    Box(modifier.background(Brush.verticalGradient(listOf(Blue, BlueDark)))) {
        verticalLabel("SONY", 20.sp, Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
        verticalLabel("WALKMAN", 17.sp, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))

        Column(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.ArrowLeft, null, tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(6.dp))
            cassetteWindow(vm, progress, rotation)
            Spacer(Modifier.weight(1f))
            transportRow(vm)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun verticalLabel(text: String, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier) {
    Box(modifier) {
        Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = size,
            modifier = Modifier.rotate(-90f))
    }
}

@Composable
private fun cassetteWindow(vm: PlayerViewModel, progress: Float, rotation: Float) {
    Box(
        Modifier.fillMaxHeight(0.66f).aspectRatio(1.7f, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414))
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            // cream label strip with the track title
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Cream)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(vm.now?.title ?: "No Tape", color = Color(0xFF262626),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                reel(1f - progress, rotation, Modifier.weight(1f).aspectRatio(1f))
                Box(
                    Modifier.size(58.dp, 36.dp).clip(RoundedCornerShape(4.dp)).background(Cream),
                    contentAlignment = Alignment.Center
                ) {
                    Text(time(vm.positionMs), color = Color(0xFF333333),
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                reel(progress, rotation, Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }
}

/** Big reel with a wound-tape disc (scaled by `fill`) and a 3-spoke hub that spins. */
@Composable
private fun reel(fill: Float, rotation: Float, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF1A130D), radius = r, center = c)
        drawCircle(
            brush = Brush.linearGradient(listOf(Color(0xFF47301F), Color(0xFF241710))),
            radius = r * (0.5f + 0.45f * fill.coerceIn(0f, 1f)), center = c
        )
        drawCircle(Color.White.copy(alpha = 0.08f), radius = r, center = c, style = Stroke(width = 2f))
        rotate(rotation, c) {
            drawCircle(Cream, radius = r * 0.42f, center = c)
            for (i in 0..2) {
                rotate(i * 120f, c) {
                    drawRoundRect(
                        color = Color(0xFF1E1A14),
                        topLeft = Offset(c.x - r * 0.045f, c.y - r * 0.42f),
                        size = Size(r * 0.09f, r * 0.26f),
                        cornerRadius = CornerRadius(r * 0.04f)
                    )
                }
            }
            drawCircle(Color(0xFF4D4D4D), radius = r * 0.10f, center = c)
        }
    }
}

@Composable
private fun transportRow(vm: PlayerViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tKey(Icons.Filled.FastRewind) { vm.previous() }
        tKey(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, orange = true, big = true) { vm.playPause() }
        tKey(Icons.Filled.FastForward) { vm.next() }
        tKey(Icons.Filled.Stop) { vm.stop() }
    }
}

@Composable
private fun tKey(icon: ImageVector, orange: Boolean = false, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = if (big) 70.dp else 56.dp, height = 46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.verticalGradient(
                    if (orange) listOf(Orange, Orange.copy(alpha = 0.8f)) else listOf(Silver, SilverDark)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (orange) Color.White else Color(0xFF38383A),
            modifier = Modifier.size(if (big) 30.dp else 26.dp))
    }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
