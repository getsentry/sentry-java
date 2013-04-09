package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;

import java.io.IOException;

public class ExceptionInterfaceBinding implements InterfaceBinding<ExceptionInterface> {
    private static final String TYPE_PARAMETER = "type";
    private static final String VALUE_PARAMETER = "value";
    private static final String MODULE_PARAMETER = "module";

    @Override
    public void writeInterface(JsonGenerator generator, ExceptionInterface exceptionInterface) throws IOException {
        ImmutableThrowable throwable = exceptionInterface.getThrowable();

        generator.writeStartObject();
        generator.writeStringField(TYPE_PARAMETER, throwable.getActualClass().getSimpleName());
        generator.writeStringField(VALUE_PARAMETER, throwable.getMessage());
        generator.writeStringField(MODULE_PARAMETER, throwable.getActualClass().getPackage().getName());
        generator.writeEndObject();
    }
}
