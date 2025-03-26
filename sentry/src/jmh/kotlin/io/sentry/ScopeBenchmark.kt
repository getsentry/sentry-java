package io.sentry

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole


@State(Scope.Benchmark)
open class ScopeBenchmark {

    @Benchmark
    fun ctor(bh: Blackhole) {
        val options = SentryOptions.empty()
        bh.consume(Scope(options))
    }

    @Benchmark
    fun getPropagationContext(bh: Blackhole) {
        val options = SentryOptions.empty()
        val scope = Scope(options)
        bh.consume(scope.propagationContext.sampleRand)
    }
}
