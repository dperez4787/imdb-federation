package org.perez_f_daniel.imdb.common.governance;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Google-signed ID tokens for the registration call, minted as the runtime
 * service account via ADC (the Cloud Run metadata server in production).
 * Returns null when ADC is absent or can't mint ID tokens (plain local runs)
 * — the registrar then calls unauthenticated, which a dev policy service
 * with no allowlist accepts.
 */
final class GoogleIdTokens {

  private static final Logger log = LoggerFactory.getLogger(GoogleIdTokens.class);

  private GoogleIdTokens() {}

  static Supplier<String> supplier(String audience) {
    return () -> {
      try {
        GoogleCredentials adc = GoogleCredentials.getApplicationDefault();
        if (!(adc instanceof IdTokenProvider provider)) {
          log.info("ADC credentials cannot mint ID tokens; registering unauthenticated");
          return null;
        }
        IdTokenCredentials credentials =
            IdTokenCredentials.newBuilder()
                .setIdTokenProvider(provider)
                .setTargetAudience(audience)
                .build();
        credentials.refreshIfExpired();
        return credentials.getIdToken().getTokenValue();
      } catch (Exception e) {
        log.info("no Google ID token available ({}); registering unauthenticated", e.getMessage());
        return null;
      }
    };
  }
}
