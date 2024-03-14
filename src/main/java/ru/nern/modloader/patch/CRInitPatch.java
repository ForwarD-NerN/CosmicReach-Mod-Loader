package ru.nern.modloader.patch;

import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import ru.nern.modloader.CosmicHooks;

import java.util.function.Consumer;
import java.util.function.Function;

public class CRInitPatch extends GamePatch {
    @Override
    public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
        String entrypoint = launcher.getEntrypoint();

        if (!entrypoint.startsWith("finalforeach.cosmicreach.")) {
            return;
        }
        ClassNode mainClass = classSource.apply(entrypoint);
        if (mainClass == null) {
            throw new RuntimeException("Could not load main class " + entrypoint + "!");
        }

        MethodNode initMethod = findMethod(mainClass, (method) -> method.name.equals("main"));
        if (initMethod == null) {
            throw new RuntimeException("Could not find init method in " + entrypoint + "!");
        }

        Log.debug(LogCategory.GAME_PATCH, "Found init method: %s -> %s", entrypoint, mainClass.name);
        Log.debug(LogCategory.GAME_PATCH, "Patching init method %s%s", initMethod.name, initMethod.desc);

        initMethod.instructions.iterator().add(new MethodInsnNode(Opcodes.INVOKESTATIC, CosmicHooks.INTERNAL_NAME, "init", "()V", false));
        classEmitter.accept(mainClass);
    }
}
