package io.sentry.profiling

import io.sentry.IContinuousProfiler
import io.sentry.ILogger
import io.sentry.IProfileConverter
import io.sentry.ISentryExecutorService
import io.sentry.ProfileLifecycle
import io.sentry.TracesSampler
import io.sentry.protocol.SentryId
import io.sentry.protocol.profiling.SentryProfile
import java.nio.file.Path
import kotlin.test.Test
import org.mockito.kotlin.mock

class ProfilingServiceLoaderTest {
  @Test
  fun loadsProfileConverterStub() {
    val service = ProfilingServiceLoader.loadProfileConverter()
    assert(service is ProfileConverterStub)
  }

  @Test
  fun loadsProfilerStub() {
    val logger = mock<ILogger>()

    val service = ProfilingServiceLoader.loadContinuousProfiler(logger, "", 10, null)
    assert(service is ContinuousProfilerStub)
  }
}

class JavaProfileConverterProviderStub : JavaProfileConverterProvider {
  override fun getProfileConverter(): IProfileConverter? {
    return ProfileConverterStub()
  }
}

class ProfileConverterStub() : IProfileConverter {
  override fun convertFromFile(jfrFilePath: Path): SentryProfile {
    TODO("Not yet implemented")
  }
}

class JavaProfilerProviderStub : JavaContinuousProfilerProvider {
  override fun getContinuousProfiler(
    logger: ILogger?,
    profilingTracesDirPath: String?,
    profilingTracesHz: Int,
    executorService: ISentryExecutorService?,
  ): IContinuousProfiler {
    return ContinuousProfilerStub()
  }
}

class ContinuousProfilerStub() : IContinuousProfiler {
  override fun isRunning(): Boolean {
    TODO("Not yet implemented")
  }

  override fun startProfiler(profileLifecycle: ProfileLifecycle, tracesSampler: TracesSampler) {
    TODO("Not yet implemented")
  }

  override fun stopProfiler(profileLifecycle: ProfileLifecycle) {
    TODO("Not yet implemented")
  }

  override fun close(isTerminating: Boolean) {
    TODO("Not yet implemented")
  }

  override fun reevaluateSampling() {
    TODO("Not yet implemented")
  }

  override fun getProfilerId(): SentryId {
    TODO("Not yet implemented")
  }
}
