package ru.nern.modloader.patch;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
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

        MethodNode initMethod = findMethod(mainClass, (method) -> method.name.equals("create"));
        if (initMethod == null) {
            throw new RuntimeException("Could not find method \"create\" in " + entrypoint + "!");
        }

        Log.debug(LogCategory.GAME_PATCH, "Found \"create\" method: %s -> %s", entrypoint, mainClass.name);
        Log.debug(LogCategory.GAME_PATCH, "Patching \"create\" method %s%s", initMethod.name, initMethod.desc);

        injectTailInsn(initMethod, new MethodInsnNode(Opcodes.INVOKESTATIC, CosmicHooks.INTERNAL_NAME, "init", "()V", false));
        classEmitter.accept(mainClass);
    }

    private static void injectTailInsn(MethodNode method, AbstractInsnNode injectedInsn) {
        AbstractInsnNode ret = null;
        int returnOpcode = Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN);

        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof InsnNode && insn.getOpcode() == returnOpcode) {
                ret = insn;
            }
        }

        if (ret == null) {
            throw new RuntimeException("TAIL could not locate a valid RETURN in the target method!");
        }

        method.instructions.insertBefore(ret, injectedInsn);
    }
}
