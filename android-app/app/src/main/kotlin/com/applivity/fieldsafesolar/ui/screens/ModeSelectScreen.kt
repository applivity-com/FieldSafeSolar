package com.applivity.fieldsafesolar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.ui.navigation.Route
import com.applivity.fieldsafesolar.ui.theme.FieldSafeColors

@Composable
fun ModeSelectScreen(navController: NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
        // Landscape layout: title left, mode buttons right
        Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {

            // Left: app title + tagline
            Column(
                modifier = Modifier.weight(0.35f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "FIELDSAFE",
                    color = FieldSafeColors.Primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "SOLAR",
                    color = FieldSafeColors.OnBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "AI · Voice · Offline",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Select your role\nto begin",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Right: mode + inspection type selection
            Column(
                modifier = Modifier.weight(0.65f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Mode cards row
                Row(
                    modifier = Modifier.fillMaxWidth().weight(0.45f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModeCard(
                        label = "WORKER",
                        subtitle = "Compliance & safety checks",
                        color = FieldSafeColors.Primary,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                            navController.navigate(Route.PreTaskPlan.createRoute("worker"))
                        }
                    )
                    ModeCard(
                        label = "INSPECTOR",
                        subtitle = "Review & audit worker reports",
                        color = FieldSafeColors.Warning,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                            navController.navigate(Route.InspectorReviewList.route)
                        }
                    )
                }

                // Past reports + settings row
                Row(
                    modifier = Modifier.fillMaxWidth().weight(0.25f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ActionCard(
                        label = "PAST REPORTS",
                        color = FieldSafeColors.Secondary,
                        textColor = FieldSafeColors.OnSecondary,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { navController.navigate(Route.ReportList.route) }
                    )
                    ActionCard(
                        label = "SETTINGS",
                        color = FieldSafeColors.Secondary,
                        textColor = FieldSafeColors.OnSecondary,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { navController.navigate(Route.Settings.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    label: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FieldSafeColors.Surface)
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ActionCard(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// Inspection type selection screen — shown after choosing Worker or Inspector mode
@Composable
fun InspectionTypeSelectScreen(navController: NavController, mode: String?) {
    val isInspector = mode == "inspector"
    Surface(modifier = Modifier.fillMaxSize(), color = FieldSafeColors.Background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isInspector) "INSPECTOR" else "WORKER",
                    color = if (isInspector) FieldSafeColors.Warning else FieldSafeColors.Primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "  •  Select inspection type",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                ActionCard(
                    label = "BACK",
                    color = FieldSafeColors.Secondary,
                    textColor = FieldSafeColors.OnSecondary,
                    modifier = Modifier.height(48.dp).width(100.dp),
                    onClick = { navController.popBackStack() }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // If inspector mode, show "Review Worker Report" option first
            if (isInspector) {
                InspectionOptionCard(
                    label = "REVIEW WORKER REPORT",
                    subtitle = "Annotate a submitted worker report",
                    color = FieldSafeColors.Warning,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    onClick = { navController.navigate(Route.InspectorReviewList.route) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "— or start a new independent audit —",
                    color = FieldSafeColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Inspection type grid (2 columns landscape)
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InspectionOptionCard(
                        label = "PPE CHECK",
                        subtitle = "Gloves · Hard hat · Eye protection",
                        color = FieldSafeColors.SafeGreen,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            navController.navigate(
                                Route.QuestionSlider.createRoute(InspectionType.PPE_CHECK.name, mode ?: "worker")
                            )
                        }
                    )
                    InspectionOptionCard(
                        label = "WORK AREA CHECK",
                        subtitle = "Hazards · Moisture · Fall risks",
                        color = FieldSafeColors.Primary,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            navController.navigate(
                                Route.QuestionSlider.createRoute(InspectionType.WORK_AREA_CHECK.name, mode ?: "worker")
                            )
                        }
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InspectionOptionCard(
                        label = "INVERTER / PANEL",
                        subtitle = "Lockout/tagout · DC/AC isolation",
                        color = FieldSafeColors.Warning,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            navController.navigate(
                                Route.QuestionSlider.createRoute(InspectionType.INVERTER_PANEL_CHECK.name, mode ?: "worker")
                            )
                        }
                    )
                    InspectionOptionCard(
                        label = "SOLAR COMMISSIONING",
                        subtitle = "IEC 62446-1 · Voc · Isc · Anti-islanding",
                        color = FieldSafeColors.DangerRed,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            navController.navigate(
                                Route.QuestionSlider.createRoute(InspectionType.SOLAR_COMMISSIONING.name, mode ?: "worker")
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectionOptionCard(
    label: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FieldSafeColors.Surface)
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = label,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = FieldSafeColors.OnSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}
