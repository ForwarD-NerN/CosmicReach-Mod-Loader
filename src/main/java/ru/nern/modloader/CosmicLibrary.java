package ru.nern.modloader;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.LibClassifier;

public enum CosmicLibrary implements LibClassifier.LibraryType {
    COSMIC_CLIENT(EnvType.CLIENT, CosmicReachGameProvider.CLIENT_ENTRYPOINT.replace(".", "/") + ".class");
    //This is where I'd put my server entrypoint if I had one
    //COSMIC_SERVER(EnvType.SERVER, CosmicReachGameProvider.SERVER_ENTRYPOINT.replace(".", "/") + ".class");

    private final EnvType env;
    private final String[] paths;

    CosmicLibrary(String path) {
        this(null, new String[] { path });
    }

    CosmicLibrary(String... paths) {
        this(null, paths);
    }

    CosmicLibrary(EnvType env, String... paths) {
        this.paths = paths;
        this.env = env;
    }

    @Override
    public boolean isApplicable(EnvType env) {
        return this.env == null || this.env == env;
    }

    @Override
    public String[] getPaths() {
        return paths;
    }
}
