package bms.player.beatoraja;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import bms.table.Course;
import bms.table.DifficultyTable;
import bms.table.DifficultyTableElement;
import bms.table.DifficultyTableParser;
import bms.table.Course.Trophy;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;

import bms.player.beatoraja.CourseData.TrophyData;

/**
 * 難易度表データアクセス用クラス
 * 
 * @author exch
 */
public class TableDataAccessor {
	
	private String tabledir = "table";

	public TableDataAccessor() {
		
	}

	public TableDataAccessor(String tabledir) {
		this.tabledir = tabledir;
	}

	public void updateTableData(String[] urls) {
		final ConcurrentLinkedDeque< Thread> tasks = new ConcurrentLinkedDeque<Thread>();
		for (final String url : urls) {
			Thread task = new Thread() {
				public void run() {
					DifficultyTableParser dtp = new DifficultyTableParser();
					DifficultyTable dt = new DifficultyTable();
					if (url.endsWith(".json")) {
						dt.setHeadURL(url);
					} else {
						dt.setSourceURL(url);
					}
					try {
						dtp.decode(true, dt);
						TableData td = new TableData();
						td.setUrl(url);
						td.setName(dt.getName());
						String[] levels = dt.getLevelDescription();
						List<TableData.TableDataELement> tdes = new ArrayList<>(levels.length);
						for (String lv : levels) {
							TableData.TableDataELement tde = new TableData.TableDataELement();
							tde.setLevel(lv);
							List<String> hashes = new ArrayList<String>();
							for (DifficultyTableElement dte : dt.getElements()) {
								if (lv.equals(dte.getDifficultyID())) {
									hashes.add(dte.getSHA256() != null ? dte.getSHA256() : dte.getMD5());
								}
							}
							tde.setHash(hashes.toArray(new String[hashes.size()]));
							tdes.add(tde);
						}
						td.setFolder(tdes.toArray(new TableData.TableDataELement[tdes.size()]));

						if (dt.getCourse() != null && dt.getCourse().length > 0) {
							List<CourseData> gname = new ArrayList<CourseData>();
							for (Course[] course : dt.getCourse()) {
								for (Course g : course) {
									CourseData cd = new CourseData();
									cd.setName(g.getName());
									cd.setHash(g.getHash());
									List<CourseData.CourseDataConstraint> l = new ArrayList<>();
									for(int i = 0;i < g.getConstraint().length;i++) {
										for (CourseData.CourseDataConstraint constraint : CourseData.CourseDataConstraint.values()) {
											if (constraint.name.equals(g.getConstraint()[i])) {
												l.add(constraint);
												break;
											}
										}
									}
									cd.setConstraint(l.toArray(new CourseData.CourseDataConstraint[l.size()]));
									if (g.getTrophy() != null) {
										List<TrophyData> tr = new ArrayList<TrophyData>();
										for (Trophy trophy : g.getTrophy()) {
											TrophyData t = new TrophyData();
											t.setName(trophy.getName());
											t.setMissrate((float) trophy.getMissrate());
											t.setScorerate((float) trophy.getScorerate());
											tr.add(t);
										}
										cd.setTrophy(tr.toArray(new TrophyData[tr.size()]));
									}
									gname.add(cd);
								}
							}

							td.setCourse(gname.toArray(new CourseData[gname.size()]));
						}
						write(td);
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}

			};
			tasks.add(task);
			task.start();
		}
		
		while(!tasks.isEmpty()) {
			if(!tasks.getFirst().isAlive()) {
				tasks.removeFirst();
			} else {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	/**
	 * 難易度表データをキャッシュする
	 * 
	 * @param td 難易度表データ
	 */
	public void write(TableData td) {
		try {
			Json json = new Json();
			json.setElementType(TableData.class, "folder", ArrayList.class);
			json.setElementType(TableData.TableDataELement.class, "hash", ArrayList.class);
			json.setElementType(TableData.class, "course", ArrayList.class);
			json.setElementType(CourseData.class, "trophy", ArrayList.class);
			json.setOutputType(OutputType.json);
			OutputStreamWriter fw = new OutputStreamWriter(new BufferedOutputStream(
					new GZIPOutputStream(new FileOutputStream(tabledir + "/" + td.getName() + ".bmt"))), "UTF-8");
			fw.write(json.prettyPrint(td));
			fw.flush();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 全てのキャッシュされた難易度表データを読み込む
	 * 
	 * @return 全てのキャッシュされた難易度表データ
	 */
	public TableData[] readAll() {
		List<TableData> result = new ArrayList<TableData>();
		try (DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get(tabledir))) {
			for (Path p : paths) {
				if (p.toString().endsWith(".bmt")) {
					try {
						Json json = new Json();
						TableData td = json.fromJson(TableData.class,
								new BufferedInputStream(new GZIPInputStream(Files.newInputStream(p))));
						result.add(td);
					} catch(Throwable e) {

					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result.toArray(new TableData[result.size()]);
	}

	/**
	 * 指定のキャッシュされた難易度表データを読み込む
	 * 
	 * @param name 難易度表名
	 * @return キャッシュされた難易度表データ。存在しない場合はnull
	 */
	public TableData read(String name) {
		TableData td = null;
		try (DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get(tabledir))) {
			for (Path p : paths) {
				if (p.getFileName().toString().equals(name + ".bmt")) {
					try {
						Json json = new Json();
						td = json.fromJson(TableData.class,
								new BufferedInputStream(new GZIPInputStream(Files.newInputStream(p))));
						break;
					} catch(Throwable e) {

					}
				}
			}
		} catch (IOException e) {

		}
		return td;
	}

}
