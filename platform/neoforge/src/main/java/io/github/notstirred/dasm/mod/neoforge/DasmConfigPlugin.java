package io.github.notstirred.dasm.mod.neoforge;

import com.google.gson.Gson;
import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.mod.BaseConfigPlugin;
import io.github.notstirred.dasm.mod.DasmConfig;
import io.github.notstirred.dasm.util.CachingClassProvider;
import io.github.notstirred.dasm.util.ClassNodeProvider;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.locating.InvalidModFileException;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

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
            IMixinService service = MixinService.getService();
            try (InputStream classStream = service.getResourceAsStream(classResource)) {
                if (classStream == null) {
                    throw new IOException();
                }
                byte[] bytes = new byte[classStream.available()];
                DataInputStream dataInputStream = new DataInputStream(classStream);
                dataInputStream.readFully(bytes);
                return Optional.of(bytes);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Failed to read class resource %s", classResource), e);
            }
        });
    }

    @Override
    protected List<DasmConfig> dasmConfigs() {
        Gson gson = new Gson();
        List<DasmConfig> configs = new ArrayList<>();

        for (ModInfo mod : LoadingModList.get().getMods()) {
            try {
                List<String> configFiles = parseDasmConfig(mod);
                for (String file : configFiles) {
                    try {
                        IMixinService service = MixinService.getService();
                        InputStream resource = service.getResourceAsStream(file);
                        if (resource == null) {
                            throw new IllegalArgumentException(String.format("The specified resource '%s' was invalid or could not be read", file));
                        }
                        DasmConfig config = gson.fromJson(new InputStreamReader(resource), DasmConfig.class);
                        configs.add(config);
                    } catch (IllegalArgumentException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new IllegalArgumentException(String.format("The specified resource '%s' was invalid or could not be read", file), ex);
                    }
                }
            } catch (Exception exception) {
                logger.error("Failed to load dasm configs from mod file", exception);
                return new ArrayList<>();
            }
        }
        return configs;
    }

    private List<String> parseDasmConfig(ModInfo modInfo) {
        IConfigurable config = modInfo.getOwningFile().getConfig();
        List<? extends IConfigurable> dasmEntries = config.getConfigList("dasm");

        if (dasmEntries == null) return new ArrayList<>();

        List<String> dasmConfigFiles = new ArrayList<>();
        for (IConfigurable dasmEntry : dasmEntries) {
            String name = dasmEntry.<String>getConfigElement("config")
                    .orElseThrow(() -> new InvalidModFileException("Missing \"config\" in [[dasm]] entry", modInfo.getOwningFile()));
            dasmConfigFiles.add(name);
        }

        return dasmConfigFiles;
    }
}
