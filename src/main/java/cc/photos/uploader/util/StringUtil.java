package cc.photos.uploader.util;

public class StringUtil {
  public static boolean isEmpty(String val) {
    return val == null || val.isEmpty();
  }

  public static boolean isNotEmpty(String val) {
    return val != null && val.length() > 0;
  }
}
