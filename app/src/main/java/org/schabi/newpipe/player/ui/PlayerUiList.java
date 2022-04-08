package org.schabi.newpipe.player.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class PlayerUiList {
    final List<PlayerUi> playerUis = new ArrayList<>();

    public void add(final PlayerUi playerUi) {
        playerUis.add(playerUi);
    }

    public <T> void destroyAll(final Class<T> playerUiType) {
        playerUis.stream()
                .filter(playerUiType::isInstance)
                .forEach(playerUi -> {
                    playerUi.destroyPlayer();
                    playerUi.destroy();
                });
        playerUis.removeIf(playerUiType::isInstance);
    }

    public <T> Optional<T> get(final Class<T> playerUiType) {
        return playerUis.stream()
                .filter(playerUiType::isInstance)
                .map(playerUiType::cast)
                .findFirst();
    }

    public void call(final Consumer<PlayerUi> consumer) {
        //noinspection SimplifyStreamApiCallChains
        playerUis.stream().forEach(consumer);
    }
}
