package net.kencochrane.raven.sentrystub.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AuthValidator {
    private static final Collection<String> SENTRY_PROTOCOL_VERSIONS = Arrays.asList("3");
    private static final String SENTRY_VERSION_PARAMETER = "Sentry sentry_version";
    private static final String PUBLIC_KEY_PARAMETER = "sentry_key";
    private static final String SECRET_KEY_PARAMETER = "sentry_secret";
    private static final String SENTRY_CLIENT_PARAMETER = "sentry_client";
    private final Map<String, String> publicKeySecretKey = new HashMap<String, String>();
    private final Map<String, String> publicKeyProjectId = new HashMap<String, String>();

    public void addUser(String publicKey, String secretKey, String projectId) {
        if (publicKeySecretKey.containsKey(publicKey) || publicKeyProjectId.containsKey(publicKey)) {
            throw new IllegalArgumentException("There is already a user " + publicKey);
        }

        publicKeySecretKey.put(publicKey, secretKey);
        publicKeyProjectId.put(publicKey, projectId);
    }

    public void validateSentryAuth(Map<String, String> authParameters, String projectId) {
        InvalidAuthException invalidAuthException = new InvalidAuthException("The auth parameters weren't valid");

        validateVersion(authParameters.get(SENTRY_VERSION_PARAMETER), invalidAuthException);
        validateKeys(authParameters.get(PUBLIC_KEY_PARAMETER), authParameters.get(SECRET_KEY_PARAMETER),
                invalidAuthException);
        validateProject(authParameters.get(PUBLIC_KEY_PARAMETER), projectId, invalidAuthException);
        validateClient(authParameters.get(SENTRY_CLIENT_PARAMETER), invalidAuthException);

        if (!invalidAuthException.isEmpty())
            throw invalidAuthException;
    }

    private void validateVersion(String authSentryVersion, InvalidAuthException invalidAuthException) {
        if (authSentryVersion == null || !SENTRY_PROTOCOL_VERSIONS.contains(authSentryVersion))
            invalidAuthException.addDetailedMessage("The version '" + authSentryVersion + "' isn't valid, " +
                    "only those " + SENTRY_PROTOCOL_VERSIONS + " are supported.");
    }

    private void validateKeys(String publicKey, String secretKey,
                              InvalidAuthException invalidAuthException) {
        if (publicKey == null)
            invalidAuthException.addDetailedMessage("No public key provided");
        else if (!publicKeySecretKey.containsKey(publicKey))
            invalidAuthException.addDetailedMessage("The public key '" + publicKey + "' isn't associated " +
                    "with a secret key.");

        if (secretKey == null)
            invalidAuthException.addDetailedMessage("No secret key provided");

        if (secretKey != null && publicKey != null && !secretKey.equals(publicKeySecretKey.get(publicKey)))
            invalidAuthException.addDetailedMessage("The secret key '" + secretKey + "' " +
                    "isn't valid for '" + publicKey + "'");
    }

    private void validateProject(String publicKey, String projectId, InvalidAuthException invalidAuthException) {
        if (projectId == null)
            invalidAuthException.addDetailedMessage("No project ID provided");

        if (projectId != null && publicKey != null && !projectId.equals(publicKeyProjectId.get(publicKey)))
            invalidAuthException.addDetailedMessage("The project '" + projectId + "' " +
                    "can't be accessed by ' " + publicKey + " '");
    }

    private void validateClient(String client, InvalidAuthException invalidAuthException) {
        if (client == null)
            invalidAuthException.addDetailedMessage("The client name is mandatory.");
    }
}
