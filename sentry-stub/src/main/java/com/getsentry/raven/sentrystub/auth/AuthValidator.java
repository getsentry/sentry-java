package com.getsentry.raven.sentrystub.auth;

import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validate a Sentry auth Header (in HTTP {@code X-Sentry-Auth}).
 * <p>
 * The validation of a header goes from the validation of the content to the authorisation check for the given public
 * and secret keys.
 */
public class AuthValidator {
    private static final Logger logger = Logger.getLogger(AuthValidator.class.getCanonicalName());
    private static final Collection<String> SENTRY_PROTOCOL_VERSIONS = Arrays.asList("6");
    private static final String SENTRY_VERSION_PARAMETER = "Sentry sentry_version";
    private static final String PUBLIC_KEY_PARAMETER = "sentry_key";
    private static final String SECRET_KEY_PARAMETER = "sentry_secret";
    private static final String SENTRY_CLIENT_PARAMETER = "sentry_client";
    private final Map<String, String> publicKeySecretKey = new HashMap<>();
    private final Map<String, String> publicKeyProjectId = new HashMap<>();

    /**
     * Adds a user to consider as valid of an Auth header.
     *
     * @param publicKey public key of the user.
     * @param secretKey secret key of the user.
     * @param projectId identifier of the project on which the user is allowed to push events.
     */
    public void addUser(String publicKey, String secretKey, String projectId) {
        if (publicKeySecretKey.containsKey(publicKey) || publicKeyProjectId.containsKey(publicKey)) {
            throw new IllegalArgumentException("There is already a user " + publicKey);
        }

        publicKeySecretKey.put(publicKey, secretKey);
        publicKeyProjectId.put(publicKey, projectId);
    }

    /**
     * Validates an auth header.
     *
     * @param authParameters auth header as a {@code Map}.
     */
    public void validateSentryAuth(Map<String, String> authParameters) {
        InvalidAuthException invalidAuthException = new InvalidAuthException("The auth parameters weren't valid");

        validateVersion(authParameters.get(SENTRY_VERSION_PARAMETER), invalidAuthException);
        validateKeys(authParameters.get(PUBLIC_KEY_PARAMETER), authParameters.get(SECRET_KEY_PARAMETER),
                invalidAuthException);
        validateClient(authParameters.get(SENTRY_CLIENT_PARAMETER), invalidAuthException);

        if (!invalidAuthException.isEmpty()) {
            throw invalidAuthException;
        }
    }

    /**
     * Validates an auth header and the access to a project.
     *
     * @param authParameters auth header as a {@code Map}.
     * @param projectId      identifier of the project being accessed (isn't a part
     * @see #validateSentryAuth(java.util.Map)
     */
    public void validateSentryAuth(Map<String, String> authParameters, String projectId) {
        InvalidAuthException invalidAuthException = new InvalidAuthException("The auth parameters weren't valid");
        try {
            validateSentryAuth(authParameters);
        } catch (InvalidAuthException e) {
            invalidAuthException = e;
        }

        validateProject(authParameters.get(PUBLIC_KEY_PARAMETER), projectId, invalidAuthException);

        if (!invalidAuthException.isEmpty()) {
            throw invalidAuthException;
        }
    }

    /**
     * Validates the version of the protocol given in the Auth header.
     * <p>
     * The only supported versions are listed in {@link #SENTRY_PROTOCOL_VERSIONS}.
     *
     * @param authSentryVersion    version of the Sentry protocol given in the auth header.
     * @param invalidAuthException exception thrown if the auth header is invalid.
     */
    private void validateVersion(String authSentryVersion, InvalidAuthException invalidAuthException) {
        if (authSentryVersion == null || !SENTRY_PROTOCOL_VERSIONS.contains(authSentryVersion)) {
            invalidAuthException.addDetailedMessage("The version '" + authSentryVersion + "' isn't valid, "
                + "only those " + SENTRY_PROTOCOL_VERSIONS + " are supported.");
        }
    }

    /**
     * Validates the public and secret user keys provided in the Auth Header.
     * <p>
     * Valid keys are listed in {@link #publicKeySecretKey}.
     *
     * @param publicKey            public key used to identify a user.
     * @param secretKey            secret key used as a password.
     * @param invalidAuthException exception thrown if the auth header is invalid.
     * @see #addUser(String, String, String)
     */
    private void validateKeys(String publicKey, String secretKey,
                              InvalidAuthException invalidAuthException) {
        if (publicKey == null) {
            invalidAuthException.addDetailedMessage("No public key provided");
        } else if (!publicKeySecretKey.containsKey(publicKey)) {
            invalidAuthException.addDetailedMessage("The public key '" + publicKey + "' isn't associated "
                + "with a secret key.");
        }

        if (secretKey == null) {
            invalidAuthException.addDetailedMessage("No secret key provided");
        }

        if (secretKey != null && publicKey != null && !secretKey.equals(publicKeySecretKey.get(publicKey))) {
            invalidAuthException.addDetailedMessage("The secret key '" + secretKey + "' "
                + "isn't valid for '" + publicKey + "'");
        }
    }

    /**
     * Validates the project and checks if the given user can indeed access the project.
     *
     * @param publicKey            public key used to identify a user.
     * @param projectId            identifier of the project on which the user is allowed to push events.
     * @param invalidAuthException exception thrown if the auth header is invalid.
     * @see #addUser(String, String, String)
     */
    private void validateProject(String publicKey, String projectId, InvalidAuthException invalidAuthException) {
        if (projectId == null) {
            invalidAuthException.addDetailedMessage("No project ID provided");
        }

        if (projectId != null && publicKey != null && !projectId.equals(publicKeyProjectId.get(publicKey))) {
            invalidAuthException.addDetailedMessage("The project '" + projectId + "' "
                + "can't be accessed by ' " + publicKey + " '");
        }
    }

    /**
     * Validates the client part of the header.
     * <p>
     * The client should always be provided.
     *
     * @param client               string identifying a client type (such as Java/3.0)
     * @param invalidAuthException exception thrown if the auth header is invalid.
     */
    private void validateClient(String client, InvalidAuthException invalidAuthException) {
        if (client == null) {
            invalidAuthException.addDetailedMessage("The client name is mandatory.");
        }
    }

    public void loadSentryUsers(String resourceName) {
        Properties sentryProperties = new Properties();

        try(InputStream resourceAsStream = AuthValidator.class.getResourceAsStream(resourceName)) {
            sentryProperties.load(resourceAsStream);
            int userCount = Integer.parseInt(sentryProperties.getProperty("sentry.user.count", "0"));
            for (int i = 1; i <= userCount; i++) {
                String publicKey = sentryProperties.getProperty("sentry.user." + i + ".publicKey");
                String secretKey = sentryProperties.getProperty("sentry.user." + i + ".secretKey");
                String projectId = sentryProperties.getProperty("sentry.user." + i + ".projectId");
                addUser(publicKey, secretKey, projectId);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't load the sentry.properties file", e);
        }
    }

}
