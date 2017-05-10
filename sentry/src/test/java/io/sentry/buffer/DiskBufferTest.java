package io.sentry.buffer;

import io.sentry.BaseTest;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class DiskBufferTest extends BaseTest {

    private static File BUFFER_DIR = new File("./sentry-test-buffer-dir");
    private int maxEvents = 2;

    private DiskBuffer buffer;

    @BeforeMethod
    public void setup() {
        buffer = new DiskBuffer(BUFFER_DIR, maxEvents);
    }

    @AfterMethod
    public void teardown() {
        delete(BUFFER_DIR);
    }

    @Test
    public void testAddAndDiscard() throws IOException {
        Breadcrumb breadcrumb = new BreadcrumbBuilder().setMessage("MESSAGE").build();
        List<Breadcrumb> breadcrumbs = Lists.newArrayList();
        breadcrumbs.add(breadcrumb);

        Event event1 = new EventBuilder().withBreadcrumbs(breadcrumbs).build();
        buffer.add(event1);
        // 1 event is buffered
        assertThat(eventCount(buffer.getEvents()), equalTo(1));

        Event event2 = new EventBuilder().build();
        buffer.add(event2);
        // 2 events are buffered
        assertThat(eventCount(buffer.getEvents()), equalTo(2));

        Event event3 = new EventBuilder().build();
        buffer.add(event3);
        // still 2 events, because we hit maxEvents (2)
        assertThat(eventCount(buffer.getEvents()), equalTo(2));

        buffer.discard(event1);
        assertThat(eventCount(buffer.getEvents()), equalTo(1));

        buffer.discard(event2);
        assertThat(eventCount(buffer.getEvents()), equalTo(0));

        // noop, because event3 isn't in the buffer
        buffer.discard(event3);
        assertThat(eventCount(buffer.getEvents()), equalTo(0));
    }

    @Test
    public void testNonEvent() throws IOException {
        // create a file in the buffer dir that dosn't match the event pattern
        File nonEventFile = new File(BUFFER_DIR, "not-an-event");
        assertThat(nonEventFile.createNewFile(), equalTo(true));

        // still 2 events, because only event files are returned
        assertThat(eventCount(buffer.getEvents()), equalTo(0));

        // but the non-event file is definitely still there
        assertThat(BUFFER_DIR.listFiles().length, equalTo(1));
    }

    @Test
    public void testCorruptEventFile() throws IOException {
        // create a file that can't be deserialized for whatever reason
        File corruptEventFile = new File(BUFFER_DIR, "corrupt-event" + DiskBuffer.FILE_SUFFIX);
        assertThat(corruptEventFile.createNewFile(), equalTo(true));

        // the file is there
        assertThat(BUFFER_DIR.listFiles().length, equalTo(1));

        // the file isn't returned because it is invalid
        assertThat(eventCount(buffer.getEvents()), equalTo(0));

        // the file has been deleted so we don't retry it forevr
        assertThat(BUFFER_DIR.listFiles().length, equalTo(0));
    }

    private int eventCount(Iterator<Event> events) {
        int count = 0;
        while (events.hasNext()) {
            events.next();
            count += 1;
        }
        return count;
    }

    private void delete(File dir) {
        if (!dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            for (File c : dir.listFiles()) {
                delete(c);
            }
        }
        if (!dir.delete()) {
            throw new RuntimeException("Failed to delete dir: " + dir);
        }
    }
}
