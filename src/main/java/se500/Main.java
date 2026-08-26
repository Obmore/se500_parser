package se500;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Main {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "samples");
        if (!Files.isDirectory(dir)) {
            System.err.println("Nincs ilyen mappa: " + dir.toAbsolutePath());
            System.exit(1);
        }

        Properties jdbc = new Properties();
        try (InputStream in = Main.class.getResourceAsStream("/jdbc.properties")) {
            jdbc.load(in);
        }

        SpiParser parser = new SpiParser();
        try (Store store = new Store(jdbc);
             DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.xml")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (store.alreadyLoaded(name)) {
                    System.out.println("már bent van, kihagyom: " + name);
                    continue;
                }
                try {
                    Inspection insp = parser.parse(file);
                    store.save(insp);
                    System.out.println(name + "  " + insp.serialCode + "  " + insp.status
                            + "  " + insp.measurements.size() + " feature");
                } catch (Exception e) {
                    store.rollbackQuietly();
                    System.err.println("hiba, fájl kihagyva: " + name + " - " + e.getMessage());
                }
            }
        }
    }
}
