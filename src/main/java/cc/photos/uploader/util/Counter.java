package cc.photos.uploader.util;

public class Counter {
  private int count;

  public Counter() {
    this.count = 0;
  }

  public void incr() {
    this.count++;
  }

  public String toString() {
    return String.valueOf(count);
  }
}
