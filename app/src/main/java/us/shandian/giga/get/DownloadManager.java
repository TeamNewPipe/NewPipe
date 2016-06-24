package us.shandian.giga.get;

public interface DownloadManager
{
	int BLOCK_SIZE = 512 * 1024;
	
	int startMission(String url, String name, int threads);
	void resumeMission(int id);
	void pauseMission(int id);
	void deleteMission(int id);
	DownloadMission getMission(int id);
	int getCount();
	String getLocation();
}
