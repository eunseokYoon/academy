package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.util.MonthWeeks;
import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.dto.lesson.LessonBulkCreateRequest;
import com.njwenglish.dto.lesson.LessonBulkCreateResponse;
import com.njwenglish.dto.lesson.LessonCreateRequest;
import com.njwenglish.dto.lesson.LessonDetailResponse;
import com.njwenglish.dto.lesson.LessonListItemResponse;
import com.njwenglish.dto.lesson.LessonUpdateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.LessonRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-4 수업 관리. 수업일을 먼저 만들고 내용을 나중에 채우는 순서다.
 *
 * <p>공개는 publish()로만 이루어진다. PATCH는 published_at을 건드리지 않는다 —
 * 작성 중인 초안이 학부모에게 보이면 곤란하다.
 */
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final ClassRoomRepository classRoomRepository;

    @Transactional(readOnly = true)
    public List<LessonListItemResponse> list(Long classRoomId, LocalDate from, LocalDate to,
                                             Short year, Short month, Short week) {
        return lessonRepository.search(classRoomId, from, to, year, month, week).stream()
            .map(LessonListItemResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public LessonDetailResponse detail(Long lessonId) {
        return LessonDetailResponse.from(findLesson(lessonId));
    }

    @Transactional
    public LessonDetailResponse create(LessonCreateRequest request) {
        ClassRoom classRoom = findClassRoom(request.classRoomId());
        requireNewDate(classRoom.getId(), request.lessonDate());

        Lesson lesson = lessonRepository.save(Lesson.create(classRoom, request.lessonDate(),
            request.year(), validMonth(request.month()), validWeek(request.week())));
        lesson.writeContent(request.title(), validVideoUrl(request.videoUrl()),
            request.content(), request.keyPoints(), request.nextPreview());

        return LessonDetailResponse.from(lesson);
    }

    /**
     * 반의 요일 기준으로 기간 안의 수업일을 만든다. 내용은 비어 있고 PENDING·미공개다.
     * 주차는 서버가 달력 기준으로 채우고, 선생님이 이후 화면에서 고칠 수 있다.
     */
    @Transactional
    public LessonBulkCreateResponse bulkCreate(LessonBulkCreateRequest request) {
        ClassRoom classRoom = findClassRoom(request.classRoomId());
        // 슬롯의 요일 집합. 요일당 슬롯이 하나라 중복은 없지만 contains로 쓰려고 Set으로 모은다
        Set<DayOfWeek> days = classRoom.getSchedules().stream()
            .map(schedule -> DayOfWeek.of(schedule.getDayOfWeek()))
            .collect(Collectors.toSet());
        if (days.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.to().isBefore(request.from())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        Set<LocalDate> skip = new HashSet<>(
            request.skipDates() == null ? List.of() : request.skipDates());
        skip.addAll(lessonRepository.findLessonDates(
            classRoom.getId(), request.from(), request.to()));

        List<LocalDate> created = new ArrayList<>();
        int skipped = 0;
        for (LocalDate date = request.from();
             !date.isAfter(request.to());
             date = date.plusDays(1)) {
            if (!days.contains(date.getDayOfWeek())) {
                continue;
            }
            if (skip.contains(date)) {
                skipped++;
                continue;
            }
            lessonRepository.save(Lesson.create(classRoom, date,
                (short) date.getYear(), (short) date.getMonthValue(), MonthWeeks.of(date)));
            created.add(date);
        }
        return new LessonBulkCreateResponse(created.size(), skipped, created);
    }

    @Transactional
    public LessonDetailResponse update(Long lessonId, LessonUpdateRequest request) {
        Lesson lesson = findLesson(lessonId);

        if (request.year() != null || request.month() != null || request.week() != null) {
            lesson.changeWeek(
                request.year() != null ? request.year() : lesson.getYear(),
                request.month() != null ? validMonth(request.month()) : lesson.getMonth(),
                request.week() != null ? validWeek(request.week()) : lesson.getWeek());
        }
        lesson.writeContent(
            request.title() != null ? request.title() : lesson.getTitle(),
            request.videoUrl() != null
                ? validVideoUrl(request.videoUrl()) : lesson.getVideoUrl(),
            request.content() != null ? request.content() : lesson.getContent(),
            request.keyPoints() != null ? request.keyPoints() : lesson.getKeyPoints(),
            request.nextPreview() != null ? request.nextPreview() : lesson.getNextPreview());

        return LessonDetailResponse.from(lesson);
    }

    /** 공개하면 학생·학부모 조회에 잡힌다. 이미 공개된 수업을 다시 호출해도 시각은 그대로다. */
    @Transactional
    public LessonDetailResponse publish(Long lessonId) {
        Lesson lesson = findLesson(lessonId);
        lesson.publish(OffsetDateTime.now());
        return LessonDetailResponse.from(lesson);
    }

    @Transactional
    public void delete(Long lessonId) {
        Lesson lesson = findLesson(lessonId);
        if (lessonRepository.hasRecords(lessonId)) {
            throw new BusinessException(ErrorCode.LESSON_HAS_RECORDS);
        }
        lessonRepository.delete(lesson);
    }

    // ---------- 내부 ----------

    private Lesson findLesson(Long lessonId) {
        return lessonRepository.findWithClassRoom(lessonId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ClassRoom findClassRoom(Long classRoomId) {
        return classRoomRepository.findById(classRoomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** lessons에 UNIQUE (class_room_id, lesson_date)가 있다. */
    private void requireNewDate(Long classRoomId, LocalDate lessonDate) {
        if (lessonRepository.existsByClassRoomIdAndLessonDate(classRoomId, lessonDate)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private String validVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }
        if (YoutubeUrls.videoId(videoUrl) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return videoUrl.trim();
    }

    private short validMonth(Short month) {
        if (month == null || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return month;
    }

    /** DB CHECK가 1~5다. 6주차를 보내면 부팅이 아니라 저장 시점에 터지므로 여기서 막는다. */
    private short validWeek(Short week) {
        if (week == null || week < 1 || week > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return week;
    }
}
