package us.shandian.giga.get;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import us.shandian.giga.get.DownloadDataSource;
import us.shandian.giga.get.DownloadManagerImpl;
import us.shandian.giga.get.DownloadMission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link DownloadManagerImpl}
 *
 * TODO: test loading from .giga files, startMission and improve tests
 */
public class DownloadManagerImplTest {

    private DownloadManagerImpl downloadManager;
    private DownloadDataSource downloadDataSource;
    private ArrayList<DownloadMission> missions;

    @org.junit.Before
    public void setUp() throws Exception {
        downloadDataSource = mock(DownloadDataSource.class);
        missions = new ArrayList<>();
        for(int i = 0; i < 50; ++i){
            missions.add(generateFinishedDownloadMission());
        }
        when(downloadDataSource.loadMissions()).thenReturn(new ArrayList<>(missions));
        downloadManager = new DownloadManagerImpl(new ArrayList<>(), downloadDataSource);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullAsDownloadDataSource() {
        new DownloadManagerImpl(new ArrayList<>(), null);
    }


    private static DownloadMission generateFinishedDownloadMission() throws IOException {
        File file = File.createTempFile("newpipetest", ".mp4");
        file.deleteOnExit();
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.setLength(1000);
        randomAccessFile.close();
        DownloadMission downloadMission = new DownloadMission(file.getName(),
                "http://google.com/?q=how+to+google", file.getParent());
        downloadMission.blocks = 1000;
        downloadMission.done = 1000;
        downloadMission.finished = true;
        return spy(downloadMission);
    }

    private static void assertMissionEquals(String message, DownloadMission expected, DownloadMission actual) {
        if(expected == actual) return;
        assertEquals(message + ": Name", expected.name, actual.name);
        assertEquals(message + ": Location", expected.location, actual.location);
        assertEquals(message + ": Url", expected.url, actual.url);
    }

    @Test
    public void testThatMissionsAreLoaded() throws IOException {
        ArrayList<DownloadMission> missions = new ArrayList<>();
        long millis = System.currentTimeMillis();
        for(int i = 0; i < 50; ++i){
            DownloadMission  mission = generateFinishedDownloadMission();
            mission.timestamp = millis - i; // reverse order by timestamp
            missions.add(mission);
        }

        downloadDataSource = mock(DownloadDataSource.class);
        when(downloadDataSource.loadMissions()).thenReturn(new ArrayList<>(missions));
        downloadManager = new DownloadManagerImpl(new ArrayList<>(), downloadDataSource);
        verify(downloadDataSource, times(1)).loadMissions();

        assertEquals(50, downloadManager.getCount());

        for(int i = 0; i < 50; ++i) {
            assertMissionEquals("mission " + i, missions.get(50 - 1 - i), downloadManager.getMission(i));
        }
    }

    @Ignore
    @Test
    public void startMission() throws Exception {
        DownloadMission mission = missions.get(0);
        mission = spy(mission);
        missions.set(0, mission);
        String url = "https://github.com/favicon.ico";
        // create a temp file and delete it so we have a temp directory
        File tempFile = File.createTempFile("favicon",".ico");
        String name = tempFile.getName();
        String location = tempFile.getParent();
        assertTrue(tempFile.delete());
        int id = downloadManager.startMission(url, location, name, true, 10);
    }

    @Test
    public void resumeMission() {
        DownloadMission mission = missions.get(0);
        mission.running = true;
        verify(mission, never()).start();
        downloadManager.resumeMission(0);
        verify(mission, never()).start();
        mission.running = false;
        downloadManager.resumeMission(0);
        verify(mission, times(1)).start();
    }

    @Test
    public void pauseMission() {
        DownloadMission mission = missions.get(0);
        mission.running = false;
        downloadManager.pauseMission(0);
        verify(mission, never()).pause();
        mission.running = true;
        downloadManager.pauseMission(0);
        verify(mission, times(1)).pause();
    }

    @Test
    public void deleteMission() {
        DownloadMission mission = missions.get(0);
        assertEquals(mission, downloadManager.getMission(0));
        downloadManager.deleteMission(0);
        verify(mission, times(1)).delete();
        assertNotEquals(mission, downloadManager.getMission(0));
        assertEquals(49, downloadManager.getCount());
    }

    @Test(expected = RuntimeException.class)
    public void getMissionWithNegativeIndex() {
        downloadManager.getMission(-1);
    }

    @Test
    public void getMission() {
        assertSame(missions.get(0), downloadManager.getMission(0));
        assertSame(missions.get(1), downloadManager.getMission(1));
    }

    @Test
    public void sortByTimestamp() {
        ArrayList<DownloadMission> downloadMissions = new ArrayList<>();
        DownloadMission mission = new DownloadMission();
        mission.timestamp = 0;

        DownloadMission mission1 = new DownloadMission();
        mission1.timestamp = Integer.MAX_VALUE + 1L;

        DownloadMission mission2 = new DownloadMission();
        mission2.timestamp = 2L * Integer.MAX_VALUE ;

        DownloadMission mission3 = new DownloadMission();
        mission3.timestamp = 2L * Integer.MAX_VALUE + 5L;


        downloadMissions.add(mission3);
        downloadMissions.add(mission1);
        downloadMissions.add(mission2);
        downloadMissions.add(mission);


        DownloadManagerImpl.sortByTimestamp(downloadMissions);

        assertEquals(mission, downloadMissions.get(0));
        assertEquals(mission1, downloadMissions.get(1));
        assertEquals(mission2, downloadMissions.get(2));
        assertEquals(mission3, downloadMissions.get(3));
    }

}