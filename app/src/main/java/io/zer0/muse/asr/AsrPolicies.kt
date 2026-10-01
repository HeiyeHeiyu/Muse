package io.zer0.muse.asr

/** HTTP failures worth retrying for chunked ASR transcription requests. */
internal fun isRetryableAsrHttpStatus(statusCode: Int): Boolean =
    statusCode == 429 || statusCode in 500..599

/** Stop should settle normally, but must not erase a failure recorded during the final flush. */
internal fun ASRState.afterAsrStop(): ASRState =
    if (status == ASRStatus.Error) copy(amplitudes = emptyList())
    else copy(status = ASRStatus.Idle, amplitudes = emptyList())

/** Ignore recorder teardown errors caused by an intentional stop or canceled capture job. */
internal fun shouldReportAsrCaptureFailure(status: ASRStatus, recordingJobActive: Boolean): Boolean =
    recordingJobActive && status != ASRStatus.Idle && status != ASRStatus.Error && status != ASRStatus.Stopping

/** Apply a terminal provider/capture failure without discarding the transcript already recognized. */
internal fun ASRState.withAsrFailure(message: String): ASRState =
    copy(status = ASRStatus.Error, errorMessage = message, amplitudes = emptyList())
