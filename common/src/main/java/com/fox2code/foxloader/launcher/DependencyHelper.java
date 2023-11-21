package com.fox2code.foxloader.launcher;

import com.fox2code.foxloader.launcher.utils.NetUtils;
import com.fox2code.foxloader.launcher.utils.Platform;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Files;
import java.util.jar.JarFile;

public class DependencyHelper {
    public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    public static final String SPONGE_POWERED = "https://repo.spongepowered.org/maven";
    public static final String JITPACK = "https://jitpack.io";
    public static final String MODRINTH = "https://api.modrinth.com/maven";

    public static final Dependency GSON_DEPENDENCY = // Used by installer.
            new Dependency("com.google.code.gson:gson:2.10.1", MAVEN_CENTRAL, "com.google.gson.Gson");

    // Extra dependencies not included in ReIndev
    public static final Dependency[] commonDependencies = new Dependency[]{
            new Dependency("org.ow2.asm:asm:9.6", MAVEN_CENTRAL, "org.objectweb.asm.ClassVisitor"),
            new Dependency("org.ow2.asm:asm-tree:9.6", MAVEN_CENTRAL, "org.objectweb.asm.tree.ClassNode"),
            new Dependency("org.ow2.asm:asm-analysis:9.6", MAVEN_CENTRAL, "org.objectweb.asm.tree.analysis.Analyzer"),
            new Dependency("org.ow2.asm:asm-commons:9.6", MAVEN_CENTRAL, "org.objectweb.asm.commons.InstructionAdapter"),
            new Dependency("org.ow2.asm:asm-util:9.6", MAVEN_CENTRAL, "org.objectweb.asm.util.CheckClassAdapter"),
            GSON_DEPENDENCY, new Dependency("com.google.guava:guava:21.0", MAVEN_CENTRAL, "com.google.common.io.Files"),
            new Dependency("org.semver4j:semver4j:5.2.2", MAVEN_CENTRAL, "org.semver4j.Semver"),
            new Dependency("org.apache.commons:commons-lang3:3.3.2", MAVEN_CENTRAL, "org.apache.commons.lang3.tuple.Pair"),
            new Dependency("org.luaj:luaj-jse:3.0.1", MAVEN_CENTRAL, "org.luaj.vm2.Globals"),
            new Dependency("org.spongepowered:mixin:0.8.5", SPONGE_POWERED, "org.spongepowered.asm.mixin.Mixins"),
            new Dependency("com.github.LlamaLad7.MixinExtras:mixinextras-common:0.2.1",
                    JITPACK, "com.llamalad7.mixinextras.MixinExtrasBootstrap",
                    // Need fallback URL cause JitPack links can ded at any time
                    "https://github.com/LlamaLad7/MixinExtras/releases/download/0.2.1/mixinextras-common-0.2.1.jar"),
    };

    public static final Dependency sparkDependency =
            new Dependency(BuildConfig.SPARK_DEPENDENCY, MODRINTH, "me.lucko.spark.common.SparkPlugin");

    public static final Dependency[] clientDependencies = new Dependency[]{
            new Dependency("net.silveros:reindev:" + BuildConfig.REINDEV_VERSION,
                    BuildConfig.CLIENT_URL, "net.minecraft.src.client.MinecraftImpl")
    };
    public static final Dependency[] serverDependencies = new Dependency[]{
            new Dependency("net.silveros:reindev-server:" + BuildConfig.REINDEV_VERSION,
                    BuildConfig.SERVER_URL, "net.minecraft.server.MinecraftServer")
    };

    private static File mcLibraries;

