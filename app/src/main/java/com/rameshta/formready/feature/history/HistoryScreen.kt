package com.rameshta.formready.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rameshta.formready.R
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.ProcessingJob
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.ui.component.EmptyStateScreen
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    jobs: List<ProcessingJob>,
    artifactsByJob: Map<String, OutputArtifact>,
    onRepeat: (ProcessingJob) -> Unit,
) {
    if (jobs.isEmpty()) {
        EmptyStateScreen(
            titleRes = R.string.history_title,
            bodyRes = R.string.history_empty,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        items(jobs, key = { it.id }) { job ->
            val artifact = artifactsByJob[job.id.toString()]
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = when (job.type) {
                            JobType.PHOTO -> stringResource(R.string.history_photo_job)
                            JobType.SIGNATURE -> stringResource(R.string.capability_signature_title)
                            JobType.PDF -> stringResource(R.string.capability_pdf_title)
                            JobType.VALIDATION -> stringResource(R.string.history_validation_job)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.history_status, job.status.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        DateFormat.getDateTimeInstance().format(
                            Date(job.updatedAtEpochMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    job.errorCode?.let { error ->
                        Text(
                            stringResource(R.string.history_error, error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    artifact?.let {
                        Text(
                            stringResource(
                                R.string.history_output_details,
                                it.widthPx ?: 0,
                                it.heightPx ?: 0,
                                it.byteCount,
                                it.readiness.name,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (job.type == JobType.PHOTO) {
                        TextButton(onClick = { onRepeat(job) }) {
                            Text(stringResource(R.string.history_repeat_photo))
                        }
                    }
                }
            }
        }
    }
}
