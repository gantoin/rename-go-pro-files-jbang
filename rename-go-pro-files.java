///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS joda-time:joda-time:2.10.8
//DEPS commons-io:commons-io:2.6
//DEPS org.apache.commons:commons-lang3:3.11
//DEPS junit:junit:4.4
//DEPS org.assertj:assertj-core:3.8.0

import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.Files.walk;
import static java.nio.file.Paths.get;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.io.FileUtils.contentEquals;
import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joda.time.DateTimeZone.forTimeZone;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
    private static final String CURRENT_DIRECTORY = getProperty("user.dir");

    public static void main(String... args) {
        int exitCode = new CommandLine(new Rename()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        try (Stream<Path> paths = walk(get(CURRENT_DIRECTORY))) {
            paths.filter(Files::isRegularFile) //
                    .filter(f -> !f.toFile().isDirectory()) //
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
                                    + df.format(creationDate.getDayOfMonth()) + " ("
                                    + df.format(creationDate.getHourOfDay()) + "h"
                                    + df.format(creationDate.getMinuteOfHour()) + ")";
                            fileName = filenameAlreadyExist(path, fileName);
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

    private String filenameAlreadyExist(Path path, String fileName) throws IOException {
        if (walkMp4Files().map(f -> f.getFileName().toString()).collect(toSet())
                .contains(fileName + OUTPUT_EXTENSION)) {
            return walkMp4Files().map(Path::toFile).collect(toSet()).stream().map(file -> {
                String f = fileName;
                try {
                    if (!contentEquals(path.toFile(), file)) {
                        String lastChar = file.getName().substring(file.getName().length() - 5).substring(0, 1);
                        if (isNumeric(lastChar)) {
                            f += "-" + (parseInt(lastChar) + 1);
                        } else {
                            f += "-1";
                            f = filenameAlreadyExist(path, f);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return f;
            }).findAny().orElse(fileName);
        }
        return fileName;
    }

    private Stream<Path> walkMp4Files() throws IOException {
        return walk(get(CURRENT_DIRECTORY)).filter(f -> f.getFileName().toString().endsWith(".mp4"));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static class RenameTest {

        @Before
        public void setUp() {

        }

        @After
        public void tearDown() throws IOException {
            walk(get(CURRENT_DIRECTORY)).filter(
                    f -> f.getFileName().toString().endsWith(".mp4") || f.getFileName().toString().endsWith(".MP4"))
                    .forEach(path -> {
                        new File(String.valueOf(path)).delete();
                    });
        }

        @Test
        public void overwriteSameFiles() throws Exception {
            Rename rename = new Rename();
            new FileOutputStream(new File(CURRENT_DIRECTORY + "/GOPRO1.MP4"));
            new FileOutputStream(new File(CURRENT_DIRECTORY + "/GOPRO2.MP4"));
            rename.call();
            Set<Path> paths = walk(get(CURRENT_DIRECTORY)).filter(f -> f.getFileName().toString().endsWith(".mp4"))
                    .collect(toSet());
            assertThat(paths).size().isEqualTo(1);
            assertThat(paths.iterator().next().toFile().getName()).contains("go-pro");
        }

        @Test
        public void renameDifferentFilesWithSameCreationTine() throws Exception {
            Rename rename = new Rename();
            try (FileOutputStream out = new FileOutputStream(new File(CURRENT_DIRECTORY + "/GOPRO1.MP4"))) {
                byte[] bytes = new byte[1024];
                new SecureRandom().nextBytes(bytes);
                out.write(bytes);
            }
            new FileOutputStream(new File(CURRENT_DIRECTORY + "/GOPRO2.MP4"));
            rename.call();
            Set<Path> paths = walk(get(CURRENT_DIRECTORY)).filter(f -> f.getFileName().toString().endsWith(".mp4"))
                    .collect(toSet());
            assertThat(paths).size().isEqualTo(2);
            List<String> fileNames = paths.stream().map(f -> f.toFile().getName()).collect(Collectors.toList());
            fileNames.forEach(fileName -> {
                assertThat(fileName).startsWith("go-pro");
            });
            assertThat(fileNames).doesNotContain("GOPRO");
        }

        @Test
        public void renameThreeDifferentFilesWithSameCreationTine() throws Exception {
            Rename rename = new Rename();
            try (FileOutputStream out = new FileOutputStream(new File(CURRENT_DIRECTORY + "/GOPRO1.MP4"))) {
                byte[] bytes = new byte[1024];
                new SecureRandom().nextBytes(bytes);
                out.write(bytes);
            }
            try (FileOutputStream out = new FileOutputStream(new File(CURRENT_DIRECTORY + "/GOPRO2.MP4"))) {
                byte[] bytes = new byte[64];
                new SecureRandom().nextBytes(bytes);
                out.write(bytes);
            }
            new FileOutputStream(new File(CURRENT_DIRECTORY + "/GOPRO3.MP4"));
            rename.call();
            Set<Path> paths = walk(get(CURRENT_DIRECTORY)).filter(f -> f.getFileName().toString().endsWith(".mp4"))
                    .collect(toSet());
            assertThat(paths).size().isEqualTo(3);
            List<String> fileNames = paths.stream().map(f -> f.toFile().getName()).collect(Collectors.toList());
            fileNames.forEach(fileName -> {
                assertThat(fileName).startsWith("go-pro");
            });
            assertThat(fileNames).doesNotContain("GOPRO");
        }

    }

}
