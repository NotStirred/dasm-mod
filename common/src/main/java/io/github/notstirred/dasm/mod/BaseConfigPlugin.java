package io.github.notstirred.dasm.mod;

import io.github.notstirred.dasm.api.provider.MappingsProvider;
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

public abstract class BaseConfigPlugin implements IMixinConfigPlugin {
    protected static final Logger logger = LogManager.getLogger("dasm");

    protected DasmExtension extension;

    @Override
    public void onLoad(String mixinPackage) {
        this.extension = DasmBootstrap.init(mappingsProvider(), classNodeProvider());
    }

    protected abstract MappingsProvider mappingsProvider();

    protected abstract ClassNodeProvider classNodeProvider();

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.extension.shouldApplyMixin(targetClassName, mixinClassName);
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
