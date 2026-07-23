package com.rameshta.formready.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rameshta.formready.R

@Composable
fun HomeScreen(
    onPreparePhoto: () -> Unit,
    onPrepareSignature: () -> Unit,
    onPreparePdf: () -> Unit,
    onScanDocument: () -> Unit,
) {
    val capabilities = listOf(
        R.string.capability_photo_title to R.string.capability_photo_description,
        R.string.capability_signature_title to R.string.capability_signature_description,
        R.string.capability_pdf_title to R.string.capability_pdf_description,
        R.string.capability_scanner_title to R.string.capability_scanner_description,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.privacy_card_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.privacy_card_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(capabilities) { capability ->
            CapabilityCard(
                titleRes = capability.first,
                descriptionRes = capability.second,
                onClick = when (capability.first) {
                    R.string.capability_photo_title -> onPreparePhoto
                    R.string.capability_signature_title -> onPrepareSignature
                    R.string.capability_pdf_title -> onPreparePdf
                    R.string.capability_scanner_title -> onScanDocument
                    else -> null
                },
            )
        }
        item {
            Text(
                text = stringResource(R.string.independent_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CapabilityCard(
    titleRes: Int,
    descriptionRes: Int,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
