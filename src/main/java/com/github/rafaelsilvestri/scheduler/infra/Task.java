package com.github.rafaelsilvestri.scheduler.infra;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Random;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@Profile("scheduler")
@Component
public class Task {

  private static final String LOCK_KEY = "lock";
  private static final String LOCK_VALUE = "locked-by-leader";
  private static final long LOCK_TTL_IN_MILLIS = 10 * 1000L;
  // finish the job 500ms before the lock TTL expires
  private static final long SAFE_RUN_LIMIT_IN_MILLIS = LOCK_TTL_IN_MILLIS - 500;

  // Distributed lock manager
  private final StringRedisTemplate dlm;
  // used for debugging
  private final String instanceName;

  public Task(StringRedisTemplate redisTemplate) {
    this.dlm = redisTemplate;
    this.instanceName = System.getenv("INSTANCE_NAME");
  }
  /**
   * Run a fixed delay task every 1 second that triggers a loop to performs the task until the lock
   * TTL is reached. The method uses DLM (Distributed Lock Management) to ensure that at-most-one
   * instance/thread will perform the task.
   */
  @Scheduled(fixedDelay = 1000)
  public void scheduleFixedDelayTask() {

    if (!isLeading()) {
      System.out.println("Skipping instance: " + instanceName);
    }

    final var limitTimeNano =
        System.nanoTime() + NANOSECONDS.convert(SAFE_RUN_LIMIT_IN_MILLIS, MILLISECONDS);

    System.out.println("limitTimeNano " + SECONDS.convert(limitTimeNano, NANOSECONDS));

    var sleepTimeInMillis = 500L;
    var counter = 0;
    try {
      do {
        // debug: print once to keep logs clear
        System.out.println(
            String.format(
                "Fixed delay task - %s - %s - %s", instanceName, limitTimeNano, System.nanoTime()));
        // random duration between 0 and 500 milliseconds to simulate work being done.
        var duration = new Random().nextInt(500);
        Thread.sleep(duration);
        counter++;

        // sleep remaining time will always give the same rate (at least 500ms before execute again)
        Thread.sleep(sleepTimeInMillis - duration);
      } while (System.nanoTime() < limitTimeNano && !Thread.currentThread().isInterrupted());
    } catch (InterruptedException e) {
      System.out.println("Error: " + e.getMessage());
    } finally {
      // release the lock immediately
      System.out.println("Run count: " + counter);
      dlm.delete(LOCK_KEY);
    }
  }

  /**
   * Creates a lock entry that defines the instance is leading the task. First come wins.
   *
   * @return true if the current instance is leading.
   */
  private boolean isLeading() {
    return Optional.ofNullable(
            dlm.opsForValue()
                .setIfAbsent(LOCK_KEY, LOCK_VALUE, Duration.ofMillis(LOCK_TTL_IN_MILLIS)))
        .orElse(false);
  }
}
