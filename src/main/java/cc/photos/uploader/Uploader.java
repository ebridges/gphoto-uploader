package cc.photos.uploader;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class Uploader {
  private static final Logger LOG = LoggerFactory.getLogger(Uploader.class);
  private Map<String,String> ALBUM_CACHE = new HashMap<>();

  private GPhotoUploadService uploadService = new GPhotoUploadService();

  public void authorize(String secretsFile) throws IOException {
    if(LOG.isDebugEnabled()) {
      LOG.debug("authorizing client with credentials: {}", secretsFile);
    }
    this.uploadService.authorize(secretsFile);
  }

  /* assume album name looks like this: `2017/2017-01-01`; we only want the final portion */
  private String resolveAlbumId(Path albumPath) {
    LOG.debug("Getting album ID for [{}]", albumPath);
    String albumName =  albumPath.getFileName().toString();
    String albumId;
    try {
      synchronized (this) {
        albumId = lookupAlbumId(albumName);
        if (albumId == null) {
          LOG.debug("no album found with name: {}, creating.", albumName);
          albumId = uploadService.createAlbum(albumName);
          ALBUM_CACHE.put(albumName, albumId);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("unable to create album: "+albumPath, e);
    }
    assert albumId != null;
    if(LOG.isDebugEnabled()) {
      LOG.debug("Found album ID {} for [{}]", albumId, albumPath);
    }
    return albumId;
  }

  public void upload(Path mediaPath) {
    Stopwatch timer = Stopwatch.createStarted();
    String albumId = resolveAlbumId(mediaPath.getParent());
    String uploadId;
    try {
      uploadId = uploadService.uploadBytes(mediaPath);
      LOG.debug("uploadId: {}", uploadId);
    } catch (IOException e) {
      throw new UploadException("Error uploading media file ["+mediaPath+"].", e);
    }
    try {
      String response = uploadService.addMediaItem(albumId, uploadId, mediaPath);
      LOG.debug("Upload completed: {}", response);
    } catch (IOException e) {
      throw new UploadException("Error adding media file to album ["+mediaPath+"].", e);
    }
    timer.stop();
    LOG.info("media file [{}] uploaded to album [{}] in [{}]", mediaPath, mediaPath.getParent(), timer);
  }

  private String lookupAlbumId(String albumName) throws IOException {
    LOG.info("loooking up ID for album {}", albumName);
    Stopwatch timer = Stopwatch.createStarted();
    if(ALBUM_CACHE.isEmpty()) {
      ALBUM_CACHE = uploadService.listAlbums();
    }
    timer.stop();
    if(LOG.isDebugEnabled()) {
      LOG.debug("listAlbums took {}", timer);
    }
    return ALBUM_CACHE.get(albumName);
  }
}
