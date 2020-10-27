///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS joda-time:joda-time:2.10.8

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "rename-gopro-files", mixinStandardHelpOptions = true, version = "0.1",
        description = "simple application to rename GoPro files")
class hello implements Callable<Integer> {

    public static void main(String... args) {
        int exitCode = new CommandLine(new hello()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        var currentDirectory = System.getProperty("user.dir");
        try (Stream<Path> paths = Files.walk(Paths.get(currentDirectory))) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(f -> !f.toFile().isDirectory())
                    .filter(f -> f.getFileName().toString().startsWith("GO"))
                    .filter(f -> f.getFileName().toString().endsWith(".MP4"))
                    .forEach(path -> {
                        try {
                            DateTime creationDate = new DateTime(Files.readAttributes(path, BasicFileAttributes.class).creationTime().toString(), DateTimeZone.forTimeZone(TimeZone.getTimeZone("Europe/Paris")));
                            DecimalFormat df = new DecimalFormat("00");
                            File renamedFile = new File("go-pro-" + //
                                    creationDate.getYear() + "-" + //
                                    df.format(creationDate.getMonthOfYear()) + "-" + //
                                    df.format(creationDate.getDayOfMonth()) + "_(" + //
                                    creationDate.getHourOfDay() + "h" + //
                                    creationDate.getMinuteOfHour() + ")" + //
                                    ".mp4");
                            boolean result = path.toFile().renameTo(renamedFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
//                        System.out.println(path.getFileName());
//                        path.toFile().renameTo(new File("TOAST.mp4"));
                    });
        }
        return 0;
    }

}
