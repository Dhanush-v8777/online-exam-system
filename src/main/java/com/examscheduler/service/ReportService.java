package com.examscheduler.service;

import com.examscheduler.entity.*;
import com.examscheduler.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.examscheduler.enums.ExamStatus;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ExaminationRepository examinationRepository;
    private final FacultyRepository facultyRepository;
    private final InvigilationRepository invigilationRepository;
    private final SeatingAllocationRepository seatingAllocationRepository;
    private final ExamRoomRepository examRoomRepository;

    public ReportService(ExaminationRepository examinationRepository,
                         FacultyRepository facultyRepository,
                         InvigilationRepository invigilationRepository,
                         SeatingAllocationRepository seatingAllocationRepository,
                         ExamRoomRepository examRoomRepository) {
        this.examinationRepository = examinationRepository;
        this.facultyRepository = facultyRepository;
        this.invigilationRepository = invigilationRepository;
        this.seatingAllocationRepository = seatingAllocationRepository;
        this.examRoomRepository = examRoomRepository;
    }

    /**
     * Generate Excel Examination Timetable Report using Apache POI
     */
    public byte[] generateTimetableExcel() throws IOException {
        List<Examination> exams = examinationRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Examination Timetable");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Title Row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("SmartExam - Official Examination Timetable Report");
            sheet.createRow(1); // blank

            // Headers
            String[] headers = {"ID", "Examination Name", "Subject Code", "Department", "Semester",
                                "Date", "Start Time", "Duration (Mins)", "Room", "Status"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            int rowIdx = 3;
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            for (Examination exam : exams) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(exam.getId());
                row.createCell(1).setCellValue(exam.getExamName());
                row.createCell(2).setCellValue(exam.getSubject() != null ? exam.getSubject().getSubjectCode() : "-");
                row.createCell(3).setCellValue(exam.getDepartment() != null ? exam.getDepartment().getDeptCode() : "-");
                row.createCell(4).setCellValue(exam.getSemester());
                row.createCell(5).setCellValue(exam.getExamDate() != null ? exam.getExamDate().format(dateFormatter) : "-");
                row.createCell(6).setCellValue(exam.getStartTime() != null ? exam.getStartTime().toString() : "-");
                row.createCell(7).setCellValue(exam.getDurationMinutes());
                row.createCell(8).setCellValue(exam.getExamRoom() != null ? exam.getExamRoom().getRoomNumber() : "Unassigned");
                row.createCell(9).setCellValue(exam.getStatus().name());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Generate Excel Faculty Workload & Invigilation Report
     */
    public byte[] generateFacultyWorkloadExcel() throws IOException {
        List<Faculty> facultyList = facultyRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Faculty Workload");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"Faculty ID", "Full Name", "Department", "Designation",
                                "Max Duties", "Assigned Duties", "Remaining Duties", "Availability"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Faculty f : facultyList) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(f.getFacultyId());
                row.createCell(1).setCellValue(f.getFullName());
                row.createCell(2).setCellValue(f.getDepartment() != null ? f.getDepartment().getDeptCode() : "-");
                row.createCell(3).setCellValue(f.getDesignation() != null ? f.getDesignation() : "-");
                row.createCell(4).setCellValue(f.getMaxDuties());
                row.createCell(5).setCellValue(f.getCurrentDutyCount());
                row.createCell(6).setCellValue(f.getRemainingDuties());
                row.createCell(7).setCellValue(f.isAvailable() ? "Available" : "Unavailable");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Generate PDF Examination Timetable Report using Apache PDFBox
     */
    public byte[] generateTimetablePdf() throws IOException {
        List<Examination> exams = examinationRepository.findAll();

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Header Title
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                contentStream.newLineAtOffset(50, 780);
                contentStream.showText("SmartExam - Official Examination Timetable");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                contentStream.newLineAtOffset(50, 762);
                contentStream.showText("Generated on: " + LocalDate.now() + " | SmartExam University Management");
                contentStream.endText();

                // Draw table header line
                contentStream.setLineWidth(1f);
                contentStream.moveTo(50, 750);
                contentStream.lineTo(550, 750);
                contentStream.stroke();

                // Column Headers
                float y = 735;
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
                contentStream.newLineAtOffset(50, y);
                contentStream.showText("Date");
                contentStream.newLineAtOffset(75, 0);
                contentStream.showText("Time");
                contentStream.newLineAtOffset(65, 0);
                contentStream.showText("Subject / Exam Name");
                contentStream.newLineAtOffset(200, 0);
                contentStream.showText("Room");
                contentStream.newLineAtOffset(65, 0);
                contentStream.showText("Dept");
                contentStream.endText();

                contentStream.moveTo(50, y - 5);
                contentStream.lineTo(550, y - 5);
                contentStream.stroke();

                y -= 20;
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);

                for (Examination exam : exams) {
                    if (y < 60) break; // keep within page bounds for simplicity

                    contentStream.beginText();
                    contentStream.newLineAtOffset(50, y);
                    contentStream.showText(exam.getExamDate() != null ? exam.getExamDate().toString() : "-");

                    contentStream.newLineAtOffset(75, 0);
                    contentStream.showText(exam.getStartTime() != null ? exam.getStartTime().toString() : "-");

                    contentStream.newLineAtOffset(65, 0);
                    String name = exam.getExamName();
                    if (name.length() > 32) name = name.substring(0, 30) + "..";
                    contentStream.showText(name);

                    contentStream.newLineAtOffset(200, 0);
                    contentStream.showText(exam.getExamRoom() != null ? exam.getExamRoom().getRoomNumber() : "TBD");

                    contentStream.newLineAtOffset(65, 0);
                    contentStream.showText(exam.getDepartment() != null ? exam.getDepartment().getDeptCode() : "-");
                    contentStream.endText();

                    y -= 16;
                }
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Room Utilization Report Data:
     * Room occupancy, assigned examinations, student allocations, and booked dates.
     */
    public List<Map<String, Object>> getRoomUtilizationReport() {
        List<ExamRoom> rooms = examRoomRepository.findAll();
        List<Examination> allExams = examinationRepository.findAll();
        List<SeatingAllocation> allAllocations = seatingAllocationRepository.findAll();

        List<Map<String, Object>> report = new ArrayList<>();
        for (ExamRoom r : rooms) {
            Map<String, Object> map = new HashMap<>();
            map.put("room", r);
            map.put("roomId", r.getId());
            map.put("roomNumber", r.getRoomNumber());
            map.put("building", r.getBuilding());
            map.put("capacity", r.getCapacity());

            long examCount = allExams.stream()
                .filter(e -> e.getStatus() != ExamStatus.CANCELLED &&
                    ((e.getExamRoom() != null && e.getExamRoom().getId().equals(r.getId()))
                    || (e.getSeatingAllocations() != null && e.getSeatingAllocations().stream().anyMatch(sa -> sa.getExamRoom() != null && sa.getExamRoom().getId().equals(r.getId())))))
                .count();
            map.put("totalExams", examCount);

            long seatedStudents = allAllocations.stream()
                .filter(sa -> sa.getExamRoom() != null && sa.getExamRoom().getId().equals(r.getId()))
                .count();
            map.put("totalStudentsSeated", seatedStudents);

            List<LocalDate> bookedDates = allExams.stream()
                .filter(e -> e.getStatus() != ExamStatus.CANCELLED &&
                    ((e.getExamRoom() != null && e.getExamRoom().getId().equals(r.getId()))
                    || (e.getSeatingAllocations() != null && e.getSeatingAllocations().stream().anyMatch(sa -> sa.getExamRoom() != null && sa.getExamRoom().getId().equals(r.getId())))))
                .map(Examination::getExamDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            map.put("bookedDates", bookedDates);

            double utilization = Math.min(100.0, (examCount * 25.0));
            map.put("utilizationScore", (int) utilization);

            report.add(map);
        }
        return report;
    }

    /**
     * Faculty Workload Report Data:
     * Duty quotas, assigned invigilations, remaining quota, and workload percentage.
     */
    public List<Map<String, Object>> getFacultyWorkloadReport() {
        List<Faculty> facultyList = facultyRepository.findAll();
        List<Invigilation> allInv = invigilationRepository.findAll();

        List<Map<String, Object>> report = new ArrayList<>();
        for (Faculty f : facultyList) {
            Map<String, Object> map = new HashMap<>();
            map.put("faculty", f);
            map.put("facultyId", f.getFacultyId());
            map.put("fullName", f.getFullName());
            map.put("department", f.getDepartment() != null ? f.getDepartment().getDeptCode() : "N/A");
            map.put("designation", f.getDesignation() != null ? f.getDesignation() : "Faculty");
            map.put("currentDuties", f.getCurrentDutyCount());
            map.put("maxDuties", f.getMaxDuties());
            int remaining = Math.max(0, f.getMaxDuties() - f.getCurrentDutyCount());
            map.put("remainingDuties", remaining);
            int percent = f.getMaxDuties() > 0 ? (f.getCurrentDutyCount() * 100 / f.getMaxDuties()) : 0;
            map.put("workloadPercentage", percent);

            List<Invigilation> duties = allInv.stream()
                .filter(inv -> inv.getFaculty() != null && inv.getFaculty().getId().equals(f.getId()))
                .collect(Collectors.toList());
            map.put("invigilations", duties);

            report.add(map);
        }
        return report;
    }
}
