package cc.photos.uploader;

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
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.apache.http.util.TextUtils.isEmpty;

@SuppressWarnings("WeakerAccess")
public class GPhotoUploadService {
  private static final Logger LOG = LoggerFactory.getLogger(GPhotoUploadService.class);

  private Credential credential;

  public GPhotoUploadService() {

  }


  //    POST https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate
  //    Content-type: application/json
  //    Authorization: Bearer OAUTH2_TOKEN
  //
  //    {
  //      "albumId": "ALBUM_ID",
  //      "newMediaItems": [
  //        {
  //          "description": "ITEM_DESCRIPTIOM",
  //            "simpleMediaItem": {
  //              "uploadToken": "UPLOAD_TOKEN"
  //            }
  //        }
  //      ]
  //    }
  //
  // OK RESPONSE:
  //    {
  //       "newMediaItemResults":[
  //          {
  //             "uploadToken":"CAIS+QIApKFirUvlZW+qNVVRSL/ce61gTWh/U/lOsx1BmjfQNcyygOqfqKUjr9MhnLHAgnM5nGDR1WB98s+++DSEUQ5CDp/1/9wF9VsXp63pPajlQevzU/QMLZN1KUvL0AL43ynHyli42s7F7NIcUtS7Y6RRbEqqjt34ZlFrAkwaj3dHyCW8aMJOLNHyULG7D4HlmAP6oyYfGOwONjIrR2Mk6FDgwyTtifU3JZg+6B/RS2FYR0czd8VurPMUIb/YNNurwd3SyE7TBhDtt8ICBs+N5Ka7EFX55kA90TEGjagrhlVRbpflNZ1udsPaX2ewVUlJafpJMso0CRgQQHf+H2IyN2xxDEp/2mOiiOWrX72FK5KMDZwy7YCkSuU3AxELLb5ltUH596EZIwIAHLqlZkNKXbbgSm3MjwtHmzaN5dApg02bc+t6AF6+AEAQci/xGbeY0hCwTe/4etZvN+huy0bt0nI5futo/lZVqyodPWgeWMz3/nR0TvKU8YVvUA",
  //             "status":{
  //                "message":"OK"
  //             },
  //             "mediaItem":{
  //                "id":"AGj1epUg7grprmyLMqcHuMxULPDLPgyzzWl33fdhW849o8_N9suu1Ixk_04o2PQE36B5ILA_zL_ulLE",
  //                "productUrl":"https://photos.google.com/lr/photo/AGj1epUg7grprmyLMqcHuMxULPDLPgyzzWl33fdhW849o8_N9suu1Ixk_04o2PQE36B5ILA_zL_ulLE",
  //                "mimeType":"image/jpeg",
  //                "mediaMetadata":{
  //                   "creationTime":"2018-05-10T14:52:04Z",
  //                   "width":"4032",
  //                   "height":"3024"
  //                }
  //             }
  //          }
  //       ]
  //    }

  public String addMediaItem(String albumId, String uploadId, String mediaPath) throws IOException {
    HttpResponse<String> jsonResponse;
    try {
      JSONObject o = new JSONObject();
      JSONObject uploadToken = new JSONObject();
      uploadToken.put("uploadToken", uploadId);
      JSONObject newMediaItems = new JSONObject();
      newMediaItems.put("description", "Test Upload: "+mediaPath);
      newMediaItems.put("simpleMediaItem", uploadToken);
      o.put("newMediaItems", singletonList(newMediaItems));
      o.put("albumId", albumId);

      LOG.info("posting request to addMediaItem: {}", o.toString());

      jsonResponse = Unirest.post("https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate")
          .header("content-type", "application/json")
          .header("authorization", format("Bearer %s", credential.getAccessToken()))
          .body(o)
          .asString();

    } catch (UnirestException e) {
      throw new IOException(e);
    }
    LOG.info("response: {} [{}]", jsonResponse.getStatusText(), jsonResponse.getStatus());
    return jsonResponse.getBody();
  }

