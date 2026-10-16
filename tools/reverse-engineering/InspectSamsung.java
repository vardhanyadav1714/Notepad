import jadx.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only, focused inspection using the JADX engine supplied with REA. */
class InspectSamsung {
    public static void main(String[] args) throws Exception {
        JadxArgs config = new JadxArgs();
        config.setInputFiles(List.of(new File(args[0])));
        config.setThreadsCount(1);
        try (JadxDecompiler jadx = new JadxDecompiler(config)) {
            jadx.load();
            Path output = Path.of(args[1]);
            Files.createDirectories(output);
            var names = jadx.getClassesWithInners().stream().map(JavaClass::getFullName)
                .filter(n -> n.startsWith("com.samsung.android"))
                .filter(n -> n.matches("(?i).*(MainList|NotesList|Composer|Folder|Search|Toolbar|Activity|Pen|Eraser|Shape).*"))
                .sorted().toList();
            Files.write(output.resolve("ui-classes.txt"), names);
            System.out.println("Classes: " + jadx.getClassesWithInners().size() + "; UI candidates: " + names.size());
            for (int i = 2; i < args.length; i++) {
                String name = args[i];
                var cls = jadx.getClassesWithInners().stream().filter(c -> c.getFullName().equals(name)).findFirst();
                if (cls.isPresent()) {
                    Files.writeString(output.resolve(name + ".java.txt"), cls.get().getCode());
                    System.out.println("Decompiled " + name);
                } else System.out.println("Class not found: " + name);
            }
        }
    }
}
