package com.example.volunteersApp.ui.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.wallet.flagFromCountryName

private fun defaultCountryLabel(country: String): String {
    val flag = flagFromCountryName(country)
    return if (flag.isNotBlank()) "$flag $country" else country
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableGlobalCountryDropdown(
    selectedCountry: String,
    countries: List<String>,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Country",
    enabled: Boolean = true,
    countryDisplayLabel: (String) -> String = ::defaultCountryLabel,
    countrySearchTerms: (String) -> String = { it }
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCountries = remember(countries, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            countries
        } else {
            countries.filter { country ->
                country.contains(query, ignoreCase = true) ||
                    countrySearchTerms(country).contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(expanded) {
        if (!expanded) searchQuery = ""
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) expanded = !expanded
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = countryDisplayLabel(selectedCountry),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled
                )
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search country") }
            )

            if (filteredCountries.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No countries found") },
                    onClick = {},
                    enabled = false
                )
            } else {
                filteredCountries.forEach { country ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = countryDisplayLabel(country),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onCountrySelected(country)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
