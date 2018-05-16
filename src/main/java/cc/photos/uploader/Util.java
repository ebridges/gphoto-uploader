package cc.photos.uploader;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClients;

public class Util {
  // https://dm.zimmer428.net/2015/03/http-caching-and-unirest-java/
  public static HttpClient configureCachingClient() {
    CacheConfig cacheConfig = CacheConfig.custom()
        .setMaxCacheEntries(1000)
        .setMaxObjectSize(8192)
        .setSharedCache(false)
        .build();
    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(30000)
        .setSocketTimeout(30000)
        .build();
    CloseableHttpClient cachingClient = CachingHttpClients.custom()
        .setCacheConfig(cacheConfig)
        .setDefaultRequestConfig(requestConfig)
        .build();
    return cachingClient;
  }
}
