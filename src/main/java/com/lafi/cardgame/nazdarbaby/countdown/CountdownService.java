package com.lafi.cardgame.nazdarbaby.countdown;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CountdownService implements Runnable {

	private final Map<BroadcastListener, CountdownTask> listenerToTask = new ConcurrentHashMap<>();

	private ScheduledExecutorService executorService;

	@Override
	public void run() {
		for (CountdownTask countdownTask : listenerToTask.values()) {
			if (countdownTask.isCanceled()) {
				removeCountdownTask(countdownTask);
			} else {
				countdownTask.eachRun();

				if (countdownTask.decreaseAndGet() < 0) {
					countdownTask.finalRun();
					removeCountdownTask(countdownTask);
				} else if (!countdownTask.isListening()) {
					removeCountdownTask(countdownTask);
				}
			}
		}
	}

	public void addCountdownTask(CountdownTask countdownTask) {
		listenerToTask.put(countdownTask.getListener(), countdownTask);

		if (executorService == null) {
			startCountdown();
		}
	}

	private void removeCountdownTask(CountdownTask countdownTask) {
		BroadcastListener broadcastListener = countdownTask.getListener();
		listenerToTask.remove(broadcastListener);

		if (listenerToTask.isEmpty()) {
			executorService.shutdown();
			executorService = null;
		}
	}

	private synchronized void startCountdown() {
		if (executorService == null) {
			executorService = Executors.newSingleThreadScheduledExecutor();
			executorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
		}
	}
}