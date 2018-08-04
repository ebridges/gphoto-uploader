package cc.photos.uploader.util;


import io.github.openunirest.http.HttpMethod;
import io.github.openunirest.http.HttpResponse;
import io.github.openunirest.http.JsonNode;
import io.github.openunirest.http.Unirest;

import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.github.openunirest.http.HttpMethod.GET;
import static io.github.openunirest.http.HttpMethod.POST;
import static java.lang.String.format;

public class UploadUtils implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(UploadUtils.class);

  private static final int SC_TOO_MANY_REQUESTS = 429;
  private static final int SC_5XX = 500;
  private static final int SECONDS_TO_PAUSE_WHEN_TOO_MANY_REQUESTS = 30;


  private UploadUtils(HttpClient client) {
    Unirest.setHttpClient(client);
  }

  public static UploadUtils instance() {
     HttpClient client = HttpClientBuilder.create()
         .setRetryHandler(retryHandler())
         .setConnectionBackoffStrategy(connectionBackoffStrategy())
         .build();
     return new UploadUtils(client);
  }

  private static ConnectionBackoffStrategy connectionBackoffStrategy() {
    return new ConnectionBackoffStrategy() {
      @Override
      public boolean shouldBackoff(final Throwable t) {
        if(LOG.isInfoEnabled()) {
          LOG.info("Caught throwable to rule on backing off: {}", t.getMessage());
        }
        return (t instanceof SocketTimeoutException
            || t instanceof ConnectException);
      }

      @Override
      public boolean shouldBackoff(final org.apache.http.HttpResponse resp) {
        if(LOG.isInfoEnabled()) {
          LOG.info("Response status code to rule on backing off: {}", resp.getStatusLine());
        }
        // https://developers.google.com/photos/library/guides/best-practices#retrying-failed-requests
        return (resp.getStatusLine().getStatusCode() >= SC_5XX);
      }
    };
  }

  private static HttpRequestRetryHandler retryHandler() {
    return (exception, executionCount, context) -> {
      HttpClientContext clientContext = HttpClientContext.adapt(context);
      org.apache.http.HttpResponse response = clientContext.getResponse();
      if(response.getStatusLine().getStatusCode() == SC_TOO_MANY_REQUESTS) {
        // https://developers.google.com/photos/library/guides/best-practices#retrying-failed-requests
        if(LOG.isInfoEnabled()) {
          LOG.info("Too many requests sleeping for {} seconds", SECONDS_TO_PAUSE_WHEN_TOO_MANY_REQUESTS);
        }
        try {
          TimeUnit.SECONDS.sleep(SECONDS_TO_PAUSE_WHEN_TOO_MANY_REQUESTS);
        } catch (InterruptedException e) {
          // ignored
        }
        return true;
      } else {
        return false;
      }
    };
  }


  public JSONObject makeJsonRequest(String method, String url, Map<String, String> headers) {
    HttpMethod m = HttpMethod.valueOf(method);
    return makeRequest(m, url, headers, Optional.empty(), JsonNode.class).getBody().getObject();
  }

  @SuppressWarnings("unused")
  public String makeStringRequest(String method, String url, Map<String, String> headers) {
    HttpMethod m = HttpMethod.valueOf(method);
    return makeRequest(m, url, headers, Optional.empty(), String.class).getBody();
  }

  public JSONObject makeJsonRequest(String method, String url, Map<String, String> headers, Object body) {
    HttpMethod m = HttpMethod.valueOf(method);
    return makeRequest(m, url, headers, Optional.of(body), JsonNode.class).getBody().getObject();
  }

  public String makeStringRequest(String method, String url, Map<String, String> headers, Object body) {
    HttpMethod m = HttpMethod.valueOf(method);
    return makeRequest(m, url, headers, Optional.of(body), String.class).getBody();
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private <T> HttpResponse<T> makeRequest(HttpMethod method, String url, Map<String, String> headers, Optional<?> body, Class<T> responseType)  {
    HttpResponse<T> response;
    if(method == GET) {
      response = Unirest.get(url).headers(headers).asObject(responseType);
    } else if(method == POST) {
      if(body.isPresent()) {
        response = Unirest.post(url).headers(headers).body(body.get()).asObject(responseType);
      } else {
        response = Unirest.post(url).headers(headers).asObject(responseType);
      }
    } else {
      throw new IllegalArgumentException("unsupported method: "+method.name());
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("response: {} [{}]", response.getStatusText(), response.getStatus());
    }
    return response;
  }

  @Override
  public void close() {
    Unirest.shutdown();
  }

  public static KeyValue h(String key, String value) {
    return new KeyValue(key, value);
  }

  public static KeyValue acceptHeader(String type) {
    return new KeyValue("accept", type);
  }

  public static KeyValue contentTypeHeader(String type) {
    return new KeyValue("content-type", type);
  }

  public static Map<String, String> headers(String accessToken, KeyValue ... additionalHeaders) {
    Map<String, String> headers = new HashMap<>();
    headers.put("authorization", format("Bearer %s", accessToken));
    for(KeyValue kv : additionalHeaders) {
      headers.put(kv.getKey(), kv.getValue());
    }
    return headers;
  }
}

