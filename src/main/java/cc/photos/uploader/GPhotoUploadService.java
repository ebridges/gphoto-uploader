package cc.photos.uploader;

import cc.photos.uploader.util.ClientSecret;
import cc.photos.uploader.util.UploadUtils;

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

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static cc.photos.uploader.util.StringUtil.isNotEmpty;
import static cc.photos.uploader.util.UploadUtils.acceptHeader;
import static cc.photos.uploader.util.UploadUtils.contentTypeHeader;
import static cc.photos.uploader.util.UploadUtils.h;
import static cc.photos.uploader.util.UploadUtils.headers;
import static java.util.Collections.singletonList;

@SuppressWarnings("WeakerAccess")
public class GPhotoUploadService {
  private static final Logger LOG = LoggerFactory.getLogger(GPhotoUploadService.class);

  private Credential credential;

  public GPhotoUploadService() {

  }

  public JSONObject addMediaItem(String albumId, String uploadId, Path mediaPath) {
    if(LOG.isDebugEnabled()) {
      LOG.debug("addMediaItem called for {}", mediaPath);
    }

    JSONObject o = new JSONObject();
    JSONObject uploadToken = new JSONObject();
    uploadToken.put("uploadToken", uploadId);
    JSONObject newMediaItems = new JSONObject();
    newMediaItems.put("description", mediaPath);
    newMediaItems.put("simpleMediaItem", uploadToken);
    o.put("newMediaItems", singletonList(newMediaItems));
    o.put("albumId", albumId);

    try(UploadUtils u = UploadUtils.instance()) {
      Map<String, String> headers = headers(
          credential.getAccessToken(),
          contentTypeHeader("application/json")
      );

      if(LOG.isDebugEnabled()) {
        LOG.debug("posting request for addMediaItem: {}", o);
      }
      return u.post("https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate", headers, o, UploadUtils::defaultHandler);
    }
  }

  public String uploadBytes(Path mediaPath) throws IOException {
    if(LOG.isDebugEnabled()) {
      LOG.debug("uploadBytes called for {}", mediaPath);
    }

    byte[] mediaBytes = readBytes(mediaPath);
    try(UploadUtils u = UploadUtils.instance()) {
      Map<String, String> headers = headers(
          credential.getAccessToken(),
          contentTypeHeader("application/octet-stream"),
          h("X-Goog-Upload-File-Name", mediaPath.getFileName().toString()),
          h("X-Goog-Upload-Protocol", "raw")
      );
      if(LOG.isDebugEnabled()) {
        LOG.debug("posting request for uploadBytes: {}", mediaPath);
      }
      UploadUtils.ResponseHandler<String,String> handler = (r) -> {
        String uploadToken = null;
        if(r.getBody() != null) {
          if(LOG.isDebugEnabled()) {
            LOG.debug("returning uploadToken from body");
          }
          uploadToken = r.getBody();
        } else {
          if(LOG.isDebugEnabled()) {
            LOG.debug("no upload token found, file has already been uploaded.");
          }
        }
        return uploadToken;
      };
      return u.post( "https://photoslibrary.googleapis.com/v1/uploads", headers, mediaBytes, handler);
    }
  }

  private static byte[] readBytes(Path mediaPath) throws IOException {
    try(InputStream is = Files.newInputStream(mediaPath)) {
      return IOUtils.toByteArray(is);
    }
  }

  public String createAlbum(String albumName) {
    if(LOG.isDebugEnabled()) {
      LOG.debug("Creating an album with name: {}", albumName);
    }

    JSONObject response;

    JSONObject album = new JSONObject();
    JSONObject title = new JSONObject();
    album.put("album", title);
    title.put("title", albumName);

    try(UploadUtils u = UploadUtils.instance()) {
      Map<String, String> headers = headers(
          credential.getAccessToken(),
          contentTypeHeader("application/json")
      );

      if(LOG.isDebugEnabled()) {
        LOG.debug("posting request for createAlbum: {}", albumName);
      }

      response = u.post("https://photoslibrary.googleapis.com/v1/albums", headers, album, UploadUtils::defaultHandler);

      if(response.has("id")) {
        if(LOG.isDebugEnabled()) {
          LOG.debug("album {} created successfully", albumName);
        }
        return response.getString("id");
      } else {
        if(LOG.isDebugEnabled()) {
          LOG.debug("album not created. response was: {}", response);
        }
        return null;
      }
    }
  }

  public Map<String,String> listAlbums() {
    if(LOG.isDebugEnabled()) {
      LOG.debug("listAlbums() called.");
    }
    JSONObject response;
    String nextPageToken = null;
    Map<String,String> albums = new HashMap<>();

    try(UploadUtils u = UploadUtils.instance()) {
      do {
        String url = "https://photoslibrary.googleapis.com/v1/albums?pageSize=50";
        if (isNotEmpty(nextPageToken)) {
          url += "&pageToken=" + nextPageToken;
        }

        Map<String, String> headers = headers(
            credential.getAccessToken(),
            acceptHeader("application/json")
        );

        response = u.get(url, headers, UploadUtils::defaultHandler);

        if (response.has("albums")) {
          populateAlbums(albums, response.getJSONArray("albums"));
          if (response.has("nextPageToken")) {
            nextPageToken = response.getString("nextPageToken");
          }
        } else {
          // end of pagination
          nextPageToken = null;
        }
      } while (nextPageToken != null);
    }

    if(LOG.isDebugEnabled()) {
      LOG.debug("albums: {}", albums);
    }
    return albums;
  }

  private void populateAlbums(Map<String,String> albums, JSONArray albumInfo) {
    if(LOG.isDebugEnabled()) {
      LOG.debug("populateAlbums called.");
    }
    for (int i=0; i<albumInfo.length(); i++) {
      JSONObject item = albumInfo.getJSONObject(i);
      String title = item.getString("title");
      String id = item.getString("id");
      if(LOG.isDebugEnabled()) {
        LOG.debug("title: {}", title);
      }
      albums.put(title, id);
    }
  }

  public void authorize(String secretsFile) throws IOException {
    if(LOG.isDebugEnabled()) {
      LOG.debug("authorize() called.");
    }
    JsonFactory jsonFactory =  new JacksonFactory();
    ClientSecret clientSecret = readClientSecret(secretsFile);
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

    VerificationCodeReceiver receiver = new LocalhostReceiver();
    AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver);
    this.credential = app.authorize("user");
    LOG.info( "Upload authorized. Credential expires in: {}s", credential.getExpiresInSeconds());
  }

  private ClientSecret readClientSecret(String secretsFile) throws IOException {
      ObjectMapper mapper = new ObjectMapper();

      try (InputStream is = new BufferedInputStream(new FileInputStream(secretsFile))) {
        return mapper.readValue(is, ClientSecret.class);
      }
  }

  class LocalhostReceiver extends AbstractPromptReceiver {
      @Override
      @SuppressWarnings("RedundantThrows")
      public String getRedirectUri() throws IOException {
          return "http://localhost:8000/";
      }
  }
}
