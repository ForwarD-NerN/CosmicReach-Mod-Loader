package ru.nern.modloader;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProviderHelper;
import net.fabricmc.loader.impl.game.LibClassifier;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.ContactInformationImpl;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import ru.nern.modloader.patch.CRInitPatch;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/*
 * A custom GameProvider which grants Fabric Loader the necessary information to launch the game.
 */
public class CosmicReachGameProvider implements GameProvider {
    public static final String PROVIDER_VERSION = "1.1.2";
    public static final String CLIENT_ENTRYPOINT = "finalforeach.cosmicreach.BlockGame";
    public static final String[] ENTRYPOINTS = new String[]{CLIENT_ENTRYPOINT};
    public static final String PROPERTY_GAME_DIRECTORY = "appDirectory";

    private Arguments arguments;
    private EnvType envType;
    private Path gameJar;
    private String version;
    private String entrypoint;
    private Collection<Path> validParentClassPath; // computed parent class path restriction (loader+deps)

    private static final GameTransformer TRANSFORMERS = new GameTransformer(new CRInitPatch());

    @Override
    public String getGameId() {
        return "cosmic_reach";
    }
    @Override
    public String getGameName() {
        return "Cosmic Reach";
    }
    @Override
    public String getRawGameVersion() {
        return version;
    }
    @Override
    public String getNormalizedGameVersion() {
        return version;
    }


    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        HashMap<String, String> contactMap = new HashMap<>();
        contactMap.put("homepage", "https://finalforeach.itch.io/cosmic-reach");
        contactMap.put("wiki", "https://cosmicreach.wiki.gg/wiki");

        BuiltinModMetadata.Builder modMetadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
                .setName(getGameName())
                .addAuthor("FinalForEach", contactMap)
                .setContact(new ContactInformationImpl(contactMap))
                .setDescription("Cosmic Reach Game");

        HashMap<String, String> contactMapProvider = new HashMap<>();
        contactMapProvider.put("homepage", "https://github.com/ForwarD-NerN/CosmicReach-Mod-Loader");

        BuiltinModMetadata.Builder providerMetadata = new BuiltinModMetadata.Builder("cosmic_reach_provider", PROVIDER_VERSION)
                .setName("Cosmic Reach Game Provider")
                .addAuthor("ForwarD NerN", contactMapProvider)
                .addContributor("KaboomRoads", contactMapProvider)
                .setContact(new ContactInformationImpl(contactMapProvider))
                .setDescription("The game provider for the Cosmic Reach");

        return Arrays.asList(new BuiltinMod(Collections.singletonList(gameJar), modMetadata.build()),
                new BuiltinMod(Collections.emptyList(), providerMetadata.build()));
    }
    @Override
    public String getEntrypoint() {
        return entrypoint;
    }

    @Override
    public Path getLaunchDirectory() {
        if (arguments == null) {
            return Paths.get(".");
        }
        return getLaunchDirectory(arguments);
    }

    private static Path getLaunchDirectory(Arguments arguments) {
        return Paths.get(arguments.getOrDefault(PROPERTY_GAME_DIRECTORY, "."));
    }
    @Override
    public boolean isObfuscated() {
        return false;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        this.envType = launcher.getEnvironmentType();
        this.arguments = new Arguments();
        this.arguments.parse(args);

        Path commonGameJar = GameProviderHelper.getCommonGameJar();
        Path envGameJar = GameProviderHelper.getEnvGameJar(envType);

        try {
            LibClassifier<CosmicLibrary> classifier = new LibClassifier<>(CosmicLibrary.class, envType, this);
            //CosmicLibrary envGameLib = envType == EnvType.CLIENT ? CosmicLibrary.COSMIC_CLIENT : CosmicLibrary.COSMIC_SERVER;
            CosmicLibrary envGameLib = CosmicLibrary.COSMIC_CLIENT;

            if(commonGameJar != null) {
                classifier.process(commonGameJar);
            }else if(envGameJar != null) {
                classifier.process(envGameJar);
            }else {
                List<String> gameLocations = new ArrayList<>();

                // Respect "fabric.gameJarPath" if it is set.
                if (System.getProperty(SystemProperties.GAME_JAR_PATH) != null) {
                    gameLocations.add(System.getProperty(SystemProperties.GAME_JAR_PATH));
                }

                // List out default locations.
                gameLocations.add("./cosmic-reach.jar");
                gameLocations.add("./game/cosmic-reach.jar");

                Optional<Path> gameLocation = gameLocations.stream().map(p ->
                        Paths.get(p).toAbsolutePath().normalize())
                        .filter(Files::exists).findFirst();
                if(gameLocation.isPresent()) {
                    classifier.process(gameLocation.get());
                }
            }
            classifier.process(launcher.getClassPath());

            gameJar = classifier.getOrigin(envGameLib);
            entrypoint = classifier.getClassName(envGameLib);
            validParentClassPath = classifier.getSystemLibraries();
        } catch (IOException e) {
            throw ExceptionUtil.wrap(e);
        }

        if(gameJar == null) {
            Log.error(LogCategory.GAME_PROVIDER, "Could not locate Cosmic Reach JAR!");
            return false;
        }

        version = CRVersionLookup.getVersion(gameJar);

        return true;
    }

    @Override
    public void initialize(FabricLauncher launcher) {
//        try {
//            launcher.setValidParentClassPath(ImmutableList.of(
//                    Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(MixinBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(FabricLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(AnnotationVisitor.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(AbstractInsnNode.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(Analyzer.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(ASMifier.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(AdviceAdapter.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(MixinExtrasBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(AccessWidener.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(CalledMethods.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(CanIgnoreReturnValue.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(InternalFutureFailureAccess.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(Gson.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(BiMap.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(Nullable.class.getProtectionDomain().getCodeSource().getLocation().toURI()),
//                    Path.of(MappingGetter.class.getProtectionDomain().getCodeSource().getLocation().toURI())
//            ));
//        } catch (URISyntaxException e) {
//            throw new RuntimeException(e);
//        }
        launcher.setValidParentClassPath(validParentClassPath);
        Log.info(LogCategory.GAME_PROVIDER, "Valid classpath: "+validParentClassPath);

        TRANSFORMERS.locateEntrypoints(launcher, Collections.singletonList(gameJar));
    }
    @Override
    public GameTransformer getEntrypointTransformer() {
        return TRANSFORMERS;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        launcher.addToClassPath(gameJar);
    }

    @Override
    public void launch(ClassLoader loader) {
        String targetClass = "finalforeach.cosmicreach.lwjgl3.Lwjgl3Launcher";

        try {
            Class<?> main = loader.loadClass(targetClass);
            Method method = main.getMethod("main", String[].class);
            method.invoke(null, (Object) this.arguments.toArray());
        } catch (InvocationTargetException e) {
            throw new FormattedException("The game has crashed!", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new FormattedException("Failed to launch the game", e);
        }
    }

    @Override
    public Arguments getArguments() {
        return this.arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        Log.info(LogCategory.GAME_PROVIDER, "LAUNCH ARGS: " + Arrays.toString(arguments.toArray()));
        return arguments == null ? new String[0] : arguments.toArray();
    }
}
