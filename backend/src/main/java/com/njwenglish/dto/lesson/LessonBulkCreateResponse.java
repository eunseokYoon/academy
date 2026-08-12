package com.njwenglish.dto.lesson;

import java.time.LocalDate;
import java.util.List;

/** 이미 있는 날짜는 건너뛴다. skipped에는 skipDates와 기존 수업일이 함께 들어간다. */
public record LessonBulkCreateResponse(int created, int skipped, List<LocalDate> createdDates) {
}
