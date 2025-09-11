package test;

import db.DatabaseHelper;
import logic.ProductionStatsCalculator;
import logic.WorkingTimeCalculator;
import ui.StatistikaPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PredPlanManager - hardened version with:
 * - entry/exit logs,
 * - debounce + cooldown to avoid infinite re-scheduling,
 * - TableModelListener guard (checks suppress flag, cooldown, debounce running and computing flag),
 * - when signature unchanged, pending debounce timer is stopped/cleared to avoid immediate restarts.
 *
 * Paste this file as ui/PredPlanManager.java (adjust column indices/constants if needed).
 */
public class PredPlanManager {
    private final AtomicBoolean computingPredPlans = new AtomicBoolean(false);
    private String lastComputeSignature = "";
    private long lastComputeMillis = 0L;
    private javax.swing.Timer debounceTimer;
    private volatile boolean suppressTableModelEvents = false;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final StatistikaPanel statistikaPanel;

    // constants - adjust indices if your model uses different positions
    private static final double DEFAULT_M2_PER_HOUR = 10.0;
    private static final int STATUS_COL_MODEL = 6; // status column index
    private static final int PRED_PLAN_COL = 15;   // predPlanIsporuke column index
    private static final LocalTime PLAN_WORK_START = LocalTime.of(7, 0);
    private static final LocalTime PLAN_WORK_END = LocalTime.of(15, 0);
    private static final int WORK_HOURS_PER_DAY = 8;

    // debounce/cooldown tuning
    private static final int DEBOUNCE_MS = 300;
    private static final int COOLDOWN_MS = 800;

    public PredPlanManager(DefaultTableModel model, JTable table, StatistikaPanel statistikaPanel) {
        this.tableModel = model;
        this.table = table;
        this.statistikaPanel = statistikaPanel;

        // Attach a safe table model listener that respects suppressTableModelEvents and cooldown/debounce
        this.tableModel.addTableModelListener(e -> {
            // Ignore programmatic updates while computing
            if (suppressTableModelEvents) {
                System.out.println("tableModelListener: suppressed event while suppressTableModelEvents==true");
                return;
            }
            long now = System.currentTimeMillis();
            // If a compute finished very recently, skip scheduling to avoid immediate retrigger loops
            if (now - lastComputeMillis < COOLDOWN_MS) {
                System.out.println("tableModelListener: skipped schedule due to cooldown (" + (now - lastComputeMillis) + "ms)");
                return;
            }
            // If a debounced timer is already pending, don't reschedule (it will restart)
            if (debounceTimer != null && debounceTimer.isRunning()) {
                System.out.println("tableModelListener: debounce already running, not scheduling new one");
                return;
            }
            // If a compute currently executing, skip scheduling (it will schedule at end if needed)
            if (computingPredPlans.get()) {
                System.out.println("tableModelListener: compute already in progress, not scheduling");
                return;
            }
            // otherwise schedule non-forced compute
            scheduleDebouncedCompute();
        });
    }

    // Debounced scheduling with cooldown guard
    public void scheduleDebouncedCompute() {
        long now = System.currentTimeMillis();
        if (now - lastComputeMillis < COOLDOWN_MS) {
            System.out.println("scheduleDebouncedCompute: skipped due to cooldown (" + (now - lastComputeMillis) + "ms)");
            return;
        }

        if (debounceTimer == null) {
            debounceTimer = new javax.swing.Timer(DEBOUNCE_MS, e -> {
                debounceTimer.stop();
                computePredPlansBatch(false);
            });
            debounceTimer.setRepeats(false);
        }
        if (debounceTimer.isRunning()) debounceTimer.restart(); else debounceTimer.start();
        System.out.println("scheduleDebouncedCompute: scheduled non-forced compute");
    }

    public void computeNow() {
        // user-initiated immediate compute (forced)
        if (debounceTimer != null && debounceTimer.isRunning()) debounceTimer.stop();
        computePredPlansBatch(true);
    }

