package io.sentry.event;

import java.io.Serializable;
import java.util.Set;

/**
 * Represents the current SDK and any integrations used to create an {@link Event}.
 */
public class Sdk implements Serializable {
    /**
     * Name of the SDK.
     */
    private String name;
    /**
     * Version of the SDK.
     */
    private String version;
    /**
     * Set of integrations used.
     */
    private Set<String> integrations;

    /**
     * Build an {@link Sdk} instance.
     *
     * @param name Name of the SDK.
     * @param version Version of the SDK.
     * @param integrations Set of integrations used.
     */
    public Sdk(String name, String version, Set<String> integrations) {
        this.name = name;
        this.version = version;
        this.integrations = integrations;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Set<String> getIntegrations() {
        return integrations;
    }
}
