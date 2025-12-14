package io.github.notstirred.dasm.mod.fabric;

import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.mod.DasmBootstrap;
import io.github.notstirred.dasm.mod.DasmExtension;
import io.github.notstirred.dasm.util.CachingClassProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DasmConfigPlugin implements IMixinConfigPlugin {
    private static final Logger logger = LogManager.getLogger("dasm");

    private DasmExtension dasmExtension;

    @Override
    public void onLoad(String mixinPackage) {
        // FIXME: identity mappings not valid on fabric
        dasmExtension = DasmBootstrap.init(MappingsProvider.IDENTITY, new CachingClassProvider(s -> {
            String classResource = s.replace(".", "/") + ".class";
            logger.debug("Loading resource {}", classResource);
            try (InputStream classStream = DasmConfigPlugin.class.getClassLoader().getResourceAsStream(classResource)) {
                byte[] bytes = new byte[classStream.available()];
                DataInputStream dataInputStream = new DataInputStream(classStream);
                dataInputStream.readFully(bytes);
                return Optional.of(bytes);
            } catch (IOException | NullPointerException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