    // Helper parsing stubs - adapt to your actual utilities if needed
    private double parseDoubleSafe(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s.replace(',', '.')); } catch (Exception ex) { return def; }
    }

    private java.time.LocalDateTime tryParseLocalDateTime(String s) {
        try {
            return logic.DateUtils.parse(s);
        } catch (Exception e) {
            try { return java.time.LocalDateTime.parse(s); } catch (Exception ex) { return null; }
        }
    }

    // ------------------------- computePredPlansBatch (with entry/exit logs + improvements) -------------------------
    public void computePredPlansBatch(boolean force) {
        // ENTRY logging + short stacktrace
        System.out.println(">>> computePredPlansBatch ENTRY: force=" + force
                + ", thread=" + Thread.currentThread().getName()
                + ", computingPredPlans=" + computingPredPlans.get()
                + ", lastSig=" + (lastComputeSignature == null ? "<null>" : lastComputeSignature)
                + ", time=" + LocalDateTime.now());
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(st.length, 10); i++) {
            System.out.println("    at " + st[i].toString());
        }

        // Prevent overlapping runs
        if (!computingPredPlans.compareAndSet(false, true)) {
            // if already running, schedule a debounced rerun
            scheduleDebouncedCompute();
            System.out.println("computePredPlansBatch: another run in progress -> scheduled debounced rerun");
            return;
        }

        try {
            suppressTableModelEvents = true; // prevent listeners reacting to programmatic changes
            final java.time.format.DateTimeFormatter outFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

            int rowCount = tableModel.getRowCount();

            // 1) Build list of items to schedule
            class Item { int modelRow; java.time.LocalDate orderDate; double m2; String status; }
            java.util.ArrayList<Item> items = new java.util.ArrayList<>();

            double totalRemainingAll = 0.0;
            for (int r = 0; r < rowCount; r++) {
                Object statusObj = tableModel.getValueAt(r, STATUS_COL_MODEL);
                String status = statusObj == null ? "" : statusObj.toString().trim();

                Object m2obj = tableModel.getValueAt(r, 11); // kolona m2 (provjeri indeks)
                double m2 = 0.0;
                if (m2obj instanceof Number) m2 = ((Number) m2obj).doubleValue();
                else if (m2obj instanceof String) m2 = parseDoubleSafe((String) m2obj, 0.0);

                // Clear previous pred plan for finished or empty rows
                if ("Izrađeno".equalsIgnoreCase(status) || m2 <= 0.0) {
                    try { tableModel.setValueAt("", r, PRED_PLAN_COL); } catch (Exception ignored) {}
                    continue;
                }

                // parse datumNarudzbe if present (column 0 assumed)
                java.time.LocalDate ord = null;
                Object od = tableModel.getValueAt(r, 0);
                if (od instanceof String && !((String) od).isBlank()) {
                    java.time.LocalDateTime parsed = tryParseLocalDateTime(od.toString());
                    if (parsed != null) ord = parsed.toLocalDate();
                }

                Item it = new Item();
                it.modelRow = r;
                it.orderDate = ord == null ? java.time.LocalDate.MAX : ord;
                it.m2 = m2;
                it.status = status;
                items.add(it);
                totalRemainingAll += m2;
            }

            // --- Deterministic selection of avgDailyFromStats / globalDailyM2 ---
            double avgDailyFromStats = 0.0;
            double totalRemainingFromStats = 0.0;

            try {
                if (statistikaPanel != null) {
                    avgDailyFromStats = statistikaPanel.getAvgDailyM2();
                    totalRemainingFromStats = statistikaPanel.getTotalRemainingM2();
                }
            } catch (Exception ignored) {}

            if (avgDailyFromStats <= 1e-6 || totalRemainingFromStats <= 0.0) {
                try {
                    Map<String, Object> stats = ProductionStatsCalculator.calculate((DefaultTableModel) tableModel, DEFAULT_M2_PER_HOUR);
                    if (stats != null) {
                        if (avgDailyFromStats <= 1e-6) {
                            Object a = stats.get(ProductionStatsCalculator.PROSJEK_M2_PO_DANU);
                            if (a != null) avgDailyFromStats = parseDoubleSafe(a.toString(), 0.0);
                        }
                        if (totalRemainingFromStats <= 0.0) {
                            Object rObj = stats.get(ProductionStatsCalculator.M2_ZAI);
                            if (rObj != null) totalRemainingFromStats = parseDoubleSafe(rObj.toString(), 0.0);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            double globalDailyM2 = avgDailyFromStats;
            String avgSource = (avgDailyFromStats > 1e-6) ? "statPanel/statsCalc" : "db/fallback";
            System.out.println("computePredPlansBatch: avg source=" + avgSource + ", avgDaily=" + avgDailyFromStats);
            if (globalDailyM2 <= 1e-6) {
                try {
                    globalDailyM2 = DatabaseHelper.getAverageDailyM2(30);
                } catch (Exception ex) {
                    globalDailyM2 = 0.0;
                }
                System.out.println("computePredPlansBatch: DB fallback avgDaily=" + globalDailyM2);
            }

            long workHours = java.time.temporal.ChronoUnit.HOURS.between(PLAN_WORK_START, PLAN_WORK_END);
            if (workHours <= 0) workHours = WORK_HOURS_PER_DAY;
            long minutesPerWorkDay = java.time.temporal.ChronoUnit.MINUTES.between(PLAN_WORK_START, PLAN_WORK_END);
            if (minutesPerWorkDay <= 0) minutesPerWorkDay = workHours * 60;
            double localFallback = DEFAULT_M2_PER_HOUR * (double) workHours;
            if (globalDailyM2 <= 1e-6) globalDailyM2 = localFallback;

            if (totalRemainingFromStats > 0.0) {
                totalRemainingAll = totalRemainingFromStats;
            }

            // ----------------- signature (memoization) -----------------
            String sig = String.format(java.util.Locale.ROOT, "tot=%.2f|g=%.3f|n=%d",
                    Math.round(totalRemainingAll * 100.0) / 100.0,
                    Math.round(globalDailyM2 * 1000.0) / 1000.0,
                    items.size()
            );
            String signature = Integer.toString(sig.hashCode());

            if (!force) {
                if (signature.equals(lastComputeSignature)) {
                    System.out.println("computePredPlansBatch: signature unchanged, skipping compute.");
                    // Set short cooldown so debounced scheduling doesn't immediately re-trigger a new run
                    lastComputeMillis = System.currentTimeMillis();
                    // stop and clear any pending debounce timer to avoid immediate restarts
                    try {
                        if (debounceTimer != null && debounceTimer.isRunning()) {
                            debounceTimer.stop();
                        }
                        debounceTimer = null;
                    } catch (Exception ignored) {}
                    // Ensure suppress flag is cleared and computing flag released
                    suppressTableModelEvents = false;
                    computingPredPlans.set(false);
                    return;
                }
            }
            // record the new signature so subsequent automatic triggers will be skipped
            lastComputeSignature = signature;
            lastComputeMillis = System.currentTimeMillis();
            // -------------------------------------------------------------------------------

            double m2PerMinuteGlobal = globalDailyM2 / (double) minutesPerWorkDay;
            final double STATIC_PER_ITEM_DAILY_CAP = 2800.0;
            final double PER_ITEM_DAILY_CAP_FINAL = Math.min(STATIC_PER_ITEM_DAILY_CAP, globalDailyM2);

            System.out.println("computePredPlansBatch(aggr): totalRemainingAll=" + totalRemainingAll
                    + ", globalDailyM2=" + globalDailyM2
                    + ", minutesPerWorkDay=" + minutesPerWorkDay
                    + ", m2PerMinute=" + m2PerMinuteGlobal
                    + ", perItemCap=" + PER_ITEM_DAILY_CAP_FINAL);

            // 4) Cursor start: if earliest orderDate is in future, start at that date; else start today at PLAN_WORK_START
            java.time.LocalDate candidateStartDate = java.time.LocalDate.now();
            java.util.Optional<java.time.LocalDate> earliestOpt = items.stream()
                    .map(i -> i.orderDate)
                    .filter(d -> !d.equals(java.time.LocalDate.MAX))
                    .min(java.time.LocalDate::compareTo);
            if (earliestOpt.isPresent()) {
                java.time.LocalDate earliest = earliestOpt.get();
                if (earliest.isAfter(candidateStartDate)) candidateStartDate = earliest;
            }

            // Ensure start is a working day
            java.time.LocalDate startDay = candidateStartDate;
            while (WorkingTimeCalculator.isHolidayOrWeekend(startDay)) startDay = startDay.plusDays(1);
            java.time.LocalDateTime cursor = java.time.LocalDateTime.of(startDay, PLAN_WORK_START);

            // remainingDayCapacity initializes for first day based on time left in day
            double remainingDayCapacity;
            java.time.LocalDateTime dayStart = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_START);
            java.time.LocalDateTime dayEnd = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_END);
            long remainingMinutesToday = java.time.Duration.between(cursor, dayEnd).toMinutes();
            if (remainingMinutesToday < 0) remainingMinutesToday = 0;
            double possibleM2RemainingByTime = remainingMinutesToday * m2PerMinuteGlobal;
            remainingDayCapacity = Math.min(globalDailyM2, Math.max(0.0, possibleM2RemainingByTime));

            if (cursor.toLocalTime().equals(PLAN_WORK_START)) remainingDayCapacity = globalDailyM2;

            // 5) Serial allocation: iterate items, consume day-by-day capacity
            for (Item it : items) {
                int r = it.modelRow;
                double remainingM2 = it.m2;
                boolean finished = false;

                if (!it.orderDate.equals(java.time.LocalDate.MAX)) {
                    java.time.LocalDateTime candidate = java.time.LocalDateTime.of(it.orderDate, PLAN_WORK_START);
                    if (cursor.isBefore(candidate)) {
                        cursor = candidate;
                        while (WorkingTimeCalculator.isHolidayOrWeekend(cursor.toLocalDate())) {
                            cursor = java.time.LocalDateTime.of(cursor.toLocalDate().plusDays(1), PLAN_WORK_START);
                        }
                        dayStart = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_START);
                        dayEnd = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_END);
                        long remMin = java.time.Duration.between(cursor, dayEnd).toMinutes();
                        if (remMin < 0) remMin = 0;
                        remainingDayCapacity = Math.min(globalDailyM2, remMin * m2PerMinuteGlobal);
                        if (cursor.toLocalTime().equals(PLAN_WORK_START)) remainingDayCapacity = globalDailyM2;
                    }
                }

                if (cursor.toLocalTime().isBefore(PLAN_WORK_START)) cursor = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_START);
                if (!cursor.toLocalTime().isBefore(PLAN_WORK_END)) {
                    java.time.LocalDate next = cursor.toLocalDate().plusDays(1);
                    while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                    cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                    remainingDayCapacity = globalDailyM2;
                }
                while (WorkingTimeCalculator.isHolidayOrWeekend(cursor.toLocalDate())) {
                    java.time.LocalDate next = cursor.toLocalDate().plusDays(1);
                    while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                    cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                    remainingDayCapacity = globalDailyM2;
                }

                while (remainingM2 > 1e-9) {
                    java.time.LocalDate currentDate = cursor.toLocalDate();
                    if (WorkingTimeCalculator.isHolidayOrWeekend(currentDate)) {
                        java.time.LocalDate next = currentDate.plusDays(1);
                        while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                        cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                        remainingDayCapacity = globalDailyM2;
                        continue;
                    }

                    java.time.LocalDateTime segStart = cursor.isAfter(java.time.LocalDateTime.of(currentDate, PLAN_WORK_START)) ? cursor : java.time.LocalDateTime.of(currentDate, PLAN_WORK_START);
                    java.time.LocalDateTime segEnd = java.time.LocalDateTime.of(currentDate, PLAN_WORK_END);
                    long availMinutes = segEnd.isAfter(segStart) ? java.time.Duration.between(segStart, segEnd).toMinutes() : 0;
                    if (availMinutes <= 0) {
                        java.time.LocalDate next = currentDate.plusDays(1);
                        while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                        cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                        remainingDayCapacity = globalDailyM2;
                        continue;
                    }

                    double availM2ByTime = availMinutes * m2PerMinuteGlobal;
                    double perDayCapForThisRow = Math.min(PER_ITEM_DAILY_CAP_FINAL, minutesPerWorkDay * m2PerMinuteGlobal);
                    double availM2ThisDay = Math.min(availM2ByTime, perDayCapForThisRow);
                    availM2ThisDay = Math.min(availM2ThisDay, remainingDayCapacity);

                    if (availM2ThisDay <= 1e-9) {
                        java.time.LocalDate next = currentDate.plusDays(1);
                        while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                        cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                        remainingDayCapacity = globalDailyM2;
                        continue;
                    }

                    if (availM2ThisDay >= remainingM2 - 1e-6) {
                        long minutesNeeded = m2PerMinuteGlobal > 0
                                ? (long) Math.ceil(remainingM2 / m2PerMinuteGlobal)
                                : availMinutes;
                        if (minutesNeeded > availMinutes) minutesNeeded = availMinutes;
                        java.time.LocalDateTime finishTime = segStart.plusMinutes(minutesNeeded);
                        String outDate = finishTime.toLocalDate().format(outFmt);
                        try { tableModel.setValueAt(outDate, r, PRED_PLAN_COL); } catch (Exception ignored) {}
                        double consumed = Math.min(remainingM2, minutesNeeded * m2PerMinuteGlobal);
                        remainingDayCapacity = Math.max(0.0, remainingDayCapacity - consumed);
                        cursor = finishTime;
                        finished = true;
                        break;
                    } else {
                        long minutesConsumed = m2PerMinuteGlobal > 0
                                ? (long) Math.ceil(availM2ThisDay / m2PerMinuteGlobal)
                                : availMinutes;
                        if (minutesConsumed > availMinutes) minutesConsumed = availMinutes;
                        double actuallyConsumed = Math.min(availM2ThisDay, minutesConsumed * m2PerMinuteGlobal);
                        remainingM2 = Math.max(0.0, remainingM2 - actuallyConsumed);
                        remainingDayCapacity = Math.max(0.0, remainingDayCapacity - actuallyConsumed);

                        java.time.LocalDate next = currentDate.plusDays(1);
                        while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                        cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                        remainingDayCapacity = globalDailyM2;
                    }
                }

                if (!finished) {
                    try { tableModel.setValueAt("", r, PRED_PLAN_COL); } catch (Exception ignored) {}
                }

                if (!cursor.toLocalTime().isBefore(PLAN_WORK_END)) {
                    java.time.LocalDate next = cursor.toLocalDate().plusDays(1);
                    while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                    cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                    remainingDayCapacity = globalDailyM2;
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (tableModel instanceof javax.swing.table.AbstractTableModel) {
                ((javax.swing.table.AbstractTableModel) tableModel).fireTableDataChanged();
            } else {
                table.repaint();
            }
            // mark finish time so debounce/cooldown can block immediate re-scheduling
            lastComputeMillis = System.currentTimeMillis();
            suppressTableModelEvents = false;
            computingPredPlans.set(false);

            // EXIT logging
            System.out.println("<<< computePredPlansBatch EXIT: thread=" + Thread.currentThread().getName()
                    + ", lastSig=" + (lastComputeSignature == null ? "<null>" : lastComputeSignature)
                    + ", time=" + LocalDateTime.now());
        }
    }
}