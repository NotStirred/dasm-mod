package io.github.notstirred.dasm.mod.neoforge;

import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.mod.BaseConfigPlugin;
import io.github.notstirred.dasm.mod.DasmBootstrap;
import io.github.notstirred.dasm.util.CachingClassProvider;
import io.github.notstirred.dasm.util.ClassNodeProvider;
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

public class DasmConfigPlugin extends BaseConfigPlugin {
    @Override
    protected MappingsProvider mappingsProvider() {
        return MappingsProvider.IDENTITY;
    }

    @Override
    protected ClassNodeProvider classNodeProvider() {
        return new CachingClassProvider(s -> {
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
        });
    }
}