    static {
        if (Platform.getJvmVersion() == 8) {
            try {
                LetsEncryptHelper.installCertificates();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void loadDependencies(boolean client) {
        if (client) {
            URL url = DependencyHelper.class.getResource("/org/lwjgl/opengl/GLChecks.class");
            if (url != null) {
                try {
                    URLConnection urlConnection = url.openConnection();
                    if (urlConnection instanceof JarURLConnection) {
                        url = ((JarURLConnection) urlConnection).getJarFileURL();
                        String lwjglPath = url.toURI().getPath();
                        int i = lwjglPath.indexOf("org/lwjgl/");
                        if (i != -1 && lwjglPath.endsWith(".jar")) {
                            mcLibraries = new File(lwjglPath.substring(0, i));

                        }
                    }
                } catch (IOException | URISyntaxException ignored) {}
            }
            setMCLibraryRoot();
        } else {
            mcLibraries = new File("libraries").getAbsoluteFile();
        }
        for (Dependency dependency : commonDependencies) {
            loadDependency(dependency);
        }
        for (Dependency dependency : (client ? clientDependencies : serverDependencies)) {
            loadDependencyImpl(dependency, true, false);
        }
    }

    public static void setMCLibraryRoot() {
        String mcLibrariesPath;
        switch (Platform.getPlatform()) {
            case WINDOWS:
                mcLibrariesPath = System.getenv("APPDATA") + "\\.minecraft\\";
                break;
            case MACOS:
                mcLibrariesPath = System.getProperty("user.home") + "/Library/Application Support/minecraft/";
                break;
            case LINUX:
                mcLibrariesPath = System.getProperty("user.home") + "/.minecraft/";
                break;
            default:
                throw new RuntimeException("Unsupported operating system");
        }
        if (mcLibraries == null) {
            mcLibraries = new File(mcLibrariesPath + "libraries");
        } else if (FoxLauncher.launcherType == LauncherType.UNKNOWN &&
                mcLibrariesPath.equals(mcLibraries.getAbsoluteFile().getParent())) {
            FoxLauncher.launcherType = LauncherType.VANILLA_LIKE;
        }
    }

    public static void loadDevDependencies(File cacheRoot, boolean client) {
        if (FoxLauncher.getFoxClassLoader() != null) return; // Why???
        mcLibraries = cacheRoot;
        for (Dependency dependency : (client ? clientDependencies : serverDependencies)) {
            loadDependencyImpl(dependency, true, true);
        }
    }

    public static boolean loadDependencySafe(Dependency dependency) {
        try {
            loadDependencyImpl(dependency, false, FoxLauncher.foxClassLoader == null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void loadDependency(Dependency dependency) {
        loadDependencyImpl(dependency, false, FoxLauncher.foxClassLoader == null);
    }

    private static File loadDependencyImpl(Dependency dependency, boolean minecraft, boolean dev) {
        if (!dev && hasClass(dependency.classCheck)) return null;
        String postURL = resolvePostURL(dependency.name);
        File file = new File(mcLibraries, fixUpPath(postURL));
        boolean justDownloaded = false;
        if (!file.exists()) {
            File parentFile = file.getParentFile();
            if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
                throw new RuntimeException("Cannot create dependency directory for " + dependency.name);
            }
            IOException fallBackIoe = null;
            try (OutputStream os = Files.newOutputStream(file.toPath())) {
                justDownloaded = true;
                NetUtils.downloadTo(new URL(dependency.repository.endsWith(".jar") ?
                        dependency.repository : dependency.repository + "/" + postURL), os);
            } catch (IOException ioe) {
                if (dependency.fallbackUrl != null) {
                    fallBackIoe = ioe;
                } else {
                    if (file.exists() && !file.delete()) file.deleteOnExit();
                    throw new RuntimeException("Cannot download " + dependency.name, ioe);
                }
            }
            if (fallBackIoe != null) {
                try (OutputStream os = Files.newOutputStream(file.toPath())) {
                    justDownloaded = true;
                    NetUtils.downloadTo(new URL(dependency.fallbackUrl), os);
                } catch (IOException ioe) {
                    if (file.exists() && !file.delete()) file.deleteOnExit();
                    throw new RuntimeException("Cannot download " + dependency.name, fallBackIoe);
                }
            }
        }
        if (dev) return file; // We don't have a FoxClass loader in dev environment.
        try {
            if (minecraft) {
                FoxLauncher.getFoxClassLoader().setMinecraftURL(file.toURI().toURL());
            } else {
                FoxLauncher.getFoxClassLoader().addURL(file.toURI().toURL());
            }
            if (hasClass(dependency.classCheck)) {
                System.out.println("Loaded " +
                        dependency.name + " -> " + file.getPath());
            } else {
                if (!justDownloaded) {
                    // Assume file is corrupted if load failed.
                    if (file.exists() && !file.delete()) file.deleteOnExit();
                    loadDependency(dependency);
                    return file;
                }
                throw new RuntimeException("Failed to load " +
                        dependency.name + " -> " + file.getPath());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return file;
    }

    public static class Agent {
        private static Instrumentation inst = null;

        public static void premain(final String agentArgs, final Instrumentation inst) {
            if (FoxLauncher.foxClassLoader != null)
                throw new IllegalStateException("FoxClassLoader already initialized!");
            Agent.inst = inst;
        }

        public static void agentmain(final String agentArgs, final Instrumentation inst) {
            if (FoxLauncher.foxClassLoader != null)
                throw new IllegalStateException("FoxClassLoader already initialized!");
            Agent.inst = inst;
        }

        static boolean supported() {
            return inst != null;
        }

        static void addToClassPath(final File library) {
            if (inst == null) {
                System.err.println("Unable to retrieve Instrumentation API to add Paper jar to classpath. If you're " +
                        "running paperclip without -jar then you also need to include the -javaagent:<paperclip_jar> JVM " +
                        "command line option.");
                System.exit(1);
                return;
            }
            try {
                inst.appendToSystemClassLoaderSearch(new JarFile(library));
            } catch (final IOException e) {
                System.err.println("Failed to add Paper jar to ClassPath");
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    public static void loadDependencySelf(Dependency dependency) {
        if (FoxLauncher.foxClassLoader != null)
            throw new IllegalStateException("FoxClassLoader already initialized!");
        if (DependencyHelper.class.getClassLoader().getResource(
                dependency.classCheck.replace('.', '/') + ".class") != null) {
            return; // Great news, we already have the library loaded!
        }
        if (mcLibraries == null) setMCLibraryRoot();
        File file = loadDependencyImpl(dependency, false, true);
        if (file == null) {
            // If null it means it's already in class path.
            return;
        }
        if (Agent.supported()) {
            Agent.addToClassPath(file);
        } else {
            final ClassLoader loader = ClassLoader.getSystemClassLoader();
            if (!(loader instanceof URLClassLoader)) {
                throw new RuntimeException("System ClassLoader is not URLClassLoader");
            }
            try {
                final Method addURL = getURLClassLoaderAddMethod(loader);
                if (addURL == null) {
                    throw new RuntimeException("Unable to find method to add library jar to System ClassLoader");
                }
                addURL.setAccessible(true);
                addURL.invoke(loader, file.toURI().toURL());
            } catch (final ReflectiveOperationException | MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Method getURLClassLoaderAddMethod(final Object o) {
        Class<?> clazz = o.getClass();
        Method m = null;
        while (m == null) {
            try {
                m = clazz.getDeclaredMethod("addURL", URL.class);
            } catch (final NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
                if (clazz == null) {
                    return null;
                }
            }
        }
        return m;
    }

    private static String fixUpPath(String path) {
        return Platform.getPlatform() == Platform.WINDOWS ?
                path.replace('/', '\\') : path;
    }

    public static boolean hasClass(String cls) {
        return FoxLauncher.getFoxClassLoader().isClassInClassPath(cls);
    }

    private static String resolvePostURL(String string) {
        String[] depKeys = string.split(":");
        // "org.joml:rrrr:${jomlVersion}"      => ${repo}/org/joml/rrrr/1.9.12/rrrr-1.9.12.jar
        // "org.joml:rrrr:${jomlVersion}:rrrr" => ${repo}/org/joml/rrrr/1.9.12/rrrr-1.9.12-rrrr.jar
        if (depKeys.length == 3) {
            return depKeys[0].replace('.','/')+"/"+depKeys[1]+"/"+depKeys[2]+"/"+depKeys[1]+"-"+depKeys[2]+".jar";
        }
        if (depKeys.length == 4) {
            return depKeys[0].replace('.','/')+"/"+depKeys[1]+"/"+depKeys[2]+"/"+depKeys[1]+"-"+depKeys[2]+"-"+depKeys[3]+".jar";
        }
        throw new RuntimeException("Invalid Dep");
    }

    public static class Dependency {
        public final String name, repository, classCheck, fallbackUrl;

        public Dependency(String name, String repository, String classCheck) {
            this(name, repository, classCheck, null);
        }

        public Dependency(String name, String repository, String classCheck, String fallbackUrl) {
            this.name = name;
            this.repository = repository;
            this.classCheck = classCheck;
            this.fallbackUrl = fallbackUrl;
        }
    }
}
