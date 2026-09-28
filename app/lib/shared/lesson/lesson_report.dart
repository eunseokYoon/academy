import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/homework_labels.dart';

import '../widgets/app_badge.dart';

/// 수업 상세에 딸린 숙제의 **결과**. 백엔드 `LessonReportResponse.Homework` 에서
/// `description` 을 뺀 것이다.
///
/// 학생 상세(S-5)와 학부모 주간 레포트(P-6)가 같이 쓴다. 숙제 **내용**
/// (`description`)은 학생 모델만 따로 받는다 — 학부모에게는 가지 않는다(4-6·4-8).
/// 학부모 쪽이 이 클래스에 필드를 더하게 두지 마라.
class LessonHomework {
  const LessonHomework({
    required this.homeworkId,
    required this.title,
    required this.kind,
    required this.dueAt,
    required this.result,
    required this.completionRate,
    required this.resolvedByResubmission,
    required this.submissionStatus,
  });

  final int homeworkId;
  final String title;
  final String kind;

  /// GRID 는 재제출을 열기 전까지 null 이다. 그리면 1970년이 뜬다.
  final String? dueAt;

  /// 오프라인 채점 축. GRID 는 **이것으로** 그린다(4-2).
  final String? result;
  final int? completionRate;
  final bool resolvedByResubmission;

  /// 온라인 제출 축. 제출 행이 없으면 null 이다.
  final String? submissionStatus;

  bool get isGrid => kind == 'GRID';

  /// 한 일로 세는가. GRID 는 ⭕만, ONLINE 은 제출했으면. 주간 레포트의 「숙제」 칸.
  bool get done => isGrid
      ? result == 'DONE'
      : submissionStatus != null && submissionStatus != 'NOT_SUBMITTED';

  factory LessonHomework.fromJson(Map<String, dynamic> json) => LessonHomework(
    homeworkId: (json['homeworkId'] as num).toInt(),
    title: json['title'] as String,
    kind: json['kind'] as String,
    dueAt: json['dueAt'] as String?,
    result: json['result'] as String?,
    completionRate: (json['completionRate'] as num?)?.toInt(),
    resolvedByResubmission: json['resolvedByResubmission'] as bool? ?? false,
    submissionStatus: json['submissionStatus'] as String?,
  );
}

/// 결과 배지의 글자와 색. **GRID 는 채점축으로 그린다** — `submissionStatus` 로
/// 그리면 ⭕를 받은 학생이 「미제출」로 뜬다(⭕는 온라인 제출을 안 해서 영원히
/// NOT_SUBMITTED 다). 학부모 캘린더를 새빨갛게 만들었던 바로 그 자리다.
(String, BadgeTone) lessonHomeworkBadge(LessonHomework h) {
  if (h.isGrid) {
    return (
      gradeLabel(h.result, h.completionRate, h.resolvedByResubmission),
      gradeTone(h.result),
    );
  }
  final status = h.submissionStatus;
  if (status == null) return ('미제출', BadgeTone.warn);
  return (
    submissionLabel(status),
    status == 'NOT_SUBMITTED' ? BadgeTone.warn : BadgeTone.ok,
  );
}

/// 수업 카드의 네 칸(V24). 학생·학부모 둘 다 본다.
class LessonNotes {
  const LessonNotes({
    required this.content,
    required this.keyPoints,
    required this.homeworkNote,
    required this.clinicNote,
  });

  final String? content;
  final String? keyPoints;
  final String? homeworkNote;
  final String? clinicNote;

  bool get isEmpty =>
      content == null &&
      keyPoints == null &&
      homeworkNote == null &&
      clinicNote == null;

  factory LessonNotes.fromJson(Map<String, dynamic> json) => LessonNotes(
    content: json['content'] as String?,
    keyPoints: json['keyPoints'] as String?,
    homeworkNote: json['homeworkNote'] as String?,
    clinicNote: json['clinicNote'] as String?,
  );
}

/// 학부모의 수업 상세(주간 레포트용). **영상도 숙제 내용도 없다** — 서버가
/// `forParent` 에서 비워 보내고, 이 모델은 그 칸을 아예 받지 않는다.
class ParentLessonDetail {
  const ParentLessonDetail({
    required this.lessonId,
    required this.lessonDate,
    required this.title,
    required this.notes,
    required this.homework,
    required this.attendance,
  });

  final int lessonId;
  final String lessonDate;
  final String? title;
  final LessonNotes notes;
  final LessonHomework? homework;

  /// null 이면 아직 확정 전이다. 결석이 아니다.
  final DayStatus? attendance;

  factory ParentLessonDetail.fromJson(Map<String, dynamic> json) =>
      ParentLessonDetail(
        lessonId: (json['lessonId'] as num).toInt(),
        lessonDate: json['lessonDate'] as String,
        title: json['title'] as String?,
        notes: LessonNotes.fromJson(json),
        homework: json['homework'] == null
            ? null
            : LessonHomework.fromJson(json['homework'] as Map<String, dynamic>),
        attendance: json['attendanceStatus'] == null
            ? null
            : DayStatus.parse(json['attendanceStatus'] as String),
      );
}
