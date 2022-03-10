package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.points.Points;
import com.lafi.cardgame.nazdarbaby.user.User;
import com.lafi.cardgame.nazdarbaby.util.ExecutorServiceUtil;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.lafi.cardgame.nazdarbaby.view.BoardView;
import com.lafi.cardgame.nazdarbaby.view.TableView;
import com.lafi.cardgame.nazdarbaby.view.TablesView;
import com.vaadin.flow.component.checkbox.Checkbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Table {

	public static final int MINIMUM_USERS = Collections.min(Points.NUMBER_OF_USERS_TO_WIN_MAP.keySet());
	public static final int MAXIMUM_USERS = Collections.max(Points.NUMBER_OF_USERS_TO_WIN_MAP.keySet());
	public static final int NOTIFICATION_DELAY_IN_MINUTES = 5;

	private final String tableName;
	private final UserProvider userProvider;
	private final Game game;

	private final List<Checkbox> countdownCheckboxes = new ArrayList<>();
	private ExecutorService newGameExecutorService;

	private Instant lastNotificationTime;
	private int nextButtonClickCounter;

	Table(String tableName) {
		this.tableName = tableName;
		userProvider = UserProvider.get(tableName);
		game = Game.get(userProvider);

		resetLastNotificationTime();
	}

	public String getTableName() {
		return tableName;
	}

	public UserProvider getUserProvider() {
		return userProvider;
	}

	public Game getGame() {
		return game;
	}

	public Instant getLastNotificationTime() {
		return lastNotificationTime;
	}

	public void setLastNotificationTime(Instant lastNotificationTime) {
		this.lastNotificationTime = lastNotificationTime;
	}

	public void addCountdownCheckbox(Checkbox countdownCheckbox) {
		if (!isNewGameCountdownRunning()) {
			return;
		}

		List<User> users = game.isGameInProgress() ? game.getMatchUsers() : userProvider.getPlayingUsers();
		if (countdownCheckboxes.size() == users.size()) {
			countdownCheckboxes.remove(0);
		}

		countdownCheckboxes.add(countdownCheckbox);
	}

	public void startNewGameCountdown() {
		if (isNewGameCountdownRunning()) {
			return;
		}

		countdownCheckboxes.clear();

		long remainingDurationInSeconds = ExecutorServiceUtil.getRemainingDurationInSeconds(1);
		newGameExecutorService = ExecutorServiceUtil.runPerSecond(new ExecutorServiceUtil.CountdownRunnable(remainingDurationInSeconds) {

			@Override
			public void everyRun() {
				for (Checkbox countdownCheckbox : countdownCheckboxes) {
					String label = countdownCheckbox.getLabel();
					String[] splittedLabel = label.split(FORMATTED_COUNTDOWN_REGEX_SPLITTER);
					String originalLabel = splittedLabel[0];

					String newLabel = originalLabel + getFormattedCountdown();
					UiUtil.access(countdownCheckbox, () -> countdownCheckbox.setLabel(newLabel));
				}
			}

			@Override
			public void finalRun() {
				if (game.isGameInProgress()) {
					stopCurrentGame();
					Broadcaster.INSTANCE.broadcast(BoardView.class, tableName, null);
				} else {
					startNewGame();
				}

				// call it even if game is in progress - some players can be in "waiting room"
				Broadcaster.INSTANCE.broadcast(TableView.class, tableName, null);
			}

			private void stopCurrentGame() {
				game.getMatchUsers().stream()
						.filter(user -> !user.isLoggedOut())
						.forEach(user -> user.setNewGame(true));
				game.setGameInProgress(false);
			}

			private void startNewGame() {
				userProvider.getPlayingUsers().stream()
						.filter(user -> !user.isReady())
						.forEach(user -> user.setLoggedOut(true));
				tryStartNewGame();
			}
		});
	}

	public void stopNewGameCountdown() {
		if (newGameExecutorService != null) {
			newGameExecutorService.shutdown();
		}

		if (game.isGameInProgress()) {
			game.getMatchUsers().forEach(User::resetAction);
			Broadcaster.INSTANCE.broadcast(BoardView.class, tableName, null);
		}
	}

	public boolean isNewGameCountdownRunning() {
		return newGameExecutorService != null && !newGameExecutorService.isShutdown();
	}

	public boolean increaseAndCheckNextButtonClickCounter() {
		return ++nextButtonClickCounter % game.getMatchUsers().size() == 0;
	}

	public String getInfo() {
		if (game.isGameInProgress()) {
			return "In progress, Playing = " + game.getMatchUsers().size();
		}

		int readyCounter = 0;
		int notReadyCounter = 0;
		for (User playingUser : userProvider.getPlayingUsers()) {
			if (playingUser.isReady()) {
				++readyCounter;
			} else {
				++notReadyCounter;
			}
		}

		return "Not started, Ready = " + readyCounter + ", Not ready = " + notReadyCounter;
	}

	public boolean isFull() {
		return game.isGameInProgress() && game.getMatchUsers().size() == MAXIMUM_USERS;
	}

	public void tryStartNewGame() {
		if (userProvider.arePlayingUsersReady()) {
			List<User> playingUsers = userProvider.getPlayingUsers();
			int numberOfPlayingUsers = playingUsers.size();

			if (numberOfPlayingUsers >= MINIMUM_USERS && numberOfPlayingUsers <= MAXIMUM_USERS) {
				stopNewGameCountdown();
				resetLastNotificationTime();

				nextButtonClickCounter = 0;
				game.setGameInProgress(true);
			}
		}
	}

	public void delete() {
		game.delete();
		userProvider.delete(tableName);

		TablesView.deleteTable(tableName);
		TableProvider.INSTANCE.delete(tableName);
	}

	private void resetLastNotificationTime() {
		long now = System.currentTimeMillis();
		long delay = TimeUnit.MINUTES.toMillis(NOTIFICATION_DELAY_IN_MINUTES);

		lastNotificationTime = Instant.ofEpochMilli(now - delay);
	}
}