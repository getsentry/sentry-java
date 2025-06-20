package io.sentry

import io.sentry.clientreport.IClientReportRecorder

class SentryOptionsManipulator {
  companion object {
    fun setClientReportRecorder(
      options: SentryOptions,
      clientReportRecorder: IClientReportRecorder,
    ) {
      options.clientReportRecorder = clientReportRecorder
    }
  }
}
