package com.oj.agent.core.executor;

import org.graalvm.polyglot.Engine;

import java.util.Set;
import java.util.TreeSet;

public final class GraalRuntimeDiagnostics {

    private static final String ENGINE_WARN_INTERPRETER_ONLY_OPTION = "engine.WarnInterpreterOnly";
    private static final String UNKNOWN_VALUE = "<unknown>";
    private static final int ABBREVIATED_MAX_LENGTH = 240;
    private static final int ABBREVIATED_PREFIX_LENGTH = 237;

    private final Set<String> installedLanguages;
    private final String vmName;
    private final String javaVendor;
    private final String classPath;
    private final String javaCommand;

    public GraalRuntimeDiagnostics(Set<String> installedLanguages,
                                   String vmName,
                                   String javaVendor,
                                   String classPath,
                                   String javaCommand) {
        this.installedLanguages = Set.copyOf(new TreeSet<>(installedLanguages));
        this.vmName = safeValue(vmName);
        this.javaVendor = safeValue(javaVendor);
        this.classPath = safeValue(classPath);
        this.javaCommand = safeValue(javaCommand);
    }

    public static GraalRuntimeDiagnostics capture() {
        try (Engine engine = Engine.newBuilder().option(ENGINE_WARN_INTERPRETER_ONLY_OPTION, "false").build()) {
            return capture(engine);
        }
    }

    public static GraalRuntimeDiagnostics capture(Engine engine) {
        return new GraalRuntimeDiagnostics(
                engine.getLanguages().keySet(),
                System.getProperty("java.vm.name"),
                System.getProperty("java.vendor"),
                System.getProperty("java.class.path"),
                System.getProperty("sun.java.command")
        );
    }

    public boolean hasLanguage(String language) {
        return installedLanguages.contains(language);
    }

    public Set<String> getInstalledLanguages() {
        return installedLanguages;
    }

    public String summary() {
        return String.format(
                "Installed languages: %s; Java VM: %s; Java vendor: %s; Java command: %s; Classpath: %s",
                installedLanguages,
                vmName,
                javaVendor,
                javaCommand,
                abbreviate(classPath)
        );
    }

    public String missingLanguageMessage(String language) {
        return String.format(
                "GraalVM language '%s' is not available. Installed languages: %s. Java VM: %s. Java vendor: %s. " +
                        "Java command: %s. Classpath: %s. If you are running a Spring Boot fat jar, do not use the " +
                        "nested-jar launcher. Start with an expanded classpath such as BOOT-INF/classes and BOOT-INF/lib/*, " +
                        "or use the repository Dockerfile entrypoint.",
                language,
                installedLanguages,
                vmName,
                javaVendor,
                javaCommand,
                abbreviate(classPath)
        );
    }

    private static String safeValue(String value) {
        return value == null || value.isBlank() ? UNKNOWN_VALUE : value;
    }

    private static String abbreviate(String value) {
        if (value.length() <= ABBREVIATED_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, ABBREVIATED_PREFIX_LENGTH) + "...";
    }
}
