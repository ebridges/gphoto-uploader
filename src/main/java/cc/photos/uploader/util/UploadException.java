package cc.photos.uploader.util;

import static java.lang.String.format;

public class UploadException extends RuntimeException {
  public UploadException(String message, Throwable cause) {
    super(message, cause);
  }

  public UploadException(int status, String statusText, String s) {
    super(format("%s [%d]: %s", statusText, status, s));
  }
}
