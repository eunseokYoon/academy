package com.njwenglish.service;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.regularexam.RegularExamGridResponse;
import com.njwenglish.dto.regularexam.RegularExamSaveRequest;
import com.njwenglish.entity.RegularExamScore;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.RegularExamScoreRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학교 내신·모의고사. <b>선생님만 본다.</b>
 *
 * <p>학생·학부모 API에 이 서비스를 물리지 마라. 원점수만 기록하고 등급·과목·시험명은 없다.
 * 데이터가 학생에 붙으므로 반을 옮겨도 점수는 남는다.
 */
@Service
@RequiredArgsConstructor
public class RegularExamService {

    private final RegularExamScoreRepository regularExamScoreRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentAccessGuard studentAccessGuard;

    /**
     * 명단은 주차 그리드와 같은 규칙이다 — 재원생 ∪ 그 해 점수가 있는 학생.
     *
     * <p>재원생을 먼저 넣어 이름순을 유지하고, 점수 보유자는 putIfAbsent로 뒤에 붙인다.
     * 재원생만 뽑으면 지난 연도를 열었을 때 퇴원생 점수가 표에서 사라진다.
     */
    @Transactional(readOnly = true)
    public RegularExamGridResponse grid(Long classRoomId, short year) {
        Map<Long, RegularExamGridResponse.StudentRow> rows = new LinkedHashMap<>();
        for (Student student : enrollmentRepository
            .findActiveStudents(classRoomId, LocalDate.now())) {
            rows.put(student.getId(), new RegularExamGridResponse.StudentRow(
                student.getId(), student.getName(), true));
        }
        for (Student student : regularExamScoreRepository
            .findStudentsWithScores(classRoomId, year)) {
            rows.putIfAbsent(student.getId(), new RegularExamGridResponse.StudentRow(
                student.getId(), student.getName(), false));
        }

        List<RegularExamGridResponse.ScoreItem> scores = rows.isEmpty()
            ? List.of()
            : regularExamScoreRepository
                .findByStudentIdInAndYear(List.copyOf(rows.keySet()), year).stream()
                .map(score -> new RegularExamGridResponse.ScoreItem(
                    score.getStudent().getId(), score.getExamSlot(), score.getRawScore()))
                .toList();

        return new RegularExamGridResponse(classRoomId, year, List.copyOf(rows.values()), scores);
    }

    /**
     * rawScore가 null이면 그 칸의 행을 삭제한다. 요청에 없는 칸은 건드리지 않는다.
     *
     * <p>같은 칸을 다시 저장하는 건 오타 수정이라는 정상 흐름이다. 409를 던지지 마라.
     */
    @Transactional
    public void save(RegularExamSaveRequest request) {
        for (RegularExamSaveRequest.Item item : request.scores()) {
            // studentId를 받는 모든 서비스 메서드의 첫 줄은 requireAccessible이다.
            // 삭제·덮어쓰기·신규 생성 세 경로 모두를 덮어야 한다
            Student student = studentAccessGuard.requireAccessible(item.studentId());

            Optional<RegularExamScore> found = regularExamScoreRepository
                .findByStudentIdAndYearAndExamSlot(item.studentId(), request.year(),
                    item.examSlot());

            if (item.rawScore() == null) {
                found.ifPresent(regularExamScoreRepository::delete);
                continue;
            }
            if (found.isPresent()) {
                found.get().changeScore(item.rawScore());
                continue;
            }
            regularExamScoreRepository.save(RegularExamScore.create(student, request.year(),
                item.examSlot(), item.rawScore()));
        }
    }
}
