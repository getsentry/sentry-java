package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryEnvelopeTest {

    @Test
    fun `deserialize sample envelope with event and two attachments`() {
        val envelopeReader = EnvelopeReader()
        val testFile = this::class.java.classLoader.getResource("envelope-event-attachment.txt")
        val stream = testFile!!.openStream()
        val envelope = envelopeReader.read(stream)
        assertNotNull(envelope)
        assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
        assertEquals(3, envelope.items.count())
        val firstItem = envelope.items.elementAt(0)
        assertEquals(SentryItemType.Event, firstItem.header.type)
        assertEquals("application/json", firstItem.header.contentType)
        assertEquals(107, firstItem.header.length)
        assertEquals(107, firstItem.data.size)
        assertNull(firstItem.header.fileName)
        val secondItem = envelope.items.elementAt(1)
        assertEquals(SentryItemType.Attachment, secondItem.header.type)
        assertEquals("text/plain", secondItem.header.contentType)
        assertEquals(61, secondItem.header.length)
        assertEquals(61, secondItem.data.size)
        assertEquals("attachment.txt", secondItem.header.fileName)
        val thirdItem = envelope.items.elementAt(2)
        assertEquals(SentryItemType.Attachment, thirdItem.header.type)
        assertEquals("text/plain", thirdItem.header.contentType)
        assertEquals(29, thirdItem.header.length)
        assertEquals(29, thirdItem.data.size)
        assertEquals("log.txt", thirdItem.header.fileName)
    }

    @Test
    fun `when envelope is empty, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = mock<InputStream>()
        whenever(stream.read(any())).thenReturn(-1)
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Empty stream.", exception.message)
    }

    @Test
    fun `when envelope has no line break, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{}".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Envelope contains no header.", exception.message)
    }

    @Test
    fun `when envelope terminates with line break, envelope parsed correctly`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n{\"length\":15,\"type\":\"event\"}\n{\"contexts\":{}}\n".toInputStream()

        val envelope = envelopeReader.read(stream)

        assertNotNull(envelope)
        assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
        assertEquals(1, envelope.items.count())
        val firstItem = envelope.items.first()
        assertEquals(SentryItemType.Event, firstItem.header.type)
        assertNull(firstItem.header.contentType)
        assertEquals(15, firstItem.header.length)
        assertEquals(15, firstItem.data.size)
        assertNull(firstItem.header.fileName)
    }

    @Test
    fun `when envelope item length is bigger than the rest of the payload, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n{\"length\":\"3\"}\n{}".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Invalid length for item at index '0'. Item is '66' bytes. There are '65' in the buffer.", exception.message)
    }

    @Test
    fun `when envelope has only a header without line break, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Envelope contains no header.", exception.message)
    }

    @Test
    fun `when envelope has only a header and line break, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Invalid envelope. Item at index '0'. has no header delimiter.", exception.message)
    }

    @Test
    fun `when envelope has the first item missing length, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
{"content_type":"application/json","type":"event"}
{}""".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Item header at index '0' has an invalid value: '0'.", exception.message)
    }

    @Test
    fun `when envelope two items, returns envelope with items`() {
        val envelopeReader = EnvelopeReader()
        val stream = """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
{"type":"event","length":"2"}
{}
{"content_type":"application/octet-stream","type":"attachment","length":"10","filename":"null.bin"}
abcdefghij""".toInputStream()
        val envelope = envelopeReader.read(stream)

        assertNotNull(envelope)
        assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
        assertEquals(2, envelope.items.count())
        val firstItem = envelope.items.first()
        assertEquals(SentryItemType.Event, firstItem.header.type)
        assertNull(firstItem.header.contentType)
        assertEquals(2, firstItem.header.length)
        assertEquals(2, firstItem.data.size)
        assertNull(firstItem.header.fileName)
        val secondItem = envelope.items.last()
        assertEquals(SentryItemType.Attachment, secondItem.header.type)
        assertEquals("application/octet-stream", secondItem.header.contentType)
        assertEquals("null.bin", secondItem.header.fileName)
        assertEquals(10, secondItem.header.length)
        assertEquals(10, secondItem.data.size)
    }

    @Test
    fun `test test`() {
        // no special chars
//        val str = "{\"event_id\":\"203feeea1abc4459be9c8049e7808919\",\"sdk\":{\"name\":\"sentry.dart.flutter\",\"version\":\"4.0.0\",\"packages\":[{\"name\":\"pub:sentry\",\"version\":\"4.0.0\"},{\"name\":\"pub:sentry_flutter\",\"version\":\"4.0.0\"}],\"integrations\":[\"isolateErrorIntegration\",\"flutterErrorIntegration\",\"widgetsBindingIntegration\",\"nativeSdkIntegration\",\"loadAndroidImageListIntegration\",\"loadReleaseIntegration\",\"runZonedGuardedIntegration\"]}}\n" +
//                "{\"content_type\":\"application/json\",\"type\":\"event\",\"length\":2669}\n" +
//                "{\"event_id\":\"203feeea1abc4459be9c8049e7808919\",\"timestamp\":\"2020-12-16T13:08:17.570Z\",\"platform\":\"other\",\"release\":\"io.sentry.samples.flutter@0.1.2+3\",\"dist\":\"3\",\"environment\":\"debug\",\"message\":{\"formatted\":\"==| EXCEPTION CAUGHT BY RENDERING LIBRARY |======================\\nThe following assertion was thrown during layout:\\nA RenderFlex overflowed by 1175 pixels on the right.\\n\\nThe relevant error-causing widget was:\\n  Row\\n  file:///Users/marandaneto/Github/sentry-dart/flutter/example/lib/scaffold_with_ui_error.dart:40:26\\n\\nThe overflowing RenderFlex has an orientation of Axis.horizontal.\\nThe edge of the RenderFlex that is overflowing has been marked in\\nthe rendering with a yellow and black striped pattern. This is\\nusually caused by the contents being too big for the RenderFlex.\\nConsider applying a flex factor (e.g. using an Expanded widget)\\nto force the children of the RenderFlex to fit within the\\navailable space instead of being sized to their natural size.\\nThis is considered an error condition because it indicates that\\nthere is content that cannot be seen. If the content is\\nlegitimately bigger than the available space, consider clipping\\nit with a ClipRect widget before putting it in the flex, or using\\na scrollable container rather than a Flex, like a ListView.\\nThe specific RenderFlex in question is: RenderFlex#08ee1 OVERFLOWING:\\n  parentData: <none> (can use size)\\n  constraints: BoxConstraints(w=300.0, h=500.0)\\n  size: Size(300.0, 500.0)\\n  direction: horizontal\\n  mainAxisAlignment: start\\n  mainAxisSize: max\\n  crossAxisAlignment: center\\n  textDirection: ltr\\n  verticalDirection: down\\n----------------------------------------------------------------------------------------------------\\n=================================================================\\n\"},\"exception\":{\"values\":[{\"type\":\"FlutterError\",\"value\":\"A RenderFlex overflowed by 1175 pixels on the right.\",\"mechanism\":{\"type\":\"FlutterError\",\"handled\":true}}]},\"level\":\"fatal\",\"breadcrumbs\":[{\"timestamp\":\"2020-12-16T13:08:06.157Z\",\"category\":\"navigation\",\"data\":{\"state\":\"didPush\",\"to\":\"/\"},\"level\":\"info\",\"type\":\"navigation\"},{\"timestamp\":\"2020-12-16T13:08:17.247Z\",\"category\":\"navigation\",\"data\":{\"state\":\"didPush\",\"from\":\"/\",\"to\":\"ScaffoldWithUiError\"},\"level\":\"info\",\"type\":\"navigation\"}],\"sdk\":{\"name\":\"sentry.dart.flutter\",\"version\":\"4.0.0\",\"packages\":[{\"name\":\"pub:sentry\",\"version\":\"4.0.0\"},{\"name\":\"pub:sentry_flutter\",\"version\":\"4.0.0\"}],\"integrations\":[\"isolateErrorIntegration\",\"flutterErrorIntegration\",\"widgetsBindingIntegration\",\"nativeSdkIntegration\",\"loadAndroidImageListIntegration\",\"loadReleaseIntegration\",\"runZonedGuardedIntegration\"]}}"

        // special chars ◢ ◤ ═ ╡ ╞
        val str = "{\"event_id\":\"373d12b9c1d94ad399351151aade27b9\",\"sdk\":{\"name\":\"sentry.dart.flutter\",\"version\":\"4.0.0\",\"packages\":[{\"name\":\"pub:sentry\",\"version\":\"4.0.0\"},{\"name\":\"pub:sentry_flutter\",\"version\":\"4.0.0\"}],\"integrations\":[\"isolateErrorIntegration\",\"flutterErrorIntegration\",\"widgetsBindingIntegration\",\"nativeSdkIntegration\",\"loadAndroidImageListIntegration\",\"loadReleaseIntegration\",\"runZonedGuardedIntegration\"]}}\n" +
                "{\"content_type\":\"application/json\",\"type\":\"event\",\"length\":2669}\n" +
                "{\"event_id\":\"373d12b9c1d94ad399351151aade27b9\",\"timestamp\":\"2020-12-16T13:17:37.851Z\",\"platform\":\"other\",\"release\":\"io.sentry.samples.flutter@0.1.2+3\",\"dist\":\"3\",\"environment\":\"debug\",\"message\":{\"formatted\":\"══╡ EXCEPTION CAUGHT BY RENDERING LIBRARY ╞══════════════════════\\nThe following assertion was thrown during layout:\\nA RenderFlex overflowed by 1175 pixels on the right.\\n\\nThe relevant error-causing widget was:\\n  Row\\n  file:///Users/marandaneto/Github/sentry-dart/flutter/example/lib/scaffold_with_ui_error.dart:40:26\\n\\nThe overflowing RenderFlex has an orientation of Axis.horizontal.\\nThe edge of the RenderFlex that is overflowing has been marked in\\nthe rendering with a yellow and black striped pattern. This is\\nusually caused by the contents being too big for the RenderFlex.\\nConsider applying a flex factor (e.g. using an Expanded widget)\\nto force the children of the RenderFlex to fit within the\\navailable space instead of being sized to their natural size.\\nThis is considered an error condition because it indicates that\\nthere is content that cannot be seen. If the content is\\nlegitimately bigger than the available space, consider clipping\\nit with a ClipRect widget before putting it in the flex, or using\\na scrollable container rather than a Flex, like a ListView.\\nThe specific RenderFlex in question is: RenderFlex#dfd91 OVERFLOWING:\\n  parentData: <none> (can use size)\\n  constraints: BoxConstraints(w=300.0, h=500.0)\\n  size: Size(300.0, 500.0)\\n  direction: horizontal\\n  mainAxisAlignment: start\\n  mainAxisSize: max\\n  crossAxisAlignment: center\\n  textDirection: ltr\\n  verticalDirection: down\\n◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤◢◤\\n═════════════════════════════════════════════════════════════════\\n\"},\"exception\":{\"values\":[{\"type\":\"FlutterError\",\"value\":\"A RenderFlex overflowed by 1175 pixels on the right.\",\"mechanism\":{\"type\":\"FlutterError\",\"handled\":true}}]},\"level\":\"fatal\",\"breadcrumbs\":[{\"timestamp\":\"2020-12-16T13:17:07.168Z\",\"category\":\"navigation\",\"data\":{\"state\":\"didPush\",\"to\":\"/\"},\"level\":\"info\",\"type\":\"navigation\"},{\"timestamp\":\"2020-12-16T13:17:37.533Z\",\"category\":\"navigation\",\"data\":{\"state\":\"didPush\",\"from\":\"/\",\"to\":\"ScaffoldWithUiError\"},\"level\":\"info\",\"type\":\"navigation\"}],\"sdk\":{\"name\":\"sentry.dart.flutter\",\"version\":\"4.0.0\",\"packages\":[{\"name\":\"pub:sentry\",\"version\":\"4.0.0\"},{\"name\":\"pub:sentry_flutter\",\"version\":\"4.0.0\"}],\"integrations\":[\"isolateErrorIntegration\",\"flutterErrorIntegration\",\"widgetsBindingIntegration\",\"nativeSdkIntegration\",\"loadAndroidImageListIntegration\",\"loadReleaseIntegration\",\"runZonedGuardedIntegration\"]}}"

        val array = str.toByteArray()
        val str2 = String(array, charset("UTF-8"))
        assertEquals(str.length, str2.length)

        val envelopeReader = EnvelopeReader()
//        val testFile = this::class.java.classLoader.getResource("new-envelope.txt")
//        val stream = testFile!!.openStream()
        val envelope = envelopeReader.read(str2.toInputStream())
        assertNotNull(envelope)
    }
}
