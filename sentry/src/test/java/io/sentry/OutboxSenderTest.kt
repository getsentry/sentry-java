package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.cache.EnvelopeCache
import io.sentry.hints.Flushable
import io.sentry.hints.Resettable
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.transport.ITransport
import io.sentry.util.HintUtils
import io.sentry.util.thread.NoOpThreadChecker
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Date
import java.util.concurrent.Callable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OutboxSenderTest {
  private class Fixture {

    val options = mock<SentryOptions>()
    val scopes = mock<IScopes>()
    var envelopeReader = mock<IEnvelopeReader>()
    val serializer = mock<ISerializer>()
    val logger = mock<ILogger>()

    init {
      whenever(options.dsn).thenReturn("https://key@sentry.io/proj")
      whenever(options.dateProvider).thenReturn(SentryNanotimeDateProvider())
      whenever(options.threadChecker).thenReturn(NoOpThreadChecker.getInstance())
      whenever(options.continuousProfiler).thenReturn(NoOpContinuousProfiler.getInstance())
      whenever(scopes.options).thenReturn(this.options)
    }

    fun getSut(): OutboxSender {
      return OutboxSender(scopes, envelopeReader, serializer, logger, 15000, 30)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `attachments on both sides of a parent travel together through the client`() {
    for (isTransaction in listOf(false, true)) {
      val transport = mock<ITransport>()
      val options =
        SentryOptions().apply {
          dsn = "https://key@sentry.io/proj"
          maxAttachmentSize = 1024
          setTransportFactory { _, _ -> transport }
        }
      val serializer = JsonSerializer(options)
      val reader = EnvelopeReader(serializer)
      val scopes = mock<IScopes>()
      whenever(scopes.options).thenReturn(options)
      val traceContext =
        TraceContext(SentryId(), "key", null, null, null, "outbox", "0.5", "true", null, "0.25")
      val parent =
        if (isTransaction) {
          SentryTransaction(SentryTracer(TransactionContext("outbox", "test"), scopes))
        } else {
          SentryEvent()
        }
      val attachments =
        (1..4).map {
          SentryEnvelopeItem(
            SentryEnvelopeItemHeader(
              SentryItemType.Attachment,
              { 3 },
              "application/octet-stream",
              "$it.dmp",
              "event.minidump",
            ),
            byteArrayOf(it.toByte(), 0, -1),
          )
        }
      val envelope =
        SentryEnvelope(
          SentryEnvelopeHeader(parent.eventId, null, traceContext),
          attachments.take(2) +
            SentryEnvelopeItem.fromEvent(serializer, parent) +
            attachments.drop(2),
        )
      val file = File.createTempFile("outbox", ".envelope")
      file.outputStream().use { serializer.serialize(envelope, it) }
      val hint = HintUtils.createWithTypeCheckHint(mock<Retryable>())
      val snapshots = mutableListOf<List<Attachment>>()
      val client = SentryClient(options)
      whenever(scopes.captureEvent(any(), any<Hint>())).thenAnswer {
        val captureHint = it.getArgument<Hint>(1)
        snapshots.add(captureHint.attachments)
        client.captureEvent(it.getArgument<SentryEvent>(0), null, captureHint)
      }
      whenever(scopes.captureTransaction(any(), any(), any())).thenAnswer {
        val transaction = it.getArgument<SentryTransaction>(0)
        assertThat(transaction.samplingDecision!!.sampled).isTrue()
        assertThat(transaction.samplingDecision!!.sampleRate).isEqualTo(0.5)
        assertThat(transaction.samplingDecision!!.sampleRand).isEqualTo(0.25)
        val captureHint = it.getArgument<Hint>(2)
        snapshots.add(captureHint.attachments)
        client.captureTransaction(
          transaction,
          it.getArgument<TraceContext>(1),
          null,
          captureHint,
          null,
        )
      }

      OutboxSender(scopes, reader, serializer, options.logger, 15000, 30)
        .processEnvelopeFile(file.absolutePath, hint)

      assertThat(snapshots).hasSize(1)
      assertThat(snapshots.single().map { it.filename })
        .containsExactly("1.dmp", "2.dmp", "3.dmp", "4.dmp")
        .inOrder()
      val sent = argumentCaptor<SentryEnvelope>()
      verify(transport).send(sent.capture(), eq(hint))
      val bytes = ByteArrayOutputStream()
      serializer.serialize(sent.firstValue, bytes)
      val parsed = reader.read(bytes.toByteArray().inputStream())!!
      assertThat(parsed.header.eventId).isEqualTo(parent.eventId)
      val items = parsed.items.toList()
      assertThat(items).hasSize(5)
      assertThat(items.first().header.type)
        .isEqualTo(if (isTransaction) SentryItemType.Transaction else SentryItemType.Event)
      assertThat(
          serializer
            .deserialize(
              items.first().data.inputStream().reader(),
              if (isTransaction) SentryTransaction::class.java else SentryEvent::class.java,
            )!!
            .eventId
        )
        .isEqualTo(parent.eventId)
      if (isTransaction) {
        assertThat(parsed.header.traceContext!!.traceId).isEqualTo(traceContext.traceId)
        assertThat(parsed.header.traceContext!!.sampleRate).isEqualTo("0.5")
        assertThat(parsed.header.traceContext!!.sampleRand).isEqualTo("0.25")
      }
      items.drop(1).forEachIndexed { index, item ->
        assertThat(item.data).isEqualTo(attachments[index].data)
        assertThat(item.header.fileName).isEqualTo("${index + 1}.dmp")
        assertThat(item.header.contentType).isEqualTo("application/octet-stream")
        assertThat(item.header.attachmentType).isEqualTo("event.minidump")
      }
      verify(scopes, never()).captureEnvelope(any(), any())
      assertThat(hint.attachments).isEmpty()
      assertThat(file.exists()).isFalse()
      client.close()
    }
  }

  @Test
  fun `serialization drops oversized and unreadable grouped attachments but keeps valid items`() {
    val transport = mock<ITransport>()
    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        maxAttachmentSize = 4
        setTransportFactory { _, _ -> transport }
      }
    val serializer = JsonSerializer(options)
    val event = SentryEvent()
    val attachments =
      listOf(4, 5, 4).mapIndexed { index, size ->
        SentryEnvelopeItem(
          SentryEnvelopeItemHeader(SentryItemType.Attachment, { size }, "text/plain", "$index.txt"),
          ByteArray(size) { index.toByte() },
        )
      }
    val broken =
      SentryEnvelopeItem(
        SentryEnvelopeItemHeader(SentryItemType.Attachment, { 1 }, null, "broken"),
        Callable<ByteArray> { throw IOException("unreadable attachment") },
      )
    whenever(fixture.envelopeReader.read(any()))
      .thenReturn(
        SentryEnvelope(
          event.eventId,
          null,
          attachments + broken + SentryEnvelopeItem.fromEvent(serializer, event),
        )
      )
    val client = SentryClient(options)
    whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer {
      client.captureEvent(it.getArgument<SentryEvent>(0), null, it.getArgument<Hint>(1))
    }
    OutboxSender(fixture.scopes, fixture.envelopeReader, serializer, fixture.logger, 15000, 30)
      .processEnvelopeFile(getTempEnvelope(), HintUtils.createWithTypeCheckHint(mock<Retryable>()))

    val sent = argumentCaptor<SentryEnvelope>()
    verify(transport).send(sent.capture(), any())
    val bytes = ByteArrayOutputStream()
    serializer.serialize(sent.firstValue, bytes)
    val parsed = EnvelopeReader(serializer).read(bytes.toByteArray().inputStream())!!
    assertThat(parsed.header.eventId).isEqualTo(event.eventId)
    assertThat(parsed.items.map { it.header.type })
      .containsExactly(SentryItemType.Event, SentryItemType.Attachment, SentryItemType.Attachment)
      .inOrder()
    assertThat(parsed.items.drop(1).map { it.header.fileName })
      .containsExactly("0.txt", "2.txt")
      .inOrder()
    assertThat(parsed.items.drop(1).map { it.data.size }).containsExactly(4, 4)
    verify(fixture.scopes, never()).captureEnvelope(any(), any())
    client.close()
  }

  @Test
  fun `filtered parent does not send standalone attachments`() {
    val transport = mock<ITransport>()
    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        maxAttachmentSize = 1024
        setTransportFactory { _, _ -> transport }
        setBeforeSend { _, _ -> null }
      }
    val serializer = JsonSerializer(options)
    val event = SentryEvent()
    val envelope =
      SentryEnvelope(
        event.eventId,
        null,
        listOf(
          SentryEnvelopeItem.fromEvent(serializer, event),
          SentryEnvelopeItem.fromAttachment(
            serializer,
            options.logger,
            Attachment(byteArrayOf(1), "crash.dmp"),
            options.maxAttachmentSize,
          ),
        ),
      )
    val file = File.createTempFile("outbox", ".envelope")
    file.outputStream().use { serializer.serialize(envelope, it) }
    val client = SentryClient(options)
    whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer {
      client.captureEvent(it.getArgument<SentryEvent>(0), null, it.getArgument<Hint>(1))
    }
    OutboxSender(fixture.scopes, EnvelopeReader(serializer), serializer, fixture.logger, 15000, 30)
      .processEnvelopeFile(file.absolutePath, HintUtils.createWithTypeCheckHint(mock<Retryable>()))

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
    verify(transport, never()).send(any(), any())
    verify(fixture.scopes, never()).captureEnvelope(any(), any())
    client.close()
  }

  @Test
  fun `invalid event and transaction parents suppress their attachments`() {
    for (type in listOf(SentryItemType.Event, SentryItemType.Transaction)) {
      for (nullParent in listOf(false, true)) {
        val fixture = Fixture()
        val event = SentryEvent()
        val transaction =
          SentryTransaction(SentryTracer(TransactionContext("outbox", "test"), fixture.scopes))
        whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java)))
          .thenReturn(if (nullParent) null else event)
        whenever(fixture.serializer.deserialize(any(), eq(SentryTransaction::class.java)))
          .thenReturn(if (nullParent) null else transaction)
        val attachment =
          SentryEnvelopeItem(
            SentryEnvelopeItemHeader(SentryItemType.Attachment, { 1 }, null, "crash.dmp"),
            byteArrayOf(1),
          )
        val parent =
          SentryEnvelopeItem(
            SentryEnvelopeItemHeader(type, { 2 }, "application/json", null),
            "{}".toByteArray(),
          )
        whenever(fixture.envelopeReader.read(any()))
          .thenReturn(SentryEnvelope(SentryId(), null, listOf(attachment, parent, attachment)))

        fixture
          .getSut()
          .processEnvelopeFile(
            getTempEnvelope(),
            HintUtils.createWithTypeCheckHint(mock<Retryable>()),
          )

        verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
        verify(fixture.scopes, never()).captureTransaction(any(), any(), any())
        verify(fixture.scopes, never()).captureEnvelope(any(), any())
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), anyVararg<Any>())
      }
    }
  }

  @Test
  fun `attachment only and multiple parent envelopes retain per-item dispatch`() {
    for (parentCount in listOf(0, 2)) {
      val fixture = Fixture()
      val event = SentryEvent()
      whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)
      val attachments =
        (1..2).map {
          SentryEnvelopeItem(
            SentryEnvelopeItemHeader(SentryItemType.Attachment, { 1 }, null, "$it.dmp"),
            byteArrayOf(it.toByte()),
          )
        }
      val parents =
        (0 until parentCount).map {
          SentryEnvelopeItem(
            SentryEnvelopeItemHeader(SentryItemType.Event, { 2 }, "application/json", null),
            "{}".toByteArray(),
          )
        }
      whenever(fixture.envelopeReader.read(any()))
        .thenReturn(SentryEnvelope(event.eventId, null, attachments + parents))
      val hint = HintUtils.createWithTypeCheckHint(mock<Retryable>())
      val snapshots = mutableListOf<List<Attachment>>()
      whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer {
        snapshots.add(it.getArgument<Hint>(1).attachments)
        event.eventId
      }

      fixture.getSut().processEnvelopeFile(getTempEnvelope(), hint)

      val captured = argumentCaptor<SentryEnvelope>()
      verify(fixture.scopes, times(2)).captureEnvelope(captured.capture(), eq(hint))
      assertThat(captured.allValues.flatMap { it.items })
        .containsExactlyElementsIn(attachments)
        .inOrder()
      verify(fixture.scopes, times(parentCount)).captureEvent(eq(event), eq(hint))
      assertThat(snapshots.flatten()).isEmpty()
    }
  }

  @Test
  fun `grouped attachments restore hint across mixed items and reused files`() {
    val event = SentryEvent()
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)
    val attachment =
      SentryEnvelopeItem(
        SentryEnvelopeItemHeader(SentryItemType.Attachment, { 1 }, null, "crash.dmp"),
        byteArrayOf(1),
      )
    val unnamed =
      SentryEnvelopeItem(
        SentryEnvelopeItemHeader(SentryItemType.Attachment, { 1 }, null, null),
        byteArrayOf(2),
      )
    val feedback =
      SentryEnvelopeItem(
        SentryEnvelopeItemHeader(SentryItemType.UserFeedback, { 2 }, "application/json", null),
        "{}".toByteArray(),
      )
    val parent =
      SentryEnvelopeItem(
        SentryEnvelopeItemHeader(SentryItemType.Event, { 2 }, "application/json", null),
        "{}".toByteArray(),
      )
    // An absent envelope ID must still allow a valid parent ID.
    whenever(fixture.envelopeReader.read(any()))
      .thenReturn(
        SentryEnvelope(null, null, listOf(attachment, parent, feedback, unnamed)),
        SentryEnvelope(null, null, listOf(parent)),
      )
    val hint = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    val original = Attachment(byteArrayOf(3), "original")
    hint.addAttachment(original)
    val snapshots = mutableListOf<List<Attachment>>()
    whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer {
      snapshots.add(it.getArgument<Hint>(1).attachments)
      event.eventId
    }
    whenever(fixture.scopes.captureEnvelope(any(), any())).thenAnswer {
      assertThat(it.getArgument<Hint>(1).attachments).containsExactly(original)
      event.eventId
    }

    repeat(2) { fixture.getSut().processEnvelopeFile(getTempEnvelope(), hint) }

    assertThat(snapshots.map { snapshot -> snapshot.map { it.filename } })
      .containsExactly(listOf("original", "crash.dmp"), listOf("original"))
      .inOrder()
    assertThat(hint.attachments).containsExactly(original)
    val captured = argumentCaptor<SentryEnvelope>()
    verify(fixture.scopes, times(2)).captureEnvelope(captured.capture(), eq(hint))
    assertThat(captured.allValues.flatMap { it.items }).containsExactly(feedback, unnamed).inOrder()
  }

  @Test
  fun `grouped attachments do not add flushes or resets and parent failure preserves retry`() {
    for (outcome in listOf("success", "timeout", "failure", "exception")) {
      val fixture = Fixture()
      val event = SentryEvent()
      whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)
      val attachment =
        SentryEnvelopeItem(
          SentryEnvelopeItemHeader(SentryItemType.Attachment, { 1 }, null, "crash.dmp"),
          byteArrayOf(1),
        )
      val parent =
        SentryEnvelopeItem(
          SentryEnvelopeItemHeader(SentryItemType.Event, { 2 }, "application/json", null),
          "{}".toByteArray(),
        )
      val feedback =
        SentryEnvelopeItem(
          SentryEnvelopeItemHeader(SentryItemType.UserFeedback, { 2 }, "application/json", null),
          "{}".toByteArray(),
        )
      whenever(fixture.envelopeReader.read(any()))
        .thenReturn(
          SentryEnvelope(event.eventId, null, listOf(attachment, parent, attachment, feedback))
        )
      val sdkHint =
        mock<Retryable>(
          extraInterfaces = arrayOf(Flushable::class, SubmissionResult::class, Resettable::class)
        )
      whenever(sdkHint.isRetry).thenReturn(outcome != "success")
      whenever((sdkHint as Flushable).waitFlush()).thenReturn(outcome != "timeout")
      whenever((sdkHint as SubmissionResult).isSuccess).thenReturn(outcome == "success")
      val hint = HintUtils.createWithTypeCheckHint(sdkHint)
      val original = Attachment(byteArrayOf(2), "original")
      hint.addAttachment(original)
      val snapshots = mutableListOf<List<Attachment>>()
      whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer {
        snapshots.add(it.getArgument<Hint>(1).attachments)
        if (outcome == "exception") throw IllegalStateException("capture failed")
        event.eventId
      }
      val path = getTempEnvelope()

      fixture.getSut().processEnvelopeFile(path, hint)

      assertThat(snapshots.single().map { it.filename })
        .containsExactly("original", "crash.dmp", "crash.dmp")
        .inOrder()
      assertThat(hint.attachments).containsExactly(original)
      assertThat(HintUtils.getSentrySdkHint(hint)).isSameInstanceAs(sdkHint)
      verify(
          sdkHint as Flushable,
          times(if (outcome == "success") 2 else if (outcome == "exception") 0 else 1),
        )
        .waitFlush()
      verify(sdkHint as Resettable, times(if (outcome == "success") 2 else 0)).reset()
      verify(fixture.scopes, times(if (outcome == "success") 1 else 0))
        .captureEnvelope(any(), eq(hint))
      assertThat(File(path).exists()).isEqualTo(outcome != "success")
      File(path).delete()
    }
  }

  private fun getTempEnvelope(fileName: String = "envelope-event-attachment.txt"): String {
    val testFile = this::class.java.classLoader.getResource(fileName)
    val testFileBytes = testFile!!.readBytes()
    val targetFile = File.createTempFile("temp-envelope", ".tmp")
    Files.write(Paths.get(targetFile.toURI()), testFileBytes)
    return targetFile.absolutePath
  }

  @Test
  fun `when envelopeReader returns null, file is deleted `() {
    whenever(fixture.envelopeReader.read(any())).thenReturn(null)
    val sut = fixture.getSut()
    val path = getTempEnvelope()
    assertTrue(File(path).exists()) // sanity check

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)
    assertFalse(File(path).exists())
    // Additionally make sure we have a error logged
    verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
  }

  @Test
  fun `when parser is EnvelopeReader and serializer returns SentryEvent, event captured, file is deleted `() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
    val expected = SentryEvent(SentryId("9ec79c33-ec99-42ab-8353-589fcb2e04dc"), Date())
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java)))
      .thenReturn(expected)
    val sut = fixture.getSut()
    val path = getTempEnvelope()
    assertTrue(File(path).exists()) // sanity check

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    verify(fixture.scopes).captureEvent(eq(expected), any<Hint>())
    assertFalse(File(path).exists())
    // Additionally make sure we have no errors logged
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
  }

  @Test
  fun `when parser is EnvelopeReader and serializer return SentryTransaction, transaction captured, transactions sampled, file is deleted`() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
    whenever(fixture.options.maxSpans).thenReturn(1000)
    whenever(fixture.scopes.options).thenReturn(fixture.options)
    whenever(fixture.options.transactionProfiler).thenReturn(NoOpTransactionProfiler.getInstance())

    val transactionContext = TransactionContext("fixture-name", "http")
    transactionContext.description = "fixture-request"
    transactionContext.status = SpanStatus.OK
    transactionContext.setTag("fixture-tag", "fixture-value")

    val sentryTracer = SentryTracer(transactionContext, fixture.scopes)
    val span = sentryTracer.startChild("child")
    span.finish(SpanStatus.OK)
    sentryTracer.finish()

    val sentryTracerSpy = spy(sentryTracer)
    whenever(sentryTracerSpy.eventId).thenReturn(SentryId("3367f5196c494acaae85bbbd535379ac"))

    val expected = SentryTransaction(sentryTracerSpy)
    whenever(fixture.serializer.deserialize(any(), eq(SentryTransaction::class.java)))
      .thenReturn(expected)

    val sut = fixture.getSut()
    val path = getTempEnvelope(fileName = "envelope-transaction.txt")
    assertTrue(File(path).exists())

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertEquals(expected, it)
          assertTrue(it.isSampled)
        },
        any(),
        any(),
      )
    assertFalse(File(path).exists())

    // Additionally make sure we have no errors logged
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
  }

  @Test
  fun `restores sampleRate`() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
    whenever(fixture.options.maxSpans).thenReturn(1000)
    whenever(fixture.scopes.options).thenReturn(fixture.options)
    whenever(fixture.options.transactionProfiler).thenReturn(NoOpTransactionProfiler.getInstance())

    val transactionContext = TransactionContext("fixture-name", "http")
    transactionContext.description = "fixture-request"
    transactionContext.status = SpanStatus.OK
    transactionContext.setTag("fixture-tag", "fixture-value")
    transactionContext.samplingDecision = TracesSamplingDecision(true, 0.00000021, 0.021)

    val sentryTracer = SentryTracer(transactionContext, fixture.scopes)
    val span = sentryTracer.startChild("child")
    span.finish(SpanStatus.OK)
    sentryTracer.finish()

    val sentryTracerSpy = spy(sentryTracer)
    whenever(sentryTracerSpy.eventId).thenReturn(SentryId("3367f5196c494acaae85bbbd535379ac"))

    val expected = SentryTransaction(sentryTracerSpy)
    whenever(fixture.serializer.deserialize(any(), eq(SentryTransaction::class.java)))
      .thenReturn(expected)

    val sut = fixture.getSut()
    val path = getTempEnvelope(fileName = "envelope-transaction-with-sample-rand.txt")
    assertTrue(File(path).exists())

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertEquals(expected, it)
          assertTrue(it.isSampled)
          assertEquals(0.00000021, it.samplingDecision?.sampleRate)
          assertEquals(0.021, it.samplingDecision?.sampleRand)
          assertTrue(it.samplingDecision!!.sampled)
        },
        check {
          assertEquals("b156a475de54423d9c1571df97ec7eb6", it.traceId.toString())
          assertEquals("key", it.publicKey)
          assertEquals("0.00000021", it.sampleRate)
          assertEquals("1.0-beta.1", it.release)
          assertEquals("prod", it.environment)
          assertEquals("usr1", it.userId)
          assertEquals("tx1", it.transaction)
        },
        any(),
      )
    assertFalse(File(path).exists())

    // Additionally make sure we have no errors logged
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
  }

  @Test
  fun `backfills sampleRand`() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
    whenever(fixture.options.maxSpans).thenReturn(1000)
    whenever(fixture.scopes.options).thenReturn(fixture.options)
    whenever(fixture.options.transactionProfiler).thenReturn(NoOpTransactionProfiler.getInstance())

    val transactionContext = TransactionContext("fixture-name", "http")
    transactionContext.description = "fixture-request"
    transactionContext.status = SpanStatus.OK
    transactionContext.setTag("fixture-tag", "fixture-value")
    transactionContext.samplingDecision = TracesSamplingDecision(true, 0.00000021)

    val sentryTracer = SentryTracer(transactionContext, fixture.scopes)
    val span = sentryTracer.startChild("child")
    span.finish(SpanStatus.OK)
    sentryTracer.finish()

    val sentryTracerSpy = spy(sentryTracer)
    whenever(sentryTracerSpy.eventId).thenReturn(SentryId("3367f5196c494acaae85bbbd535379ac"))

    val expected = SentryTransaction(sentryTracerSpy)
    whenever(fixture.serializer.deserialize(any(), eq(SentryTransaction::class.java)))
      .thenReturn(expected)

    val sut = fixture.getSut()
    val path = getTempEnvelope(fileName = "envelope-transaction-with-sample-rate.txt")
    assertTrue(File(path).exists())

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertEquals(expected, it)
          assertTrue(it.isSampled)
          assertEquals(0.00000021, it.samplingDecision?.sampleRate)
          assertNotNull(it.samplingDecision?.sampleRand)
          assertTrue(it.samplingDecision!!.sampled)
        },
        check {
          assertEquals("b156a475de54423d9c1571df97ec7eb6", it.traceId.toString())
          assertEquals("key", it.publicKey)
          assertEquals("0.00000021", it.sampleRate)
          assertEquals("1.0-beta.1", it.release)
          assertEquals("prod", it.environment)
          assertEquals("usr1", it.userId)
          assertEquals("tx1", it.transaction)
        },
        any(),
      )
    assertFalse(File(path).exists())

    // Additionally make sure we have no errors logged
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
  }

  @Test
  fun `when parser is EnvelopeReader and serializer returns SentryEnvelope, event captured, file is deleted `() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))

    val event = SentryEvent(SentryId("9ec79c33ec9942ab8353589fcb2e04dc"), Date())
    val expected = SentryEnvelope(SentryId("9ec79c33ec9942ab8353589fcb2e04dc"), null, setOf())
    whenever(fixture.serializer.deserializeEnvelope(any())).thenReturn(expected)
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)
    val sut = fixture.getSut()
    val path = getTempEnvelope()
    assertTrue(File(path).exists()) // sanity check

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
    assertFalse(File(path).exists())
    // Additionally make sure we have no errors logged
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
  }

  @Test
  fun `when envelope has unknown item type, create and capture an envelope`() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))

    val sut = fixture.getSut()
    val path = getTempEnvelope(fileName = "envelope_attachment.txt")
    assertTrue(File(path).exists()) // sanity check

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    verify(fixture.scopes).captureEnvelope(any(), any())
    assertFalse(File(path).exists())
    // Additionally make sure we have no errors logged
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
    verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
  }

  @Test
  fun `when parser is EnvelopeReader and serializer returns a null event, file error logged, no event captured `() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(null)
    val sut = fixture.getSut()
    val path = getTempEnvelope()
    assertTrue(File(path).exists()) // sanity check

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    // Additionally make sure we have no errors logged
    verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), anyVararg<Any>())
    verify(fixture.scopes, never()).captureEvent(any())
    assertFalse(File(path).exists())
  }

  @Test
  fun `when parser is EnvelopeReader and serializer returns a null envelope, file error logged, no event captured `() {
    fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
    whenever(fixture.serializer.deserializeEnvelope(any())).thenReturn(null)
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(null)
    val sut = fixture.getSut()
    val path = getTempEnvelope()
    assertTrue(File(path).exists()) // sanity check

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(path, hints)

    // Additionally make sure we have no errors logged
    verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), anyVararg<Any>())
    verify(fixture.scopes, never()).captureEvent(any())
    assertFalse(File(path).exists())
  }

  @Test
  fun `when processEnvelopeFile is called with a invalid path, logs error`() {
    val sut = fixture.getSut()

    val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
    sut.processEnvelopeFile(
      File.separator + "i-hope-it-doesnt-exist" + File.separator + "file.txt",
      hints,
    )
    verify(fixture.logger)
      .log(eq(SentryLevel.ERROR), any<String>(), argWhere { it is FileNotFoundException })
  }

  @Test
  fun `when file name is null, should not be relevant`() {
    assertFalse(fixture.getSut().isRelevantFileName(null))
  }

  @Test
  fun `when file name is current prefix, should be ignored`() {
    assertFalse(fixture.getSut().isRelevantFileName(EnvelopeCache.PREFIX_CURRENT_SESSION_FILE))
  }

  @Test
  fun `when file name is startup crash marker, should be ignored`() {
    assertFalse(fixture.getSut().isRelevantFileName(EnvelopeCache.STARTUP_CRASH_MARKER_FILE))
  }

  @Test
  fun `when file name is previous session file, should be ignored`() {
    assertFalse(fixture.getSut().isRelevantFileName(EnvelopeCache.PREFIX_PREVIOUS_SESSION_FILE))
  }

  @Test
  fun `when file name is relevant, should return true`() {
    assertTrue(fixture.getSut().isRelevantFileName("123.envelope"))
  }
}
