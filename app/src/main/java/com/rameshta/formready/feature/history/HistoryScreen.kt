package com.rameshta.formready.feature.history

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rameshta.formready.R
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.ProcessingJob
import com.rameshta.formready.core.model.Readiness
import com.rameshta.formready.ui.component.EmptyStateScreen
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HistoryScreen(
    jobs: List<ProcessingJob>,
    artifactsByJob: Map<String, OutputArtifact>,
    onRepeat: (ProcessingJob) -> Unit,
    onFavourite: (ProcessingJob, Boolean) -> Unit,
    onDelete: (ProcessingJob) -> Unit,
    onDeleteOutputAndHistory: (ProcessingJob, OutputArtifact) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<JobType?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDeletion by remember {
        mutableStateOf<Pair<ProcessingJob, OutputArtifact?>?>(null)
    }
    val filtered = jobs.filter { job ->
        (filter == null || job.type == filter) &&
            (query.isBlank() ||
                job.type.name.contains(query, true) ||
                job.status.name.contains(query, true) ||
                job.errorCode?.contains(query, true) == true)
    }.sortedWith(compareByDescending<ProcessingJob> { it.isFavourite }.thenByDescending {
        it.updatedAtEpochMillis
    })
    if (jobs.isEmpty()) {
        EmptyStateScreen(R.string.history_title, R.string.history_empty)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                label = { Text(stringResource(R.string.history_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text(stringResource(R.string.history_all)) },
                )
                JobType.entries.take(3).forEach { type ->
                    FilterChip(
                        selected = filter == type,
                        onClick = { filter = type },
                        label = { Text(jobTypeLabel(type)) },
                    )
                }
            }
            Button(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.history_clear))
            }
        }
        items(filtered, key = { it.id }) { job ->
            HistoryCard(
                job,
                artifactsByJob[job.id.toString()],
                onRepeat,
                onFavourite,
                onDelete = { pendingDeletion = it to null },
                onDeleteOutputAndHistory = { job, artifact ->
                    pendingDeletion = job to artifact
                },
            )
        }
    }
    if (confirmClear) {
        ConfirmHistoryDialog(
            title = stringResource(R.string.history_clear),
            message = stringResource(R.string.settings_clear_history_confirm),
            onConfirm = {
                onClear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
    pendingDeletion?.let { (job, artifact) ->
        ConfirmHistoryDialog(
            title = stringResource(
                if (artifact == null) R.string.history_delete else R.string.history_delete_output,
            ),
            message = stringResource(
                if (artifact == null) {
                    R.string.history_delete_confirm
                } else {
                    R.string.history_delete_output_confirm
                },
            ),
            onConfirm = {
                if (artifact == null) onDelete(job) else onDeleteOutputAndHistory(job, artifact)
                pendingDeletion = null
            },
            onDismiss = { pendingDeletion = null },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryCard(
    job: ProcessingJob,
    artifact: OutputArtifact?,
    onRepeat: (ProcessingJob) -> Unit,
    onFavourite: (ProcessingJob, Boolean) -> Unit,
    onDelete: (ProcessingJob) -> Unit,
    onDeleteOutputAndHistory: (ProcessingJob, OutputArtifact) -> Unit,
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(jobTypeLabel(job.type), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_status, jobStatusLabel(job.status)))
            Text(DateFormat.getDateTimeInstance().format(Date(job.updatedAtEpochMillis)))
            job.errorCode?.let { Text(stringResource(R.string.history_error, it)) }
            artifact?.let {
                Text(
                    stringResource(
                        R.string.history_output_details,
                        it.widthPx ?: 0,
                        it.heightPx ?: 0,
                        it.byteCount,
                        readinessLabel(it.readiness),
                    ),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(onClick = {
                        openArtifact(context, it, Intent.ACTION_VIEW)
                    }) { Text(stringResource(R.string.action_open)) }
                    TextButton(onClick = {
                        openArtifact(context, it, Intent.ACTION_SEND)
                    }) { Text(stringResource(R.string.action_share)) }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = { onRepeat(job) }) {
                    Text(stringResource(R.string.history_repeat))
                }
                TextButton(onClick = { onFavourite(job, !job.isFavourite) }) {
                    Text(
                        stringResource(
                            if (job.isFavourite) {
                                R.string.history_unfavourite
                            } else {
                                R.string.history_favourite
                            },
                        ),
                    )
                }
                TextButton(onClick = { onDelete(job) }) {
                    Text(stringResource(R.string.history_delete))
                }
            }
            if (artifact != null) {
                TextButton(onClick = { onDeleteOutputAndHistory(job, artifact) }) {
                    Text(stringResource(R.string.history_delete_output))
                }
            }
        }
    }
}

@Composable
private fun jobTypeLabel(type: JobType): String = stringResource(
    when (type) {
        JobType.PHOTO -> R.string.preset_type_photo
        JobType.SIGNATURE -> R.string.preset_type_signature
        JobType.PDF -> R.string.preset_type_pdf
        JobType.VALIDATION -> R.string.history_validation_job
    },
)

@Composable
private fun jobStatusLabel(status: JobStatus): String = stringResource(
    when (status) {
        JobStatus.QUEUED -> R.string.job_status_queued
        JobStatus.RUNNING -> R.string.job_status_running
        JobStatus.SUCCEEDED -> R.string.job_status_succeeded
        JobStatus.FAILED -> R.string.job_status_failed
        JobStatus.CANCELLED -> R.string.job_status_cancelled
    },
)

@Composable
private fun readinessLabel(readiness: Readiness): String = stringResource(
    when (readiness) {
        Readiness.READY -> R.string.readiness_ready
        Readiness.READY_WITH_WARNINGS -> R.string.readiness_ready_with_warnings
        Readiness.NOT_READY -> R.string.readiness_not_ready
    },
)

@Composable
private fun ConfirmHistoryDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_keep)) }
        },
    )
}

private fun openArtifact(
    context: android.content.Context,
    artifact: OutputArtifact,
    action: String,
) {
    runCatching {
        val root = context.filesDir.canonicalFile
        val file = File(root, artifact.uri).canonicalFile
        check(file.path.startsWith("${root.path}${File.separator}") && file.isFile)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val intent = if (action == Intent.ACTION_SEND) {
            Intent(Intent.ACTION_SEND)
                .setType(artifact.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
        } else {
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, artifact.mimeType)
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}
