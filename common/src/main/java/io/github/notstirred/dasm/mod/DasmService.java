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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class DasmService {
    private final ClassNodeProvider classProvider;
    private final Transformer transformer;
    private final AnnotationParser annotationParser;

    private static final Logger logger = LogManager.getLogger("dasm");

    public DasmService(MappingsProvider mappingsProvider, ClassNodeProvider classProvider) {
        this.classProvider = classProvider;
        this.transformer = new Transformer(classProvider, mappingsProvider);
        this.annotationParser = new AnnotationParser(this.classProvider);
    }

    public Either<List<MethodTransform>, ClassTransform> scanForTransforms(Type dasmClassType) throws NoSuchTypeExists {
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
    }

    public ClassNode doTransform(ClassNode input, Either<List<MethodTransform>, ClassTransform> transform) {
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


    private record DasmTarget(ClassNode primary, Optional<ClassNode> secondary) { }

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
            Path path = Path.of(".dasm.out/APPLY/" + classNode.name.replace('.', '/') + ".class").toAbsolutePath();
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
            throw new RuntimeException("DASM Failure, please see log output");
        }
    }
}