  public String uploadBytes(String mediaPath) throws IOException {
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

  private static byte[] readBytes(File mediaPath) throws IOException {
    try(InputStream is = new FileInputStream(mediaPath)) {
      return IOUtils.toByteArray(is);
    }
  }


  //  POST https://photoslibrary.googleapis.com/v1/albums
  //  Content-type: application/json
  //  Authorization: Bearer OAUTH2_TOKEN
  //
  //  {
  //    "album": {
  //      "title": "New Album Title"
  //    }
  //  }
  //
  //  OK RESPONSE:
  //  {
  //    "productUrl": "URL_TO_OPEN_IN_GOOGLE_PHOTOS ",
  //    "id": "ALBUM_ID",
  //    "title": "New Album Title",
  //    "isWriteable": "WHETHER_YOU_CAN_WRITE_TO_THIS_ALBUM"
  //  }
  public String createAlbum(String albumName) throws IOException {
    LOG.info("Creating an album with name: {}", albumName);
    HttpResponse<JsonNode> jsonResponse;

    JSONObject album = new JSONObject();
    try {
      JSONObject title = new JSONObject();
      album.put("album", title);
      title.put("title", albumName);

      jsonResponse = Unirest.post("https://photoslibrary.googleapis.com/v1/albums")
          .header("content-type", "application/json")
          .header("authorization", format("Bearer %s", credential.getAccessToken()))
          .body(album)
          .asJson();

    } catch (UnirestException e) {
      throw new IOException(e);
    }
    LOG.info("response: {} [{}]", jsonResponse.getStatusText(), jsonResponse.getStatus());
    int status = jsonResponse.getStatus();
    if(status >= 200 && status < 300) {
      LOG.info("album {} created successfully", albumName);
      return jsonResponse.getBody().getObject().getString("id");
    } else {
      LOG.info("album not created.  response was: ", jsonResponse.getBody().getObject().toString());
      return null;
    }
  }

  public Map<String,String> listAlbums() throws IOException {
    JSONObject responseBody;
    String nextPageToken = null;
    Map<String,String> albums = new HashMap<>();

    do {
      try {
        String url = "https://photoslibrary.googleapis.com/v1/albums?pageSize=50";
        if (!isEmpty(nextPageToken)) {
          url += "&pageToken=" + nextPageToken;
        }
        HttpResponse<JsonNode> jsonResponse = Unirest
            .get(url)
            .header("accept", "application/json")
            .header("authorization", format("Bearer %s", credential.getAccessToken()))
            .asJson();
        LOG.info("response: {} [{}]", jsonResponse.getStatusText(), jsonResponse.getStatus());
        if (jsonResponse.getStatus() >= 400) {
          LOG.error(jsonResponse.getBody().toString());
          throw new IOException("request error: " + jsonResponse.getStatusText());
        }
        responseBody = jsonResponse.getBody().getObject();
      } catch (UnirestException e) {
        throw new IOException(e);
      }
      if (responseBody.has("albums")) {
        populateAlbums(albums, responseBody.getJSONArray("albums"));
        if (responseBody.has("nextPageToken")) {
          nextPageToken = responseBody.getString("nextPageToken");
        }
      } else {
        // end of pagination
        nextPageToken = null;
      }

    } while (nextPageToken != null);

    LOG.info("albums: {}", albums);
    return albums;
  }

  private void populateAlbums(Map<String,String> albums, JSONArray albumInfo) {
    for (int i=0; i<albumInfo.length(); i++) {
      JSONObject item = albumInfo.getJSONObject(i);
      String title = item.getString("title");
      String id = item.getString("id");
      LOG.info("title: {}", title);
      albums.put(title, id);
    }
  }

  public void authorize(String secretsFile) throws IOException {
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
    LOG.info( "Credential expires in secs: {}", credential.getExpiresInSeconds());
  }

  private ClientSecret readClientSecret(String secretsFile) throws IOException {
      ObjectMapper mapper = new ObjectMapper();

      InputStream is = Uploader.class.getResourceAsStream(secretsFile);
      return mapper.readValue(is, ClientSecret.class);
  }

  class LocalhostReceiver extends AbstractPromptReceiver {
      @Override
      @SuppressWarnings("RedundantThrows")
      public String getRedirectUri() throws IOException {
          return "http://localhost:8000/";
      }
  }
}
