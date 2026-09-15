package com.njwenglish.dto.clinic;

import java.time.LocalDate;
import java.util.List;

/** skipped에는 skipDates와 이미 OPEN이던 날짜가 함께 들어간다. */
public record ClinicBulkCreateResponse(int created, int skipped, List<LocalDate> createdDates) {
}
