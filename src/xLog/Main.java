package xLog;


import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.file.*;

public class Main {
  // --- DB ----------------------------------------------------------------
  private static Connection conn = null;
  private static final double XP_MAX = 109500.0;

  // ANSI styles
  private static final String BOLD   = "\033[1m";
  private static final String ITALIC = "\033[3m";
  private static final String RESET  = "\033[0m";
  private static final String DIM    = "\033[90m";

  // Rank labels and colors
  private static final String[] RANK_NAMES = {
    "Rookie","Explorer","Crafter","Strategist",
    "Expert","Architect","Elite","Master","Legend"
  };
  private static final String[] COLORS = {
    "\033[97m","\033[90m","\033[93m","\033[91m",
    "\033[92m","\033[94m","\033[95m","\033[31m","\033[30m"
  };

  // Badge size
  private static final int BADGE_H = 11;
  private static final int BADGE_W = BADGE_H * 2;

  // -------------------- utilities ----------------------------------------
  private static void ensureDbDir(Path dbPath) throws Exception {
    Path dir = dbPath.getParent();
    if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);
  }

  private static void clearScreen() {
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }

  private static String nowStr() {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE); // yyyy-MM-dd
  }

  private static int getInt(String q) throws SQLException {
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(q)) {
      return rs.next() ? rs.getInt(1) : -1;
    }
  }

  private static double getDouble(String q) throws SQLException {
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(q)) {
      return rs.next() ? rs.getDouble(1) : 0.0;
    }
  }

  private static int getDomainIdByName(String name) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM domains WHERE name = ?")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt("id") : -1;
      }
    }
  }

  private static int getElementIdByName(String name) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM elements WHERE name = ?")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt("id") : -1;
      }
    }
  }

  private static int getTaskIdByName(String name) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM tasks WHERE name = ?")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt("id") : -1;
      }
    }
  }

  private static String getTaskLastDone(int tid) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT last_done FROM tasks WHERE id = ?")) {
      ps.setInt(1, tid);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          String s = rs.getString(1);
          return s == null ? "" : s;
        }
      }
    }
    return "";
  }

  private static List<Double> fetchDomainXPs() throws SQLException {
    List<Double> v = new ArrayList<>(Arrays.asList(0.0,0.0,0.0,0.0));
    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM domains ORDER BY id LIMIT 4");
         ResultSet rs = ps.executeQuery()) {
      int i = 0;
      while (rs.next() && i < 4) {
        int did = rs.getInt(1);
        double sum = getDouble("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = " + did);
        v.set(i++, sum);
      }
    }
    return v;
  }

  private static void insertXpLog(List<Double> dx, double px) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
      "INSERT INTO xp_log(date,profile_xp,domain1_xp,domain2_xp,domain3_xp,domain4_xp) VALUES(?,?,?,?,?,?)")) {
      ps.setString(1, nowStr());
      ps.setDouble(2, px);
      for (int i = 0; i < 4; ++i) ps.setDouble(3 + i, dx.get(i));
      ps.executeUpdate();
    }
  }

  // -------------------- DB init ------------------------------------------
  private static void initDB() throws SQLException {
    try (Statement st = conn.createStatement()) {
      st.execute("PRAGMA foreign_keys = ON;");
      st.execute(
        "CREATE TABLE IF NOT EXISTS user (" +
        " id INTEGER PRIMARY KEY CHECK(id=1)," +
        " name TEXT NOT NULL," +
        " created_at TEXT NOT NULL" +
        ");"
      );
      st.execute(
        "CREATE TABLE IF NOT EXISTS domains (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " name TEXT NOT NULL UNIQUE" +
        ");"
      );
      st.execute(
        "CREATE TABLE IF NOT EXISTS elements (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " domain_id INTEGER NOT NULL REFERENCES domains(id) ON DELETE CASCADE," +
        " name TEXT NOT NULL," +
        " is_focus INTEGER NOT NULL DEFAULT 0," +
        " xp INTEGER NOT NULL DEFAULT 0," +
        " UNIQUE(domain_id, name)" +
        ");"
      );
      st.execute(
        "CREATE TABLE IF NOT EXISTS tasks (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " name TEXT NOT NULL UNIQUE," +
        " type TEXT NOT NULL," +
        " frequency INTEGER NOT NULL," +
        " major_elem INTEGER NOT NULL REFERENCES elements(id)," +
        " minor_elem INTEGER NOT NULL REFERENCES elements(id)," +
        " last_done TEXT," +
        " streak INTEGER NOT NULL DEFAULT 0," +
        " active INTEGER NOT NULL DEFAULT 1" +
        ");"
      );
      st.execute(
        "CREATE TABLE IF NOT EXISTS xp_log (" +
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " date TEXT NOT NULL UNIQUE," +
        " profile_xp REAL NOT NULL," +
        " domain1_xp REAL NOT NULL," +
        " domain2_xp REAL NOT NULL," +
        " domain3_xp REAL NOT NULL," +
        " domain4_xp REAL NOT NULL" +
        ");"
      );
    }
  }

  // -------------------- intro --------------------------------------------
  private static void typePrint(String txt, int delayMs) {
    for (char c : txt.toCharArray()) {
      System.out.print(c);
      try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
    }
    System.out.println();
  }

  private static void showIntro(String name) {
    String[] lines = {
      "System: \"Your Soul Chronicle is at 7%. Game Over.\"",
      name + ": \"No... this can't be the end!\"",
      "A blaze of white light tears through the battlefield.",
      "You tumble into a silent chamber before the Aevum Engine.",
      "The lever glows \"-4 Years\".",
      name + ": \"I will grow strong. I will learn. I will find allies who stand beside me.\"",
      name + ": \"And when I face him again... I will win.\"",
      "Your journey begins anew..."
    };
    Scanner sc = new Scanner(System.in);
    int i = 0;
    for (String line : lines) {
      typePrint(line, 30);
      try { Thread.sleep(100); } catch (InterruptedException ignored) {}
      i++; if (i == 2) sc.nextLine();
    }
    sc.nextLine();
  }

  // -------------------- initial setup -----------------------------------
  private static void promptInitialSetup() throws SQLException {
    clearScreen();
    Scanner sc = new Scanner(System.in);
    System.out.print("Enter your name: ");
    String uname = sc.nextLine();

    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO user(id,name,created_at) VALUES(1,?,?)")) {
      ps.setString(1, uname);
      ps.setString(2, nowStr());
      ps.executeUpdate();
    }

    showIntro(uname);
    clearScreen();
    System.out.println("-- Create 4 domains & elements --");
    for (int d = 0; d < 4; ++d) {
      System.out.print("Domain #" + (d + 1) + " name: ");
      String dn = sc.nextLine();
      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO domains(name) VALUES(?)")) {
        ps.setString(1, dn);
        ps.executeUpdate();
      }
      int did = getDomainIdByName(dn);
      for (int e = 0; e < 4; ++e) {
        System.out.print("  Element #" + (e + 1) + " for '" + dn + "': ");
        String en = sc.nextLine();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO elements(domain_id,name) VALUES(?,?)")) {
          ps.setInt(1, did);
          ps.setString(2, en);
          ps.executeUpdate();
        }
      }
    }

    int didElem = getElementIdByName("Discipline");
    if (didElem > 0) {
      try (PreparedStatement ps = conn.prepareStatement(
        "INSERT OR IGNORE INTO tasks(name,type,frequency,major_elem,minor_elem) VALUES('daily_login','quick',1,?,?)")) {
        ps.setInt(1, didElem);
        ps.setInt(2, didElem);
        ps.executeUpdate();
      }
    }
  }

  // -------------------- task ops ----------------------------------------
  private static void addTask() throws SQLException {
    clearScreen();
    System.out.println("-- Add Task --");
    Scanner sc = new Scanner(System.in);
    System.out.print("Task name: "); String name = sc.nextLine();
    System.out.print("Type (quick/session/grind): "); String type = sc.nextLine();
    System.out.print("Frequency (days, 0=one-time): "); int freq = Integer.parseInt(sc.nextLine());
    System.out.print("Major element name: "); String maj = sc.nextLine();
    System.out.print("Minor element name: "); String min = sc.nextLine();
    int mi = getElementIdByName(maj), mn = getElementIdByName(min);
    if (mi < 0 || mn < 0) { System.out.println("Element not found."); new Scanner(System.in).nextLine(); return; }
    try (PreparedStatement ps = conn.prepareStatement(
      "INSERT INTO tasks(name,type,frequency,major_elem,minor_elem) VALUES(?,?,?,?,?)")) {
      ps.setString(1, name);
      ps.setString(2, type);
      ps.setInt(3, freq);
      ps.setInt(4, mi);
      ps.setInt(5, mn);
      ps.executeUpdate();
    }
  }

  private static void deleteTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) { System.out.println("Not found."); new Scanner(System.in).nextLine(); return; }
    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
      ps.setInt(1, tid);
      ps.executeUpdate();
    }
    System.out.println("Deleted."); new Scanner(System.in).nextLine();
  }

  private static void viewTodaysTasks() throws SQLException {
    clearScreen();
    System.out.println("-- Today's Tasks --");
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT name,type FROM tasks WHERE active=1 AND (last_done IS NULL OR (frequency>0 AND date('now','localtime')>=date(last_done,'+'||frequency||' days')))"
    ); ResultSet rs = ps.executeQuery()) {
      while (rs.next()) System.out.println("- " + rs.getString(1) + " (" + rs.getString(2) + ")");
    }
    new Scanner(System.in).nextLine();
  }

  private static void completeTaskById(int tid) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT type,major_elem,minor_elem,streak,frequency,last_done FROM tasks WHERE id = ?")) {
      ps.setInt(1, tid);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return;
        String type = rs.getString(1);
        int maj = rs.getInt(2), minr = rs.getInt(3);
        int streak = rs.getInt(4), freq = rs.getInt(5);
        String last = rs.getString(6);
        if (last == null) last = "";

        int base_maj = 0, base_min = 0;
        if ("quick".equals(type)) { base_maj = 10; base_min = 5; }
        else if ("session".equals(type)) { base_maj = 60; base_min = 30; }
        else if ("grind".equals(type)) { base_maj = 125; base_min = 75; }

        boolean focus = false;
        try (PreparedStatement p2 = conn.prepareStatement("SELECT is_focus FROM elements WHERE id = ?")) {
          p2.setInt(1, maj);
          try (ResultSet r2 = p2.executeQuery()) {
            focus = r2.next() && r2.getInt(1) == 1;
          }
        }
        double maj_xp = base_maj, min_xp = base_min;
        if (focus) { maj_xp *= 1.1; min_xp *= 1.1; }

        int pct = Math.min(streak, 20);
        maj_xp *= (1 + pct / 100.0);
        min_xp *= (1 + pct / 100.0);

        if (!last.isEmpty() && freq > 0) {
          LocalDate lastDate = LocalDate.parse(last);
          LocalDate due = lastDate.plusDays(freq);
          if (LocalDate.now().isAfter(due)) { maj_xp *= 0.6; min_xp *= 0.6; }
        }

        int imaj = (int)Math.round(maj_xp), imin = (int)Math.round(min_xp);
        try (PreparedStatement up1 = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
          up1.setInt(1, imaj); up1.setInt(2, maj); up1.executeUpdate();
        }
        try (PreparedStatement up2 = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
          up2.setInt(1, imin); up2.setInt(2, minr); up2.executeUpdate();
        }
      }
    }

    try (PreparedStatement ps2 = conn.prepareStatement("UPDATE tasks SET last_done = ?, streak = streak + 1 WHERE id = ?")) {
      ps2.setString(1, nowStr());
      ps2.setInt(2, tid);
      ps2.executeUpdate();
    }
  }

  private static void completeTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) { System.out.println("Not found."); new Scanner(System.in).nextLine(); return; }
    completeTaskById(tid);
    System.out.println("Task completed!"); // pause not strictly necessary
  }

  private static void grantBaseXp(String type, String majEle, String minEle) throws SQLException {
    int majId = getElementIdByName(majEle), minId = getElementIdByName(minEle);
    if (majId < 0 || minId < 0) { System.out.println("Element not found."); return; }
    int base_maj = 0, base_min = 0;
    if ("quick".equals(type)) { base_maj = 10; base_min = 5; }
    else if ("session".equals(type)) { base_maj = 60; base_min = 30; }
    else if ("grind".equals(type)) { base_maj = 125; base_min = 75; }

    boolean focus = false;
    try (PreparedStatement p = conn.prepareStatement("SELECT is_focus FROM elements WHERE id = ?")) {
      p.setInt(1, majId);
      try (ResultSet r = p.executeQuery()) { focus = r.next() && r.getInt(1) == 1; }
    }
    if (focus) { base_maj += base_maj / 10; base_min += base_min / 10; }

    try (PreparedStatement up = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
      up.setInt(1, base_maj); up.setInt(2, majId); up.executeUpdate();
    }
    try (PreparedStatement up = conn.prepareStatement("UPDATE elements SET xp = xp + ? WHERE id = ?")) {
      up.setInt(1, base_min); up.setInt(2, minId); up.executeUpdate();
    }
    System.out.println("XP granted."); new Scanner(System.in).nextLine();
  }

  private static void makeFocus(String elemName) throws SQLException {
    int eid = getElementIdByName(elemName);
    if (eid < 0) { System.out.println("Element not found."); new Scanner(System.in).nextLine(); return; }

    int domId = -1;
    try (PreparedStatement ps = conn.prepareStatement("SELECT domain_id FROM elements WHERE id = ?")) {
      ps.setInt(1, eid);
      try (ResultSet rs = ps.executeQuery()) { if (rs.next()) domId = rs.getInt(1); }
    }
    if (domId == -1) { System.out.println("Domain not found."); new Scanner(System.in).nextLine(); return; }

    try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 0 WHERE domain_id = ?")) {
      ps.setInt(1, domId); ps.executeUpdate();
    }
    try (PreparedStatement ps = conn.prepareStatement("UPDATE elements SET is_focus = 1 WHERE id = ?")) {
      ps.setInt(1, eid); ps.executeUpdate();
    }
    System.out.println("Focus updated successfully."); new Scanner(System.in).nextLine();
  }

  // -------------------- daily log ---------------------------------------
  private static void logTodayXp() throws SQLException {
    String today = nowStr();
    try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM xp_log WHERE date = ?")) {
      ps.setString(1, today);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return; // already logged
      }
    }

    int dlt = getTaskIdByName("daily_login");
    if (dlt >= 0) completeTaskById(dlt);

    List<Double> dx = fetchDomainXPs();
    double prod = 1.0;
    for (double x : dx) prod *= x;
    double px = Math.pow(prod, 1.0 / 4.0);
    insertXpLog(dx, px);
  }

  // -------------------- toggle tasks -----------------------------------
  private static void enableTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) System.out.println("Task not found.");
    else try (PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET active = 1 WHERE id = ?")) {
      ps.setInt(1, tid); ps.executeUpdate(); System.out.println("Task enabled.");
    }
    new Scanner(System.in).nextLine();
  }

  private static void disableTask(String name) throws SQLException {
    int tid = getTaskIdByName(name);
    if (tid < 0) System.out.println("Task not found.");
    else try (PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET active = 0 WHERE id = ?")) {
      ps.setInt(1, tid); ps.executeUpdate(); System.out.println("Task disabled.");
    }
    new Scanner(System.in).nextLine();
  }

  // -------------------- views -------------------------------------------
  private static String padRight(String s, int width) {
    if (s.length() >= width) return s.substring(0, width);
    return s + " ".repeat(width - s.length());
  }

  private static List<String> buildBadge(String text, String color) {
    List<String> b = new ArrayList<>();
    int innerW = BADGE_W - 2;
    int midH = BADGE_H / 2;
    for (int y = 0; y < BADGE_H; ++y) {
      if (y == 0 || y == BADGE_H - 1) b.add(color + "+" + "-".repeat(innerW) + "+" + RESET);
      else if (y == midH) {
        String t = text;
        if (t.length() > innerW) t = t.substring(0, innerW);
        int pad = innerW - t.length();
        int lp = pad / 2, rp = pad - lp;
        b.add(color + "|" + " ".repeat(lp) + t + " ".repeat(rp) + "|" + RESET);
      } else b.add(color + "|" + " ".repeat(innerW) + "|" + RESET);
    }
    return b;
  }

  private static String buildProgressBar(double frac, int len, String color) {
    int filled = (int)(frac * len + 0.5);
    String bar = "=".repeat(filled) + "-".repeat(len - filled);
    return color + "[" + bar + "] " + (int)(frac * 100) + "%" + RESET;
  }

  private static void viewProfile() throws SQLException {
    clearScreen();
    String[] domainNames = {"","","",""};
    double[] domainXps = new double[4];
    try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM domains ORDER BY id");
         ResultSet rs = ps.executeQuery()) {
      int idx = 0;
      while (rs.next() && idx < 4) {
        int id = rs.getInt(1);
        domainNames[idx] = rs.getString(2);
        try (PreparedStatement s2 = conn.prepareStatement("SELECT COALESCE(SUM(xp),0) FROM elements WHERE domain_id = ?")) {
          s2.setInt(1, id);
          try (ResultSet r2 = s2.executeQuery()) { if (r2.next()) domainXps[idx] = r2.getDouble(1); }
        }
        idx++;
      }
    }

    double prod = 1.0;
    for (double x : domainXps) prod *= x;
    double profileXp = Math.pow(prod, 1.0/4.0);
    double lvlF = Math.sqrt(profileXp / XP_MAX) * 8.0;
    int lvl = Math.min(8, Math.max(0, (int)lvlF));
    double frac = (lvl < 8 ? lvlF - lvl : 1.0);
    String color = COLORS[lvl];
    String rank = RANK_NAMES[lvl];

    String user = "";
    int daysLeft = 0;
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT name, CAST(julianday(date(created_at,'+4 years'))-julianday('now','localtime') AS INTEGER) FROM user WHERE id=1");
         ResultSet rs = ps.executeQuery()) {
      if (rs.next()) {
        user = rs.getString(1);
        daysLeft = rs.getInt(2);
      }
    }

    List<String> badge = buildBadge(rank, color);
    String[] info = new String[BADGE_H];
    Arrays.fill(info, "");
    info[0] = color + BOLD + rank + " " + ITALIC + user + RESET;
    info[1] = DIM + "-".repeat(31) + RESET;
    info[2] = "XP      : " + color + ((int)profileXp) + RESET;
    info[3] = "Time    : " + daysLeft;
    info[4] = BOLD + "Lvl:" + RESET + " " + buildProgressBar(frac, 20, color);
    for (int i = 0; i < 4; ++i) info[6 + i] = padRight(domainNames[i], 12) + " : " + color + ((int)domainXps[i]) + RESET;

    for (int y = 0; y < BADGE_H; ++y) {
      String left = badge.get(y);
      String right = info[y] == null ? "" : info[y];
      System.out.println(left + "  " + right);
    }
    new Scanner(System.in).nextLine();
  }

  private static void viewDomain(String choice) throws SQLException {
    List<int[]> domains = new ArrayList<>(); // [id, ignored], store names in map
    Map<Integer, String> nameMap = new HashMap<>();
    try (PreparedStatement ps = conn.prepareStatement("SELECT id,name FROM domains ORDER BY id");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        int id = rs.getInt(1); String nm = rs.getString(2);
        domains.add(new int[]{id});
        nameMap.put(id, nm);
      }
    }
    int did = -1;
    for (Map.Entry<Integer,String> e : nameMap.entrySet()) if (e.getValue().equals(choice)) did = e.getKey();
    if (did < 0) { System.out.println("Domain not found."); new Scanner(System.in).nextLine(); return; }

    String user = "";
    try (PreparedStatement p = conn.prepareStatement("SELECT name FROM user WHERE id=1");
         ResultSet r = p.executeQuery()) { if (r.next()) user = r.getString(1); }

    class Elem { String name; double xp; boolean focus; }
    List<Elem> elems = new ArrayList<>();
    double totalXP = 0;
    try (PreparedStatement ps = conn.prepareStatement("SELECT name,xp,is_focus FROM elements WHERE domain_id = ?")) {
      ps.setInt(1, did);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Elem e = new Elem();
          e.name = rs.getString(1); e.xp = rs.getDouble(2); e.focus = rs.getInt(3) == 1;
          elems.add(e); totalXP += e.xp;
        }
      }
    }
    elems.sort((a,b) -> Double.compare(b.xp, a.xp));
    double lvlF = Math.sqrt(totalXP / XP_MAX) * 8.0;
    int lvl = Math.min(8, Math.max(0, (int)lvlF));
    double frac = (lvl < 8 ? lvlF - lvl : 1.0);
    String color = COLORS[lvl];

    List<String> badge = buildBadge(choice, color);
    String[] info = new String[BADGE_H];
    Arrays.fill(info, "");
    info[0] = color + BOLD + ITALIC + user + RESET;
    info[1] = DIM + "-".repeat(31) + RESET;
    info[2] = color + BOLD + choice + " : " + ((int)totalXP) + RESET;
    info[4] = BOLD + "Lvl:" + RESET + " " + buildProgressBar(frac, 20, color);
    for (int i = 0; i < 4 && i < elems.size(); ++i) {
      String label = padRight(elems.get(i).name, 12);
      if (elems.get(i).focus) label = ITALIC + label + RESET;
      info[6 + i] = label + " : " + color + ((int)elems.get(i).xp) + RESET;
    }

    for (int y = 0; y < BADGE_H; ++y) System.out.println(badge.get(y) + "  " + (info[y] == null ? "" : info[y]));
    new Scanner(System.in).nextLine();
  }

  private static void viewAllTasks() throws SQLException {
    clearScreen();
    System.out.println("-- All Tasks --");
    try (PreparedStatement ps = conn.prepareStatement(
      "SELECT name, type, frequency, last_done, streak, active FROM tasks ORDER BY id");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        String name = rs.getString(1), type = rs.getString(2);
        int freq = rs.getInt(3); String ld = rs.getString(4);
        int streak = rs.getInt(5), active = rs.getInt(6);
        System.out.println("- " + name + " [" + type + "] freq=" + freq + " last_done=" + (ld == null ? "never" : ld)
          + " streak=" + streak + " " + (active==1 ? "ENABLED" : "DISABLED"));
      }
    }
    new Scanner(System.in).nextLine();
  }

  // -------------------- usage -------------------------------------------
  private static void usage() {
    System.out.println(
      "Usage: xlog <command> [args]\n" +
      "Commands:\n" +
      "  profile            (default if no args)\n" +
      "  today              Show today's tasks\n" +
      "  create             Add a new task\n" +
      "  delete <task_name>   Delete task by name\n" +
      "  list               List all tasks\n" +
      "  enable <task_name>   Enable a task\n" +
      "  pause  <task_name>   Disable (pause) a task\n" +
      "  done   <name> [<name>...] Mark tasks done\n" +
      "  quick|session|grind <ele1> <ele2>  Grant quick XP\n" +
      "  info   <domain>    Show domain dashboard\n" +
      "  focus  <element>   Set focus element\n"
    );
  }

  // -------------------- main --------------------------------------------
  public static void main(String[] args) {
    try {
      String home = System.getProperty("user.home");
      Path dbPath = Paths.get(home, "xLog", "xLog.db");
      ensureDbDir(dbPath);
      String url = "jdbc:sqlite:" + dbPath.toString();
      conn = DriverManager.getConnection(url);
      initDB();

      if (getInt("SELECT COUNT(*) FROM domains") == 0) promptInitialSetup();
      logTodayXp();

    } catch (Exception e) {
      e.printStackTrace();
      if (conn != null) try { conn.close(); } catch (Exception ignore) {}
    }
  }
}

