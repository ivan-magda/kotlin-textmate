package dev.textmate.buildlogic;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class ArchiveConventionTest {
    @Rule
    public TemporaryFolder project = new TemporaryFolder();

    @Test
    public void archivesIgnoreInputTimestampsAndReuseConfigurationCache() throws Exception {
        Path root = project.getRoot().toPath();
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        Files.writeString(root.resolve("build.gradle"), """
            plugins {
                id 'java'
                id 'textmate.base'
            }
            tasks.named('jar') {
                from('fixtures')
            }
            """);
        Path resource = root.resolve("fixtures/message.txt");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "same contents\n");
        Files.setLastModifiedTime(resource, FileTime.fromMillis(1_600_000_000_000L));

        runner().build();
        Path archive = root.resolve("build/libs/fixture.jar");
        byte[] first = Files.readAllBytes(archive);

        Files.setLastModifiedTime(resource, FileTime.fromMillis(1_700_000_000_000L));
        String output = runner().build().getOutput();

        assertTrue(output, output.contains("Reusing configuration cache."));
        assertArrayEquals("Changes to input timestamps must not change the archive bytes.",
                first, Files.readAllBytes(archive));
    }

    private GradleRunner runner() {
        return GradleRunner.create()
                .withProjectDir(project.getRoot())
                .withPluginClasspath()
                .withArguments("jar", "--rerun-tasks", "--no-build-cache", "--configuration-cache", "--stacktrace");
    }
}
