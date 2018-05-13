package cc.photos.uploader;

import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.util.Key;

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

  Web() {
  }
}
