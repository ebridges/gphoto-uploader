package cc.photos.uploader.factory;

import cc.photos.uploader.util.Constants;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.StatusCode;
import com.google.auth.Credentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import org.threeten.bp.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;

/** A factory class that helps initialize a {@link PhotosLibraryClient} instance. */
public class PhotosLibraryClientFactory {
    private static final File DATA_STORE_DIR = new File(Constants.UPLOADER_STORED_CREDENTIALS_DIR);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final int LOCAL_RECEIVER_PORT = 8001;

    private PhotosLibraryClientFactory() {}

    /** Creates a new {@link PhotosLibraryClient} instance with credentials and scopes. */
    public static PhotosLibraryClient createClient(String credentialsPath, List<String> selectedScopes)
            throws IOException, GeneralSecurityException {
        // Create a new retry configuration.
        RetrySettings retrySettings= RetrySettings.newBuilder()
                .setInitialRetryDelay(Duration.ofSeconds(10))
                .setRetryDelayMultiplier(1.5)
                .setMaxAttempts(10)
                .setMaxRetryDelay(Duration.ofSeconds(60))
                .setTotalTimeout(Duration.ofMinutes(15))
                .build();

        // Set the status codes returned from the API that should be retried.
        Set<StatusCode.Code> retryableCodes=
                Set.of(StatusCode.Code.RESOURCE_EXHAUSTED, StatusCode.Code.DEADLINE_EXCEEDED, StatusCode.Code.UNAVAILABLE);

        PhotosLibrarySettings.Builder librarySettingsBuilder=
                PhotosLibrarySettings.newBuilder()
                        .setCredentialsProvider(
                                FixedCredentialsProvider.create(
                                        getUserCredentials(credentialsPath, selectedScopes)));

        librarySettingsBuilder.createAlbumSettings()
                .setRetrySettings(retrySettings)
                .setRetryableCodes(retryableCodes);

        librarySettingsBuilder.batchCreateMediaItemsSettings()
                .setRetrySettings(retrySettings)
                .setRetryableCodes(retryableCodes);

        return PhotosLibraryClient.initialize(librarySettingsBuilder.build());
    }

    private static Credentials getUserCredentials(String credentialsPath, List<String> selectedScopes)
            throws IOException, GeneralSecurityException {
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(
                        JSON_FACTORY, new InputStreamReader(new FileInputStream(credentialsPath)));
        String clientId = clientSecrets.getDetails().getClientId();
        String clientSecret = clientSecrets.getDetails().getClientSecret();

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        JSON_FACTORY,
                        clientSecrets,
                        selectedScopes)
                        .setDataStoreFactory(new FileDataStoreFactory(DATA_STORE_DIR))
                        .setAccessType("offline")
                        .build();
        LocalServerReceiver receiver =
                new LocalServerReceiver.Builder().setPort(LOCAL_RECEIVER_PORT).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        return UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(credential.getRefreshToken())
                .build();
    }
}