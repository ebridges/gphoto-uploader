package cc.photos.uploader.util;

import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.util.Key;

/*
E.g.:

{
   "web" : {
      "client_id" : "abcdef",
      "auth_provider_x509_cert_url" : "https://www.googleapis.com/oauth2/v1/certs",
      "client_secret" : "abcdef",
      "project_id" : "abcdef",
      "redirect_uris" : [
         "http://localhost:8000/"
      ],
      "auth_uri" : "https://accounts.google.com/o/oauth2/auth",
      "token_uri" : "https://accounts.google.com/o/oauth2/token"
   }
}
 */

public class ClientSecret {
  @Key public Web web;

  public ClientSecret() {
    this.web = new Web();
  }

  public ClientParametersAuthentication getAuthParameters() {
    return new ClientParametersAuthentication(
        this.web.client_id,
        this.web.client_secret
    );
  }

  public String getClientId() {
    return this.web.client_id;
  }

  public GenericUrl getAuthUri() {
    return new GenericUrl(this.web.auth_uri);
  }

  public GenericUrl getTokenUri() {
    return new GenericUrl(this.web.token_uri);
  }
}

class Web {
  @Key public String client_id;
  @Key public String client_secret;
  @Key public String project_id;
  @Key public String auth_uri;
  @Key public String token_uri;
  @Key public String auth_provider_x509_cert_url;
  @Key public String[] redirect_uris;

  Web() {
  }
}
