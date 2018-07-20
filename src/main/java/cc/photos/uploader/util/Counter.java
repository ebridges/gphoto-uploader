package cc.photos.uploader.util;

import java.util.concurrent.atomic.AtomicInteger;

public class Counter {
  private AtomicInteger count;

  public Counter() {
    this.count = new AtomicInteger(0);
  }

  public int incr() {
    return this.count.incrementAndGet();
  }

  public String toString() {
    return String.valueOf(count.get());
  }
}
