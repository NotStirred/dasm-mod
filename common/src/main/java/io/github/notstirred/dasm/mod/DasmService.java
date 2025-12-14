package io.github.notstirred.dasm.mod;

import io.github.notstirred.dasm.annotation.AnnotationParser;
import io.github.notstirred.dasm.annotation.AnnotationUtil;
import io.github.notstirred.dasm.annotation.parse.RefImpl;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.provider.MappingsProvider;
import io.github.notstirred.dasm.exception.NoSuchTypeExists;
import io.github.notstirred.dasm.mod.util.Either;
import io.github.notstirred.dasm.notify.Notification;
import io.github.notstirred.dasm.transformer.Transformer;
import io.github.notstirred.dasm.transformer.data.ClassTransform;
import io.github.notstirred.dasm.transformer.data.MethodTransform;
import io.github.notstirred.dasm.util.ClassNodeProvider;
import io.github.notstirred.dasm.util.NotifyStack;
import io.github.notstirred.dasm.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DasmService {
    private static final Logger logger = LogManager.getLogger("dasm");

    private final ClassNodeProvider classProvider;
    private final Transformer transformer;
    private final AnnotationParser annotationParser;

    public DasmService(MappingsProvider mappingsProvider, ClassNodeProvider classProvider) {
        this.classProvider = classProvider;
        this.transformer = new Transformer(classProvider, mappingsProvider);
        this.annotationParser = new AnnotationParser(this.classProvider);
    }

    class DasmTransform {
        final DasmTarget target;
        final Either<List<MethodTransform>, ClassTransform> transform;

        DasmTransform(DasmTarget target, Either<List<MethodTransform>, ClassTransform> transform) {
            this.target = target;
            this.transform = transform;
        }
    }

    DasmTransform scanForTransforms(Type dasmClassType) throws NoSuchTypeExists {
        DasmTarget target = getDasmTarget(dasmClassType);

        handleNotification(annotationParser.findDasmAnnotations(target.primary));
        Optional<Collection<MethodTransform>> methodTransformsPrimary = handleNotification(annotationParser.buildContext().buildMethodTargets(target.primary, ""));
        Optional<ClassTransform> classTransform = handleNotification(annotationParser.buildContext().buildClassTarget(target.primary));
        Optional<Collection<MethodTransform>> methodTransformsSecondary = target.secondary.flatMap(classNode -> {
            handleNotification(annotationParser.findDasmAnnotations(classNode));
            return handleNotification(annotationParser.buildContext().buildMethodTargets(classNode, ""));
        });

        List<MethodTransform> methodTransforms = Stream.of(methodTransformsPrimary, methodTransformsSecondary)
                .filter(Optional::isPresent).map(Optional::get)
                .flatMap(Collection::stream).collect(Collectors.toList());

        if (classTransform.isPresent()) {
            // TODO: nice error
            assert (!methodTransformsSecondary.isPresent() || methodTransformsSecondary.get().isEmpty()) && (!methodTransformsPrimary.isPresent() || methodTransformsPrimary.get().isEmpty()) : "Whole class transform WITH method transforms?";
            return new DasmTransform(target, Either.right(classTransform.get()));
        } else {
            return new DasmTransform(target, Either.left(methodTransforms));
        }
    }

    public ClassNode doTransform(ClassNode input, Either<List<MethodTransform>, ClassTransform> transform) {
        transform.left().ifPresent(methodTransforms -> {
            Transformer.TransformResult<List<MethodNode>> result = transformer.transform(input, methodTransforms);
            handleNotification(result.notifications());

            result.changed().forEach(method -> {
                // By default, Mixin will merge the native dasm transform method over the dasm output method, this is the easiest way to prevent it
                // Unfortunately mixin outputs a warning for every case. See MixinApplicatorStandard#isAlreadyMerged
                if (method.visibleAnnotations == null) method.visibleAnnotations = new ArrayList<>(1);
                method.visibleAnnotations.add(new AnnotationNode("Lorg/spongepowered/asm/mixin/Final;"));
            });

        });
        transform.right().ifPresent(classTransform -> {
            try {
                transformer.transform(input, classTransform);
            } catch (NoSuchTypeExists e) { // TODO: remove when dasm doesn't throw here.
                handleNotification(Collections.singletonList(new Notification(e.getMessage(), Notification.Kind.ERROR, e.getClass())));
            }
        });
        doDasmOut(input);
        return input;
    }


    static class DasmTarget {
        ClassNode primary;
        Optional<ClassNode> secondary;

        DasmTarget(ClassNode primary, Optional<ClassNode> secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }
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

    private void doDasmOut(ClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        try {
            Path path = Paths.get(".dasm.out/APPLY/" + classNode.name.replace('.', '/') + ".class").toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.write(path, classWriter.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        for (Notification notification : notifications) {
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
            throw new RuntimeException("DASM Failure, please see log output");
        }
    }
}
