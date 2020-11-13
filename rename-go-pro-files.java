///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS joda-time:joda-time:2.10.8
//DEPS commons-io:commons-io:2.6

import static java.lang.System.getProperty;
import static java.lang.System.out;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.Files.walk;
import static java.nio.file.Paths.get;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.contentEquals;
import static org.joda.time.DateTimeZone.forTimeZone;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.joda.time.DateTime;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "rename-gopro-files", mixinStandardHelpOptions = true, version = "0.1",
        description = "simple application to rename GoPro files")
class Rename implements Callable<Integer> {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";

    private static final String OUTPUT_EXTENSION = ".mp4";

    public static void main(String... args) {
        int exitCode = new CommandLine(new Rename()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        var currentDirectory = getProperty("user.dir");
        try (Stream<Path> paths = walk(get(currentDirectory))) {
            paths.filter(Files::isRegularFile).filter(f -> !f.toFile().isDirectory())
                    .filter(f -> f.getFileName().toString().startsWith("GO"))
                    .filter(f -> f.getFileName().toString().endsWith(".MP4")) //
                    .forEach(path -> {
                        try {
                            DateTime creationDate = new DateTime(
                                    readAttributes(path, BasicFileAttributes.class).creationTime().toString(),
                                    forTimeZone(TimeZone.getTimeZone("Europe/Paris")));
                            DecimalFormat df = new DecimalFormat("00");
                            String fileName = "go-pro-" + creationDate.getYear() + "-"
                                    + df.format(creationDate.getMonthOfYear()) + "-"
                                    + df.format(creationDate.getDayOfMonth()) + "_("
                                    + df.format(creationDate.getHourOfDay()) + "h"
                                    + df.format(creationDate.getMinuteOfHour()) + ")";
                            fileName = filenameAlreadyExist(currentDirectory, path, fileName);
                            File newFile = new File(fileName + OUTPUT_EXTENSION);
                            boolean renamed = path.toFile().renameTo(newFile);
                            if (renamed) {
                                out.println(GREEN + " ✓ " + RESET + YELLOW + path.toFile().getName() + RESET
                                        + " successfully renamed to " + BLUE + newFile + RESET);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
        return 0;
    }

    private String filenameAlreadyExist(String currentDirectory, Path path, String fileName) {
        if (Objects.requireNonNull(getRenamedMp4Names(currentDirectory)).contains(fileName + OUTPUT_EXTENSION)) {
            ArrayList<File> files = new ArrayList<>(Objects.requireNonNull(getRenamedMp4Files(currentDirectory)));
            return files.stream().map(file -> {
                String f = fileName;
                try {
                    if (!contentEquals(path.toFile(), file)) {
                         f += "-1";
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return f;
            }).findAny().orElse(fileName);
        }
        return fileName;
    }

    private List<String> getRenamedMp4Names(String currentDirectory) {
        try {
            return walk(get(currentDirectory)).filter(f -> f.getFileName().toString().endsWith(".mp4"))
                    .map(f -> f.getFileName().toString()).collect(toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<File> getRenamedMp4Files(String currentDirectory) {
        try {
            return walk(get(currentDirectory)).filter(f -> f.getFileName().toString().endsWith(".mp4"))
                    .map(Path::toFile).collect(toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
