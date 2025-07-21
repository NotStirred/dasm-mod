package io.github.notstirred.dasm.mod.forge;

import com.google.gson.Gson;
import cpw.mods.modlauncher.api.*;
import io.github.notstirred.dasm.annotation.AnnotationParser;
import io.github.notstirred.dasm.annotation.AnnotationUtil;
import io.github.notstirred.dasm.annotation.parse.RefImpl;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.exception.NoSuchTypeExists;
import io.github.notstirred.dasm.mod.DasmConfig;
import io.github.notstirred.dasm.mod.util.Either;
import io.github.notstirred.dasm.notify.Notification;
import io.github.notstirred.dasm.transformer.Transformer;
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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DasmForgeTransformationService implements ITransformationService {
    private final ClassNodeProvider classProvider;
    private final Transformer transformer;

    private IModuleLayerManager layerManager;
    private final Logger logger = LogManager.getLogger("dasm");

    public DasmForgeTransformationService() {
        MappingsProvider mappings;
        InputStream resource = DasmForgeTransformationService.class.getClassLoader().getResourceAsStream("mappings");
        if (resource == null || isDev()) {
            logger.warn("Using identity mappings {}, {}", resource, isDev());
            mappings = MappingsProvider.IDENTITY;
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

            logger.warn("Mapping from {} to {}", named, srg);

            mappings = new MappingsProvider() {
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
                    logger.warn("Remapped field: {} to {}", fieldName, s);
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
                    logger.warn("Remapped method: {} to {}", methodName, s);
                    return s;
                }

                @Override
                public String mapClassName(String className) {
                    String s = className;
                    MappingTree.ClassMapping aClass = tree.getClass(className.replace('.', '/'), named);
                    if (aClass != null) {
                        s = aClass.getDstName(srg);
                    }
                    logger.warn("Remapped class: {} to {}", className, s);
                    return s;
                }
            };
        }
        logger.warn("Creating class provider");
        this.classProvider = new CachingClassProvider(s -> {
            String classResource = s.replace(".", "/") + ".class";
            logger.debug("Loading resource {}", classResource);
            try (InputStream classStream = layerManager.getLayer(IModuleLayerManager.Layer.GAME)
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
        });
        logger.warn("Creating transformer");
        this.transformer = new Transformer(this.classProvider, mappings);
        logger.warn("Created transformer");
    }

    @Override
    public @NotNull String name() {
        return "dasm";
    }

    @Override
    public void initialize(IEnvironment iEnvironment) {
        logger.warn("init");
    }

    @Override
    public void onLoad(IEnvironment iEnvironment, Set<String> set) {
        logger.warn("load");
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        logger.warn("complete scan");
        this.layerManager = layerManager;
        return List.of();
    }

    @Override
    public @Nonnull List<ITransformer> transformers() {
        List<ITransformer> transformers = new ArrayList<>();

        logger.warn("Looking for mods");
        LoadingModList.get().getModFiles().forEach(modFile -> {
            Map<Type, Supplier<Either<List<MethodTransform>, ClassTransform>>> modTransforms = new IdentityHashMap<>();

            AnnotationParser annotationParser = new AnnotationParser(this.classProvider);

            String modId = modFile.getMods().get(0).getModId();
            logger.warn("Found mod {}", modId);

            List<String> dasmConfigs = parseDasmConfigs(modFile);
            for (String cfg : dasmConfigs) {
                logger.warn("Loading dasm config {}", cfg);
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
                    logger.warn("Found dasm class {}", dasmClass);
                    modTransforms.put(dasmClassType, () -> {
                        try {
                            logger.warn("Doing dasm things");
                            DasmTarget target = getDasmTarget(dasmClassType);

                            handleNotification(annotationParser.findDasmAnnotations(target.primary));
                            var methodTransformsPrimary = handleNotification(annotationParser.buildContext().buildMethodTargets(target.primary, ""));
                            var classTransform = handleNotification(annotationParser.buildContext().buildClassTarget(target.primary));
                            var methodTransformsSecondary = target.secondary.flatMap(classNode -> {
                                handleNotification(annotationParser.findDasmAnnotations(classNode));
                                return handleNotification(annotationParser.buildContext().buildMethodTargets(classNode, ""));
                            });

                            var methodTransforms = Stream.of(methodTransformsPrimary, methodTransformsSecondary)
                                    .filter(Optional::isPresent).map(Optional::get)
                                    .flatMap(Collection::stream).toList();

                            if (classTransform.isPresent()) {
                                // TODO: nice error
                                assert methodTransformsSecondary.isEmpty() && methodTransformsPrimary.isEmpty() : "Whole class transform WITH method transforms?";
                                return Either.right(classTransform.get());
                            } else {
                                return Either.left(methodTransforms);
                            }

                        } catch (NoSuchTypeExists e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }

            logger.warn("Adding transformers for {} transforms", modTransforms.size());
            modTransforms.forEach((target, modTransform) -> transformers.add(new ITransformer<ClassNode>() {
                @Nonnull @Override
                public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
                    Either<List<MethodTransform>, ClassTransform> transform = modTransform.get();
                    transform.left().ifPresent(methodTransforms -> {
                        handleNotification(transformer.transform(input, methodTransforms));
                    });
                    transform.right().ifPresent(classTransform -> {
                        try {
                            transformer.transform(input, classTransform);
                        } catch (NoSuchTypeExists e) { // TODO: remove when dasm doesn't throw here.
                            handleNotification(List.of(new Notification(e.getMessage(), Notification.Kind.ERROR, e.getClass())));
                        }
                    });
                    doDasmOut(input);
                    return input;
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

    private void doDasmOut(ClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        try {
            Path path = Path.of(".dasm.out/APPLY/" + classNode.name.replace('.', '/') + ".class").toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.write(path, classWriter.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record DasmTarget(ClassNode primary, Optional<ClassNode> secondary) {
    }

    private DasmTarget getDasmTarget(Type dasmType) throws NoSuchTypeExists {
        ClassNode dasmClassNode = this.classProvider.classNode(dasmType);

        AnnotationNode dasmAnnotation = AnnotationUtil.getAnnotationIfPresent(dasmClassNode.invisibleAnnotations, Dasm.class);
        Optional<Type> targetSpecifier = Optional.empty();
        if (dasmAnnotation != null) {
            targetSpecifier = RefImpl.parseOptionalRefAnnotation((AnnotationNode) AnnotationUtil.getAnnotationValues(dasmAnnotation, Dasm.class).get("target"));
        }

        Optional<ClassNode> targetClassNode = Optional.empty();
        if (targetSpecifier.isPresent() && !targetSpecifier.get().equals(dasmType)) {
            targetClassNode = Optional.of(this.classProvider.classNode(targetSpecifier.get()));
        }

        // If there is a @Dasm target it is the primary and the dasm class is the secondary
        // otherwise the dasm class is the primary
        ClassNode primary = targetClassNode.orElse(dasmClassNode);
        Optional<ClassNode> secondary = targetClassNode.map(target -> dasmClassNode);

        return new DasmTarget(primary, secondary);
    }

    private List<String> parseDasmConfigs(IModFileInfo modFile) {
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
            System.err.printf("Failed to load dasm configs from mod file %s", exception);
            return List.of();
        }
    }


    private <T> T handleNotification(Pair<T, List<Notification>> result) {
        handleNotification(result.second());
        return result.first();
    }

    private void handleNotification(NotifyStack notifyStack) {
        handleNotification(notifyStack.notifications());
    }

    private void handleNotification(List<Notification> notifications) {
        for (var notification : notifications) {
            switch (notification.kind) {
                case INFO:
                    logger.info(notification.message);
                    break;
                case WARNING:
                    logger.warn(notification.message);
                    break;
                case ERROR:
                    logger.fatal(notification.message);
                    break;
                default:
                    throw new IllegalStateException("Unknown DASM notification kind: " + notification.kind);
            }
        }
        if (notifications.stream().anyMatch(n -> n.kind == Notification.Kind.ERROR)) {
            // throw Util.pauseInIde(new RuntimeException("DASM Failure, please see log output"));
            throw new RuntimeException("DASM Failure, please see log output");
        }
    }

    private boolean isDev() { // SURELY there is a better way to know this. All other methods I try are illegal bc early class load or modules.
        return System.getProperty("intellij.debug.agent", "false").equals("true")
                || System.getProperty("fabric.development", "false").equals("true")
                || System.getProperty("forge.enableGameTest", "false").equals("true");
    }
}
