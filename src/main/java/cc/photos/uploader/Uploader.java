package cc.photos.uploader;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AbstractPromptReceiver;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class Uploader {
  private static final Logger LOG = LoggerFactory.getLogger(Uploader.class);

  public static void main(String[] args) throws Exception {
    LOG.info("GPhoto Uploader Started.");
    Uploader u = new Uploader();
    Credential c = u.authorize();
    LOG.info( "Credential expires in secs: {}", c.getExpiresInSeconds());
    u.upload(c, args[0]);
  }

  private void upload(Credential credential, String mediaPath) throws IOException {
    String uploadId = uploadBytes(credential, mediaPath);
    LOG.info("uploadId: {}", uploadId);

  }

  private String uploadBytes(Credential credential, String mediaPath) throws IOException {
    File fromFile = new File(mediaPath);
    byte[] mediaBytes = readBytes(fromFile);
    HttpResponse<String> jsonResponse;
    try {
      jsonResponse = Unirest.post("https://photoslibrary.googleapis.com/v1/uploads")
          .header("content-type", "application/octet-stream")
          .header("accept", "application/json")
          .header("authorization", format("Bearer %s", credential.getAccessToken()))
          .header("X-Goog-Upload-File-Name", mediaPath)
          .body(mediaBytes)
          .asString();
    } catch (UnirestException e) {
      throw new IOException(e);
    }
    LOG.info("response: {} [{}]", jsonResponse.getStatusText(), jsonResponse.getStatus());
    return jsonResponse.getBody();
  }

  private byte[] readBytes(File mediaPath) throws IOException {
    try(InputStream is = new FileInputStream(mediaPath)) {
      return IOUtils.toByteArray(is);
    }
  }

  private Credential authorize() throws Exception {
    JsonFactory jsonFactory =  new JacksonFactory();
    ClientSecret clientSecret = readClientSecret(jsonFactory);
    AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
        BearerToken.authorizationHeaderAccessMethod(),
        new NetHttpTransport(),
        jsonFactory,
        clientSecret.getTokenUri(),
        clientSecret.getAuthParameters(),
        clientSecret.getClientId(),
        clientSecret.getAuthUri().build()).setScopes(singletonList(
        // List of scopes:
        // https://developers.google.com/photos/library/guides/authentication-authorization?authuser=1
        "https://www.googleapis.com/auth/photoslibrary"
    )).setDataStoreFactory(new FileDataStoreFactory(new File("./datastore"))).build();

    VerificationCodeReceiver receiver = new MyReceiver();
    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
  }

  private ClientSecret readClientSecret(JsonFactory jsonFactory) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    String clientSecretJson = "/client-secret.json";

    InputStream is = Uploader.class.getResourceAsStream(clientSecretJson);
    return mapper.readValue(is, ClientSecret.class);
  }

  class MyReceiver extends AbstractPromptReceiver {
    @Override
    public String getRedirectUri() throws IOException {
      return "http://localhost:8000/";
    }
  }
}
