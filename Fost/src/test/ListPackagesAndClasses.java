package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Alat za ispis paketa i pripadajućih top-level Java klasa.
 * - Pokušava automatski detektirati root source folder.
 * - Možeš navesti root kao prvi argument (npr. "src" ili "Fost/src").
 */
public class ListPackagesAndClasses {

    public static void main(String[] args) {
        try {
            Path root = resolveRoot(args);
            System.out.println("WORKING DIR = " + System.getProperty("user.dir"));
            System.out.println("Korišteni SOURCE ROOT = " + root.toAbsolutePath());

            Result result = listPackagesAndClasses(root);

            System.out.println("\nPronađeni paketi i klase (" + root + "):");
            result.packages.forEach((pkg, classes) -> {
                System.out.println("- paket " + pkg + " (" + classes.size() + "):");
                classes.forEach(c -> System.out.println("    • " + c));
            });

            System.out.println("\nPotpuno kvalificirana imena:");
            result.packages.forEach((pkg, classes) -> {
                classes.forEach(c -> {
                    if ("(default)".equals(pkg)) {
                        System.out.println(c);
                    } else {
                        System.out.println(pkg + "." + c);
                    }
                });
            });

        } catch (IOException e) {
            System.err.println("Greška pri čitanju: " + e.getMessage());
        }
    }

    /**
     * Pokušava pronaći root source folder.
     * Redoslijed:
     * 1) Ako je korisnik dao args[0] → uzmi to.
     * 2) Inače probaj "src"
     * 3) Zatim "Fost/src"
     * 4) Zatim "Fost/Fost/src"
     */
    private static Path resolveRoot(String[] args) throws IOException {
        if (args.length > 0) {
            Path argRoot = Paths.get(args[0]);
            if (Files.isDirectory(argRoot)) return argRoot;
            throw new IOException("Zadani argument root ne postoji: " + argRoot.toAbsolutePath());
        }
        List<Path> candidates = List.of(
                Paths.get("src"),
                Paths.get("Fost", "src"),
                Paths.get("Fost", "Fost", "src")
        );

        for (Path p : candidates) {
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new IOException("Nijedan candidate source root nije pronađen (pokušano: " + candidates + ")");
    }

    // === JAVNA METODA ZA PONOVNU UPORABU ===
    public static Result listPackagesAndClasses(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Root folder ne postoji: " + root.toAbsolutePath());
        }

        Map<String, List<String>> pkgs = new TreeMap<>();

        try (var stream = Files.walk(root)) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                String pkg = readPackageName(file);
                String cls = stripExtension(file.getFileName().toString());
                pkgs.computeIfAbsent(pkg, k -> new ArrayList<>()).add(cls);
            }
        }

        pkgs.values().forEach(list -> list.sort(String::compareTo));

        Set<String> fqcn = new TreeSet<>();
        pkgs.forEach((pkg, classes) -> {
            for (String c : classes) {
                if ("(default)".equals(pkg)) fqcn.add(c);
                else fqcn.add(pkg + "." + c);
            }
        });

        return new Result(pkgs, fqcn);
    }

    public static class Result {
        public final Map<String, List<String>> packages;
        public final Set<String> fullyQualifiedClassNames;
        public Result(Map<String, List<String>> packages, Set<String> fullyQualifiedClassNames) {
            this.packages = packages;
            this.fullyQualifiedClassNames = fullyQualifiedClassNames;
        }
    }

    // --- Privatne pomoćne ---
    private static String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return (i >= 0) ? name.substring(0, i) : name;
    }

    private static String readPackageName(Path file) {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                    continue;
                }
                if (line.startsWith("package ")) {
                    String rest = line.substring("package ".length());
                    int semi = rest.indexOf(';');
                    if (semi >= 0) {
                        rest = rest.substring(0, semi);
                    }
                    // odreži inline komentar ako postoji
                    int slashSlash = rest.indexOf("//");
                    if (slashSlash >= 0) {
                        rest = rest.substring(0, slashSlash);
                    }
                    int blockComment = rest.indexOf("/*");
                    if (blockComment >= 0) {
                        rest = rest.substring(0, blockComment);
                    }
                    return rest.trim();
                }
                break;
            }
        } catch (Exception ignored) {}
        return "(default)";
    }
}