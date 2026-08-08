package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.RedPrimary

val DEFAULT_LEGAZPI_BARANGAYS = listOf(
    CachedBarangayEntity("PH-ALB-LEG-001", "Barangay 1 - Em's Barrio", "LEGAZPI CITY", "ALBAY", 131391000, 1237438000),
    CachedBarangayEntity("PH-ALB-LEG-002", "Barangay 2 - Bitano", "LEGAZPI CITY", "ALBAY", 131430000, 1237450000),
    CachedBarangayEntity("PH-ALB-LEG-003", "Barangay 3 - Gogon", "LEGAZPI CITY", "ALBAY", 131480000, 1237400000),
    CachedBarangayEntity("PH-ALB-LEG-004", "Barangay 4 - Sagpon", "LEGAZPI CITY", "ALBAY", 131350000, 1237380000),
    CachedBarangayEntity("PH-ALB-LEG-005", "Barangay 5 - Albay District", "LEGAZPI CITY", "ALBAY", 131320000, 1237350000),
    CachedBarangayEntity("PH-ALB-LEG-006", "Barangay 6 - Bañadero", "LEGAZPI CITY", "ALBAY", 131410000, 1237480000),
    CachedBarangayEntity("PH-ALB-LEG-007", "Barangay 7 - Oro Site", "LEGAZPI CITY", "ALBAY", 131370000, 1237410000),
    CachedBarangayEntity("PH-ALB-LEG-008", "Barangay 8 - Bogtong", "LEGAZPI CITY", "ALBAY", 131550000, 1237490000),
    CachedBarangayEntity("PH-ALB-LEG-009", "Barangay 9 - Rawis", "LEGAZPI CITY", "ALBAY", 131620000, 1237550000),
    CachedBarangayEntity("PH-ALB-LEG-010", "Barangay 10 - Arimbay", "LEGAZPI CITY", "ALBAY", 131700000, 1237590000)
)

@Composable
fun BarangaySelectorDialog(
    barangays: List<CachedBarangayEntity> = DEFAULT_LEGAZPI_BARANGAYS,
    onDismissRequest: () -> Unit,
    onBarangaySelected: (CachedBarangayEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(searchQuery, barangays) {
        val listToSearch = if (barangays.isNotEmpty()) barangays else DEFAULT_LEGAZPI_BARANGAYS
        if (searchQuery.isBlank()) listToSearch
        else listToSearch.filter {
            it.barangayName.contains(searchQuery, ignoreCase = true) ||
            it.barangayCode.contains(searchQuery, ignoreCase = true)
        }
    }

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = AgapShape.xl,
            color = cardBg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(AgapSpacing.xs)
                .border(width = 1.dp, color = borderCol, shape = AgapShape.xl)
        ) {
            Column(
                modifier = Modifier
                    .padding(AgapSpacing.lg)
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                Text(
                    text = "SELECT BARANGAY REFERENCE",
                    color = RedPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Selecting a Barangay provides approximate coordinates for field responders without requiring Internet.",
                    color = subTextColor,
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Barangay Name or Code") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AgapShape.sm
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredList) { brgy ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBarangaySelected(brgy)
                                    onDismissRequest()
                                },
                            shape = AgapShape.xs
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = brgy.barangayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        text = "${brgy.cityMunicipality}, ${brgy.province} • Approx Ref point",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = subTextColor
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("CANCEL", color = subTextColor)
                    }
                }
            }
        }
    }
}
