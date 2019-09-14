package net;

import java.net.URLEncoder;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 获取山东大学教室的空闲情况
 * 
 * @author chendukuan
 */
public class StudyRoom {

	private static final String FREE = "空闲";
	private static final String CLASSROOM = "classroom";
	private static final String BUILDING = "building";
	private static final String STATUS = "status";

	private static final String CAMPUS = "兴隆山校区";
	private static final String BUILDING_A = "兴隆山群楼A座";
	private static final String BUILDING_B = "兴隆山群楼B座";
	private static final String BUILDING_C = "兴隆山群楼C座";
	private static final String BUILDING_D = "兴隆山群楼D座";
	private static final String BUILDING_E = "兴隆山群楼E座";
	private static final String BUILDING_LECTURE = "兴隆山讲学堂";

	private static final Integer[] COURSE_NUM = { 1, 2, 3, 4, 5, 6 };

	public static void main(String[] args) {
		try {
			SslUtils.ignoreSsl();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// the year minus 1900;the month minus 1
		final Date mondayDate = new Date(2019 - 1900, 9 - 1, 16);
		HashMap<String, ClassRoom> map = getAllFreeClassRoomWeekly(mondayDate);
		Set<Entry<String, ClassRoom>> set = map.entrySet();
		List<Entry<String, ClassRoom>> list = new ArrayList<>(set);
		Collections.sort(list, new FreeComparator());

		System.out.println("一共查询了" + list.size() + "个教室");
		System.out.println("教学楼/教室/空闲时间/上课时间");
		for (int i = 0; i < list.size(); i++) {
			ClassRoom room = list.get(i).getValue();
			if (room.freeTimes < 40) {
				continue;
			}
			System.out.print(room.building + "/" + room.roomName + "/" + room.freeTimes);
			if (room.freeTimes < 42) {
				for (CourseTime course : room.courseTimes) {
					System.out.print("/" + course.day + ":" + course.time);
				}

			}
			System.out.println();
		}
	}

	private static HashMap<String, ClassRoom> getAllFreeClassRoomWeekly(final Date mondayDate) {
		HashMap<String, ClassRoom> result = new HashMap<>();
		result.putAll(getFreeClassRoomWeekly(CAMPUS, BUILDING_A, mondayDate));
		result.putAll(getFreeClassRoomWeekly(CAMPUS, BUILDING_B, mondayDate));
		result.putAll(getFreeClassRoomWeekly(CAMPUS, BUILDING_C, mondayDate));
		result.putAll(getFreeClassRoomWeekly(CAMPUS, BUILDING_D, mondayDate));
		result.putAll(getFreeClassRoomWeekly(CAMPUS, BUILDING_E, mondayDate));
		result.putAll(getFreeClassRoomWeekly(CAMPUS, BUILDING_LECTURE, mondayDate));
		return result;
	}

	private static HashMap<String, ClassRoom> getFreeClassRoomWeekly(String campus, String building,
			final Date mondayDate) {
		HashMap<String, ClassRoom> listWeekly = new HashMap<>();

		final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		for (int i = 0; i < 7; i++) {
			String date = sdf.format(new Date(mondayDate.getTime() + i * 24 * 60 * 60 * 1000));
			HashMap<String, ClassRoom> listDaily = getFreeClassDaily(i, request(campus, building, date));
			sumFreeTimes(listDaily, listWeekly);
		}
		return listWeekly;
	}

	private static void sumFreeTimes(HashMap<String, ClassRoom> listDaily, HashMap<String, ClassRoom> listWeekly) {
		if (listDaily == null || listDaily.isEmpty()) {
			return;
		}
		if (listWeekly == null) {
			return;
		}
		for (Entry<String, ClassRoom> entry : listDaily.entrySet()) {
			String roomName = entry.getKey();
			if (listWeekly.containsKey(roomName)) {
				listWeekly.get(roomName).freeTimes += entry.getValue().freeTimes;
				listWeekly.get(roomName).courseTimes.addAll(entry.getValue().courseTimes);
			} else {
				listWeekly.put(roomName, entry.getValue());
			}
		}
	}

	private static String request(String campus, String building, String date) {
		try {
			StringBuilder url = new StringBuilder("https://sduonline.cn/isdu/studyroom/api?");
			url.append("campus=").append(URLEncoder.encode(campus, "utf-8"));
			url.append("&building=").append(URLEncoder.encode(building, "utf-8"));
			url.append("&date=").append(date);
			return HttpClient.doGet(url.toString());
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @param day  day of week
	 * @param body the response
	 * @return
	 */
	private static HashMap<String, ClassRoom> getFreeClassDaily(int day, String body) {
		HashMap<String, ClassRoom> map = new HashMap<>();
		if (body == null || body.isEmpty()) {
			System.out.println("response is null");
			return map;
		}
		try {
			JSONArray array = new JSONArray(body);
			JSONObject classRoom;
			JSONObject status;
			String roomName, building;
			int freeTimes = 0;
			for (int i = 0; i < array.length(); i++) {
				freeTimes = 0;
				classRoom = array.getJSONObject(i);
				roomName = classRoom.optString(CLASSROOM);
				building = classRoom.optString(BUILDING);
				status = classRoom.optJSONObject(STATUS);
				ClassRoom room = new ClassRoom(building, roomName);
				if (status != null) {
					for (int j = 0; j < COURSE_NUM.length; j++) {
						if (FREE.equals(status.getString(COURSE_NUM[j] + ""))) {
							freeTimes++;
						} else {
							room.courseTimes.add(new CourseTime(day, COURSE_NUM[j]));
						}
					}
				}
				room.freeTimes = freeTimes;
				map.put(roomName, room);
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		return map;
	}

	/**
	 * The use of a classroom
	 */
	static class ClassRoom {
		public String building;
		public String roomName;
		public int freeTimes;
		public List<CourseTime> courseTimes = new ArrayList<>();

		public ClassRoom(String building, String roomName) {
			this.building = building;
			this.roomName = roomName;
		}
	}

	/**
	 * Course time information
	 */
	static class CourseTime {
		public String day;// day of week
		public String time;// the time of the lesson

		public CourseTime(int day, int time) {
			this.day = DAYS[day];
			this.time = TIMES[time];
		}

		private static final String[] DAYS = { "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日" };

		private static final String[] TIMES = { "第一节课", "第二节课", "第三节课", "第四节课", "第五节课", "第六节课" };
	}

	static class FreeComparator implements Comparator<Entry<String, ClassRoom>> {

		@Override
		public int compare(Entry<String, ClassRoom> o1, Entry<String, ClassRoom> o2) {
			return compare(o1.getValue().freeTimes, o2.getValue().freeTimes);
		}

		/**
		 * The big one is in front
		 */
		private int compare(int x, int y) {
			return (x < y) ? 1 : ((x == y) ? 0 : -1);
		}

	}

}
