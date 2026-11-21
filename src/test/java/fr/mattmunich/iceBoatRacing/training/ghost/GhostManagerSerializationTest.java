package fr.mattmunich.iceBoatRacing.training.ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GhostManagerSerializationTest {

    @Test
    void testTrajectorySerialization(@TempDir Path tempDir) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        UUID playerUuid = UUID.randomUUID();
        String raceName = "Monza_GP";

        List<GhostPoint> points = List.of(
                new GhostPoint(10.5, 64.0, 20.0, 90.0f, 0.0f, 0L),
                new GhostPoint(25.0, 64.0, 35.5, 120.0f, 2.0f, 1500L),
                new GhostPoint(50.0, 64.0, 80.0, 180.0f, 0.0f, 3200L)
        );

        GhostTrajectory original = new GhostTrajectory(playerUuid, raceName, 3200L, points);

        File file = tempDir.resolve("ghost.json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(original, writer);
        }

        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        GhostTrajectory loaded;
        try (FileReader reader = new FileReader(file)) {
            loaded = gson.fromJson(reader, GhostTrajectory.class);
        }

        assertNotNull(loaded);
        assertEquals(playerUuid, loaded.getPlayerUuid());
        assertEquals(raceName, loaded.getRaceName());
        assertEquals(3200L, loaded.getLapTimeMs());
        assertEquals(3, loaded.getPoints().size());

        GhostPoint p0 = loaded.getPoints().get(0);
        assertEquals(10.5, p0.x(), 0.001);
        assertEquals(64.0, p0.y(), 0.001);
        assertEquals(20.0, p0.z(), 0.001);
        assertEquals(90.0f, p0.yaw(), 0.001f);
        assertEquals(0L, p0.elapsedMs());

        GhostPoint p2 = loaded.getPoints().get(2);
        assertEquals(50.0, p2.x(), 0.001);
        assertEquals(180.0f, p2.yaw(), 0.001f);
        assertEquals(3200L, p2.elapsedMs());
    }
}
