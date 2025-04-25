/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.protocol.jfr.jfr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JfrClass extends Element {
    final int id;
    final boolean simpleType;
    final String name;
    final List<JfrField> fields;

    JfrClass(Map<String, String> attributes) {
        this.id = Integer.parseInt(attributes.get("id"));
        this.simpleType = "true".equals(attributes.get("simpleType"));
        this.name = attributes.get("name");
        this.fields = new ArrayList<>(2);
    }

    @Override
    void addChild(Element e) {
        if (e instanceof JfrField) {
            fields.add((JfrField) e);
        }
    }

    public JfrField field(String name) {
        for (JfrField field : fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }
}
