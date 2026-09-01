import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Fills incomplete Android/AndroidX stubs with every public method from
 * android.jar and androidx AARs so plugins stop dying on NoSuchMethodError /
 * NoClassDefFoundError. Hand-written implementations are kept; missing members
 * become no-ops that return default values.
 */
public final class MergeAndroidCompat {
    private static final int ASM = Opcodes.ASM9;

    public static void main(String[] args) throws Exception {
        File androidJar = null;
        File outDir = null;
        List<File> classDirs = new ArrayList<>();
        List<File> libs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--android-jar":
                    androidJar = new File(args[++i]);
                    break;
                case "--out":
                    outDir = new File(args[++i]);
                    break;
                case "--classes":
                    classDirs.add(new File(args[++i]));
                    break;
                case "--lib":
                    libs.add(new File(args[++i]));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown arg " + args[i]);
            }
        }
        if (androidJar == null || !androidJar.isFile() || outDir == null) {
            throw new IllegalStateException("Need --android-jar and --out");
        }
        outDir.mkdirs();
        deleteStale(outDir);

        Map<String, byte[]> ours = new HashMap<>();
        for (File dir : classDirs) {
            if (dir.isDirectory()) loadDir(dir, dir, ours);
        }

        Map<String, byte[]> framework = new HashMap<>();
        loadJar(androidJar, framework);
        for (File lib : libs) {
            if (lib == null || !lib.isFile()) continue;
            String n = lib.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".aar")) loadAar(lib, framework);
            else if (n.endsWith(".jar")) loadJar(lib, framework);
        }

        int copied = 0;
        int skipped = 0;
        int filled = 0;
        for (Map.Entry<String, byte[]> e : framework.entrySet()) {
            String name = e.getKey();
            if (!keep(name)) {
                skipped++;
                continue;
            }
            try {
                byte[] have = ours.get(name);
                if (have != null) {
                    // Keep javac method bodies (stackmaps). Only append missing
                    // members as no-ops so plugins stop dying on NoSuchMethodError.
                    byte[] merged = fillMissing(have, e.getValue(), ours);
                    if (merged != have) {
                        writeClass(outDir, name, merged);
                        ours.put(name, merged);
                        filled++;
                    }
                    continue;
                }
                writeClass(outDir, name, noopClass(e.getValue()));
                copied++;
            } catch (Throwable t) {
                try {
                    writeClass(outDir, name, e.getValue());
                    copied++;
                } catch (Throwable ignored) {
                    skipped++;
                }
            }
        }
        System.out.println("Android compat: copied=" + copied + " filled=" + filled + " skipped=" + skipped);
    }

    private static boolean keep(String internal) {
        if (internal.startsWith("android/")) return true;
        if (internal.startsWith("dalvik/")) return true;
        if (internal.startsWith("org/xmlpull/")) return true;
        if (internal.startsWith("org/apache/http/")) return true;
        if (internal.startsWith("com/android/")) return true;
        if (internal.startsWith("com/google/android/material/")) return true;
        // Never overlay Compose Desktop's androidx.lifecycle / activity / collection.
        if (internal.startsWith("androidx/lifecycle/")) return false;
        if (internal.startsWith("androidx/activity/")) return false;
        if (internal.startsWith("androidx/savedstate/")) return false;
        if (internal.startsWith("androidx/compose/")) return false;
        if (internal.startsWith("androidx/collection/")) return false;
        if (internal.startsWith("androidx/arch/")) return false;
        if (internal.startsWith("androidx/annotation/")) return false;
        if (internal.startsWith("androidx/navigation/")) return false;
        if (internal.startsWith("androidx/preference/")) return true;
        if (internal.startsWith("androidx/fragment/")) return true;
        if (internal.startsWith("androidx/appcompat/")) return true;
        if (internal.startsWith("androidx/recyclerview/")) return true;
        if (internal.startsWith("androidx/coordinatorlayout/")) return true;
        if (internal.startsWith("androidx/core/")) return true;
        if (internal.startsWith("androidx/viewpager")) return true;
        if (internal.startsWith("androidx/drawerlayout/")) return true;
        if (internal.startsWith("androidx/webkit/")) return true;
        return false;
    }

    private static void deleteStale(File outDir) {
        String[] stale = {
            "androidx/lifecycle",
            "androidx/savedstate",
            "androidx/compose",
            "androidx/collection",
            "androidx/arch",
            "androidx/navigation",
        };
        for (String rel : stale) {
            File dir = new File(outDir, rel);
            if (dir.isDirectory()) deleteTree(dir);
        }
    }

    private static void deleteTree(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteTree(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static void loadDir(File root, File dir, Map<String, byte[]> out) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) loadDir(root, f, out);
            else if (f.getName().endsWith(".class")) {
                String rel = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                String name = rel.substring(0, rel.length() - 6);
                out.put(name, Files.readAllBytes(f.toPath()));
            }
        }
    }

    private static void loadJar(File jar, Map<String, byte[]> out) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                String name = e.getName().substring(0, e.getName().length() - 6);
                if (out.containsKey(name)) continue;
                try (InputStream in = zip.getInputStream(e)) {
                    out.put(name, readAll(in));
                }
            }
        }
    }

    private static void loadAar(File aar, Map<String, byte[]> out) throws IOException {
        try (ZipFile zip = new ZipFile(aar)) {
            ZipEntry classes = zip.getEntry("classes.jar");
            if (classes == null) return;
            File tmp = File.createTempFile("aar-classes", ".jar");
            try (InputStream in = zip.getInputStream(classes)) {
                Files.write(tmp.toPath(), readAll(in));
            }
            try {
                loadJar(tmp, out);
            } finally {
                tmp.delete();
            }
        }
    }

    private static final class ExtraField {
        final int access;
        final String name;
        final String descriptor;
        final String signature;
        final Object value;
        ExtraField(int access, String name, String descriptor, String signature, Object value) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.value = value;
        }
    }

    private static final class ExtraMethod {
        final int access;
        final String name;
        final String descriptor;
        final String signature;
        final String[] exceptions;
        ExtraMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
        }
    }

    private static byte[] fillMissing(byte[] have, byte[] framework, Map<String, byte[]> ours) {
        ClassReader self = new ClassReader(have);
        Set<String> haveMethods = collectInheritedMethods(self.getClassName(), ours);
        Set<String> haveFields = collectFields(have);
        final List<ExtraMethod> extraMethods = new ArrayList<>();
        final List<ExtraField> extraFields = new ArrayList<>();
        new ClassReader(framework).accept(new ClassVisitor(ASM) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (!haveFields.contains(name + descriptor) && (access & Opcodes.ACC_PUBLIC) != 0) {
                    extraFields.add(new ExtraField(access, name, descriptor, signature, value));
                }
                return null;
            }
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("<clinit>".equals(name) || "<init>".equals(name)) return null;
                if ((access & Opcodes.ACC_PUBLIC) == 0 && (access & Opcodes.ACC_PROTECTED) == 0) return null;
                if (haveMethods.contains(name + descriptor)) return null;
                extraMethods.add(new ExtraMethod(access, name, descriptor, signature, exceptions));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (extraMethods.isEmpty() && extraFields.isEmpty()) return have;

        ClassReader cr = new ClassReader(have);
        boolean iface = (cr.getAccess() & Opcodes.ACC_INTERFACE) != 0;
        String superName = cr.getSuperName();
        String owner = cr.getClassName();
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(ASM, cw) {
            @Override
            public void visitEnd() {
                for (ExtraField field : extraFields) {
                    FieldVisitor fv = cw.visitField(field.access, field.name, field.descriptor, field.signature, field.value);
                    if (fv != null) fv.visitEnd();
                }
                for (ExtraMethod method : extraMethods) {
                    emitMethod(cw, method.access, method.name, method.descriptor, method.signature, method.exceptions, iface, superName, owner);
                }
                super.visitEnd();
            }
        }, 0);
        return cw.toByteArray();
    }

    private static Set<String> collectInheritedMethods(String className, Map<String, byte[]> ours) {
        Set<String> keys = new HashSet<>();
        String name = className;
        Set<String> seen = new HashSet<>();
        while (name != null && seen.add(name)) {
            byte[] bytes = ours.get(name);
            if (bytes == null) break;
            keys.addAll(collectMethods(bytes));
            ClassReader cr = new ClassReader(bytes);
            name = cr.getSuperName();
            if ("java/lang/Object".equals(name)) break;
        }
        return keys;
    }

    private static Set<String> collectMethods(byte[] cls) {
        Set<String> keys = new HashSet<>();
        new ClassReader(cls).accept(new ClassVisitor(ASM) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                keys.add(name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return keys;
    }

    private static Set<String> collectFields(byte[] cls) {
        Set<String> keys = new HashSet<>();
        new ClassReader(cls).accept(new ClassVisitor(ASM) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                keys.add(name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return keys;
    }

    private static ClassWriter writer() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                if (type1 == null || type2 == null) return "java/lang/Object";
                if (type1.equals(type2)) return type1;
                if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
                    return "java/lang/Object";
                }
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable ignored) {
                    return "java/lang/Object";
                }
            }
        };
    }

    private static byte[] noopClass(byte[] framework) {
        ClassReader cr = new ClassReader(framework);
        int access = cr.getAccess();
        if ((access & Opcodes.ACC_MODULE) != 0) return framework;
        if ((access & Opcodes.ACC_ENUM) != 0) return framework;
        ClassWriter cw = writer();
        cr.accept(new ClassVisitor(ASM, cw) {
            @Override
            public MethodVisitor visitMethod(int a, String name, String descriptor, String signature, String[] exceptions) {
                boolean iface = (cr.getAccess() & Opcodes.ACC_INTERFACE) != 0;
                if (iface && (a & Opcodes.ACC_ABSTRACT) != 0 && (a & Opcodes.ACC_STATIC) == 0 && !"<init>".equals(name)) {
                    return super.visitMethod(a, name, descriptor, signature, exceptions);
                }
                emitMethod(cw, a, name, descriptor, signature, exceptions, iface, cr.getSuperName(), cr.getClassName());
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    private static void emitMethod(
            ClassWriter cw,
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions,
            boolean iface,
            String superName,
            String owner
    ) {
        int acc = access & ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);
        if (iface && (access & Opcodes.ACC_STATIC) == 0 && !"<init>".equals(name)) {
            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                MethodVisitor mv = cw.visitMethod(access, name, descriptor, signature, exceptions);
                mv.visitEnd();
                return;
            }
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0 && iface) {
            MethodVisitor mv = cw.visitMethod(access, name, descriptor, signature, exceptions);
            mv.visitEnd();
            return;
        }
        MethodVisitor mv = cw.visitMethod(acc, name, descriptor, signature, exceptions);
        mv.visitCode();
        if ("<init>".equals(name)) {
            emitCtor(mv, descriptor, superName, owner);
        } else {
            emitReturn(mv, descriptor);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitCtor(MethodVisitor mv, String descriptor, String superName, String owner) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        String superCtor = pickSuperCtor(descriptor, superName);
        Type[] args = Type.getArgumentTypes(superCtor);
        Type[] available = Type.getArgumentTypes(descriptor);
        int slot = 1;
        List<Integer> slots = new ArrayList<>();
        for (Type t : available) {
            slots.add(slot);
            slot += t.getSize();
        }
        int used = 0;
        for (Type need : args) {
            int found = -1;
            for (int i = used; i < available.length; i++) {
                if (available[i].getSort() == need.getSort()
                        && (need.getSort() != Type.OBJECT || available[i].getInternalName().equals(need.getInternalName())
                        || "java/lang/Object".equals(need.getInternalName()))) {
                    found = i;
                    used = i + 1;
                    break;
                }
            }
            if (found >= 0) {
                load(mv, available[found], slots.get(found));
            } else {
                pushDefault(mv, need);
            }
        }
        String target = superName != null ? superName : "java/lang/Object";
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, target, "<init>", superCtor, false);
        mv.visitInsn(Opcodes.RETURN);
    }

    private static String pickSuperCtor(String desc, String superName) {
        Type[] args = Type.getArgumentTypes(desc);
        if (args.length > 0 && args[0].getSort() == Type.OBJECT
                && "android/content/Context".equals(args[0].getInternalName())) {
            return "(Landroid/content/Context;)V";
        }
        if ("java/lang/Object".equals(superName) || superName == null) {
            return "()V";
        }
        if (args.length == 0) return "()V";
        if (args.length > 0 && args[0].getSort() == Type.OBJECT) {
            return "(" + args[0].getDescriptor() + ")V";
        }
        return "()V";
    }

    private static void emitReturn(MethodVisitor mv, String descriptor) {
        Type ret = Type.getReturnType(descriptor);
        switch (ret.getSort()) {
            case Type.VOID:
                mv.visitInsn(Opcodes.RETURN);
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.LCONST_0);
                mv.visitInsn(Opcodes.LRETURN);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.FCONST_0);
                mv.visitInsn(Opcodes.FRETURN);
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.DCONST_0);
                mv.visitInsn(Opcodes.DRETURN);
                break;
            default:
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
                break;
        }
    }

    private static void load(MethodVisitor mv, Type type, int slot) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                mv.visitVarInsn(Opcodes.ILOAD, slot);
                break;
            case Type.LONG:
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                break;
            case Type.FLOAT:
                mv.visitVarInsn(Opcodes.FLOAD, slot);
                break;
            case Type.DOUBLE:
                mv.visitVarInsn(Opcodes.DLOAD, slot);
                break;
            default:
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                break;
        }
    }

    private static void pushDefault(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                mv.visitInsn(Opcodes.ICONST_0);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.LCONST_0);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.FCONST_0);
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.DCONST_0);
                break;
            default:
                mv.visitInsn(Opcodes.ACONST_NULL);
                break;
        }
    }

    private static void writeClass(File outDir, String internal, byte[] bytes) throws IOException {
        File dest = new File(outDir, internal + ".class");
        dest.getParentFile().mkdirs();
        Files.write(dest.toPath(), bytes);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) >= 0) buf.write(b, 0, n);
        return buf.toByteArray();
    }
}
