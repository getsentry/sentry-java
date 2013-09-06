package net.kencochrane.raven.marshaller;

import net.kencochrane.raven.event.Event;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Marshaller allows to serialise a {@link Event} and sends over a stream.
 */
public interface Marshaller {
    /**
     * Serialises an event and sends it through an {@code OutputStream}.
     * <p>
     * The marshaller should not close the given stream, use {@link UncloseableOutputStream} to prevent automatic calls
     * to {@link OutputStream#close()}.
     * </p>
     *
     * @param event       event to serialise.
     * @param destination destination stream.
     */
    void marshall(Event event, OutputStream destination);

    /**
     * OutputStream delegating every call except for {@link #close()} to an other OutputStream.
     */
    final class UncloseableOutputStream extends OutputStream {
        private final OutputStream originalStream;

        /**
         * Creates an OutputStream which will not delegate the {@link #close()} operation.
         *
         * @param originalStream original stream to encapsulate.
         */
        public UncloseableOutputStream(OutputStream originalStream) {
            this.originalStream = originalStream;
        }

        @Override
        public void write(int b) throws IOException {
            originalStream.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            originalStream.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            originalStream.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            originalStream.flush();
        }

        @Override
        public void close() throws IOException {
        }
    }
}
