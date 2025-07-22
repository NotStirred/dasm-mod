package io.github.notstirred.dasm.mod.forge;

import com.google.gson.Gson;
import cpw.mods.modlauncher.api.*;
import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.exception.NoSuchTypeExists;
import io.github.notstirred.dasm.mod.DasmConfig;
import io.github.notstirred.dasm.mod.DasmService;
import io.github.notstirred.dasm.mod.util.Either;
import io.github.notstirred.dasm.transformer.data.ClassTransform;
import io.github.notstirred.dasm.transformer.data.MethodTransform;
import io.github.notstirred.dasm.util.*;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.InvalidModFileException;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

public class DasmForgeTransformationService implements ITransformationService {
    private final DasmService dasmService;
    private IModuleLayerManager layerManager;

    private static final Logger logger = LogManager.getLogger("dasm");

    public DasmForgeTransformationService() {
        this.dasmService = new DasmService(createMappingsProvider(), new CachingClassProvider(s -> {
            String classResource = s.replace(".", "/") + ".class";
            logger.debug("Loading resource {}", classResource);
            try (InputStream classStream = this.layerManager.getLayer(IModuleLayerManager.Layer.GAME)
                    .orElseThrow(() -> new RuntimeException("DASM: failed to get GAME layer when loading class."))
                    .modules().stream().map(module -> {
                        try {
                            return module.getResourceAsStream(classResource);
                        } catch (IOException e) {
                            return null;
                        }
                    }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new RuntimeException("DASM: failed to load resource: " + classResource))) {
                return Optional.of(classStream.readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public @NotNull String name() {
        return "dasm";
    }

    @Override
    public void initialize(IEnvironment iEnvironment) {
    }

    @Override
    public void onLoad(IEnvironment iEnvironment, Set<String> set) {
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        this.layerManager = layerManager;
        return List.of();
    }

    @Override
    public @Nonnull List<ITransformer> transformers() {
        List<ITransformer> transformers = new ArrayList<>();

        logger.debug("Looking for mods");
        LoadingModList.get().getModFiles().forEach(modFile -> {
            Map<Type, Supplier<Either<List<MethodTransform>, ClassTransform>>> modTransforms = new IdentityHashMap<>();

            String modId = modFile.getMods().get(0).getModId();
            logger.debug("Found mod {}", modId);

            List<String> dasmConfigs = parseDasmConfigs(modId, modFile);
            for (String cfg : dasmConfigs) {
                logger.debug("Loading dasm config {}", cfg);
                InputStream resource;
                try {
                    resource = Files.newInputStream(modFile.getFile().findResource(cfg));
                } catch (IOException e) {
                    logger.fatal("failed to get dasm config resource from mod {}, {}", modId, cfg);
                    throw new RuntimeException(e);
                }
                DasmConfig dasmConfig = new Gson().fromJson(new InputStreamReader(resource), DasmConfig.class);

                for (String dasmClass : dasmConfig.dasmClasses()) {
                    Type dasmClassType = Type.getObjectType(TypeUtil.classNameToInternalName(dasmClass));
                    logger.trace("Found dasm class {}", dasmClass);
                    modTransforms.put(dasmClassType, () -> {
                        try {
                            return this.dasmService.scanForTransforms(dasmClassType);
                        } catch (NoSuchTypeExists e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }

            logger.info("Adding transformers for {} transforms", modTransforms.size());
            modTransforms.forEach((target, modTransform) -> transformers.add(new ITransformer<ClassNode>() {
                @Nonnull @Override
                public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
                    return dasmService.doTransform(input, modTransform.get());
                }

                @Nonnull @Override
                public TransformerVoteResult castVote(ITransformerVotingContext context) {
                    return TransformerVoteResult.YES;
                }

                @Nonnull @Override
                public Set<Target> targets() {
                    return Set.of(Target.targetClass(target.getClassName()));
                }
            }));
        });

        return transformers;
    }

    private MappingsProvider createMappingsProvider() {
        InputStream resource = DasmForgeTransformationService.class.getClassLoader().getResourceAsStream("mappings");

        if (resource == null || isDev()) {
            logger.info("Using identity mappings {}, {}", resource, isDev());
            return MappingsProvider.IDENTITY;
        } else {
            MemoryMappingTree tree = new MemoryMappingTree();
            tree.setIndexByDstNames(true);

            try {
                MappingReader.read(new BufferedReader(new InputStreamReader(resource)), MappingFormat.TINY_2_FILE, tree);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            int named = tree.getDstNamespaces().indexOf("named");
            int srg = tree.getDstNamespaces().indexOf("srg");

            logger.info("Mapping from named {} to srg {}", named, srg);

            return new MappingsProvider() {
                @Override
                public String mapFieldName(String owner, String fieldName, String descriptor) {
                    String s = fieldName;
                    MappingTree.ClassMapping aClass = tree.getClass(owner.replace('.', '/'), named);
                    if (aClass != null) {
                        MappingTree.FieldMapping field = aClass.getField(fieldName, descriptor, named);
                        if (field != null) {
                            s = field.getDstName(srg);
                        }
                    }
                    logger.trace("Remapped field: {} to {}", fieldName, s);
                    return s;
                }

                @Override
                public String mapMethodName(String owner, String methodName, String descriptor) {
                    String s = methodName;
                    MappingTree.ClassMapping aClass = tree.getClass(owner.replace('.', '/'), named);
                    if (aClass != null) {
                        MappingTree.MethodMapping method = aClass.getMethod(methodName, descriptor, named);
                        if (method != null) {
                            s = method.getDstName(srg);
                        }
                    }
                    logger.trace("Remapped method: {} to {}", methodName, s);
                    return s;
                }

                @Override
                public String mapClassName(String className) {
                    String s = className;
                    MappingTree.ClassMapping aClass = tree.getClass(className.replace('.', '/'), named);
                    if (aClass != null) {
                        s = aClass.getDstName(srg);
                    }
                    logger.trace("Remapped class: {} to {}", className, s);
                    return s;
                }
            };
        }
    }

    private List<String> parseDasmConfigs(String modId, IModFileInfo modFile) {
        try {
            IConfigurable config = modFile.getConfig();
            List<? extends IConfigurable> dasmEntries = config.getConfigList("dasm");

            List<String> potentialDasm = new ArrayList<>();
            for (IConfigurable dasmEntry : dasmEntries) {
                String name = dasmEntry.<String>getConfigElement("config")
                        .orElseThrow(() -> new InvalidModFileException("Missing \"config\" in [[dasm]] entry", modFile));
                potentialDasm.add(name);
            }

            return potentialDasm;


        } catch (Exception exception) {
            logger.error("Failed to load dasm configs from mod {} file {}", modId, exception.toString());
            return List.of();
        }
    }

    private boolean isDev() { // SURELY there is a better way to know this. All other methods I try are illegal bc they early class load or modules jank.
        return System.getProperty("intellij.debug.agent", "false").equals("true")
                || System.getProperty("fabric.development", "false").equals("true")
                || System.getProperty("forge.enableGameTest", "false").equals("true");
    }
}
