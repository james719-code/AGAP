package com.travelingsalesman.agap.ui.screens.guest

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Flood
import androidx.compose.material.icons.outlined.Landslide
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Tsunami
import androidx.compose.material.icons.outlined.Volcano
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.travelingsalesman.agap.ui.theme.BrandAmber
import com.travelingsalesman.agap.ui.theme.BrandRed
import com.travelingsalesman.agap.ui.theme.BrandTeal
import com.travelingsalesman.agap.ui.theme.PurpleCrime
import com.travelingsalesman.agap.ui.theme.PurpleDisaster
import com.travelingsalesman.agap.ui.theme.PurpleVolunteer

/** Offline preparedness card shown to guests. Content is general public guidance. */
internal data class SafetyGuide(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val accent: Color,
    val steps: List<String>
)

/** Hazards people across Bicol face most often. */
internal val bicolSafetyGuides = listOf(
    SafetyGuide(
        title = "Typhoon",
        summary = "Bicol sits on the main typhoon path",
        icon = Icons.Outlined.Cyclone,
        accent = BrandTeal,
        steps = listOf(
            "Follow PAGASA wind signals and your LGU's announcements.",
            "Charge phones and power banks. Store water and food for at least three days.",
            "Tie down loose roofing and objects, then stay indoors away from windows.",
            "Evacuate early if you live near a river, the coast, or a steep slope."
        )
    ),
    SafetyGuide(
        title = "Volcano",
        summary = "Mayon, Bulusan and other active volcanoes",
        icon = Icons.Outlined.Volcano,
        accent = BrandRed,
        steps = listOf(
            "Know whether your barangay is inside a permanent danger zone.",
            "Follow PHIVOLCS alert levels and leave as soon as officials order evacuation.",
            "During ashfall, stay indoors, close windows, and wear an N95 mask or damp cloth.",
            "Keep a go-bag ready with water, food, medicine, a flashlight, and documents."
        )
    ),
    SafetyGuide(
        title = "Flood and lahar",
        summary = "Rivers and volcano channels rise fast",
        icon = Icons.Outlined.Flood,
        accent = PurpleCrime,
        steps = listOf(
            "Move to higher ground as soon as water starts rising.",
            "Never walk or drive through moving water. Even ankle-deep water can knock you down.",
            "Stay away from river channels below volcanoes during heavy rain. Lahar can arrive with little warning.",
            "Switch off electricity at the main if water is entering your home."
        )
    ),
    SafetyGuide(
        title = "Storm surge",
        summary = "Coastal towns along all six provinces",
        icon = Icons.Outlined.Tsunami,
        accent = PurpleDisaster,
        steps = listOf(
            "Take storm surge warnings seriously. The sea can rise several metres in minutes.",
            "Leave low-lying coastal areas before the typhoon arrives, not during it.",
            "Go inland or to a sturdy evacuation centre on higher ground.",
            "If you feel a strong earthquake near the coast, move inland right away."
        )
    ),
    SafetyGuide(
        title = "Landslide",
        summary = "Common on slopes after long rain",
        icon = Icons.Outlined.Landslide,
        accent = BrandAmber,
        steps = listOf(
            "Watch for new cracks in the ground, tilting trees, or muddy streams.",
            "Listen for rumbling or cracking sounds and leave the slope immediately.",
            "Move away from the path of the slide, not downhill in front of it.",
            "Stay away from the area after a slide. More earth can follow."
        )
    ),
    SafetyGuide(
        title = "Fire",
        summary = "Get out first, then call",
        icon = Icons.Outlined.LocalFireDepartment,
        accent = BrandRed,
        steps = listOf(
            "Get out and stay out. Do not stop for belongings.",
            "Stay low under smoke and feel doors for heat before opening them.",
            "If your clothes catch fire: stop, drop, and roll.",
            "Call 911 once you are safely outside."
        )
    ),
    SafetyGuide(
        title = "Hands-only CPR",
        summary = "For an adult who collapses",
        icon = Icons.Outlined.MonitorHeart,
        accent = PurpleVolunteer,
        steps = listOf(
            "Check if the person responds and is breathing normally.",
            "Call 911, or ask someone nearby to call.",
            "Push hard and fast in the centre of the chest, 100 to 120 times a minute.",
            "Keep going until help arrives or the person starts breathing."
        )
    )
)
