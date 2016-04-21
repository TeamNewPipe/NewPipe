package us.shandian.giga.get;

public interface DownloadManager
{
	public static final int BLOCK_SIZE = 512 * 1024;
	
	public int startMission(String url, String name, int threads);
	public void resumeMission(int id);
	public void pauseMission(int id);
	public void deleteMission(int id);
	public DownloadMission getMission(int id);
	public int getCount();
	public String getLocation();
}
