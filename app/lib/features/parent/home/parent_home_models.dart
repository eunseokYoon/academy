import '../../student/home/student_home_models.dart';

export '../../student/home/student_home_models.dart'
    show NextExam, NextClinic, HomeNotices, NoticeItem;

/// 학부모 홈. 백엔드 `dto/home/ParentHomeResponse.java` 가 정본이다.
///
/// **홈은 요약이다.** 수업 제목·내용, 숙제 사진·피드백을 이 응답에 넣지 마라 —
/// 홈이 화면 전체를 대신하기 시작한다.
class ParentHome {
  const ParentHome({
    required this.studentName,
    required this.nextExam,
    required this.nextLessonDate,
    required this.nextLessonTime,
    required this.nextLessonDDay,
    required this.notices,
    required this.pendingHomeworkCount,
    required this.nextClinic,
    required this.thisMonthAttendance,
  });

  final String studentName;
  final NextExam? nextExam;

  final String? nextLessonDate;

  /// **반의 요일 슬롯에서 온다.** 슬롯이 없으면 null 이고 화면은 시각 없이
  /// 날짜만 그린다 — 시각을 지어내 채우지 마라.
  final String? nextLessonTime;

  /// **서버가 센다.** 앱이 `nextLessonDate` 로 계산하면 기기 시계에 따라
  /// 학생 화면과 하루 어긋난다.
  final int? nextLessonDDay;

  final HomeNotices notices;
  final int pendingHomeworkCount;
  final NextClinic? nextClinic;
  final AttendanceSummary thisMonthAttendance;

  factory ParentHome.fromJson(Map<String, dynamic> json) => ParentHome(
    studentName: (json['student'] as Map<String, dynamic>)['name'] as String,
    nextExam: json['nextExam'] == null
        ? null
        : NextExam.fromJson(json['nextExam'] as Map<String, dynamic>),
    nextLessonDate: json['nextLessonDate'] as String?,
    nextLessonTime: json['nextLessonTime'] as String?,
    nextLessonDDay: (json['nextLessonDDay'] as num?)?.toInt(),
    notices: HomeNotices.fromJson(json['notices'] as Map<String, dynamic>),
    pendingHomeworkCount: (json['pendingHomeworkCount'] as num?)?.toInt() ?? 0,
    nextClinic: json['nextClinic'] == null
        ? null
        : NextClinic.fromJson(json['nextClinic'] as Map<String, dynamic>),
    thisMonthAttendance: AttendanceSummary.fromJson(
      json['thisMonthAttendance'] as Map<String, dynamic>,
    ),
  );
}

/// 이번 달 출석 요약.
class AttendanceSummary {
  const AttendanceSummary({
    required this.present,
    required this.late,
    required this.absent,
    required this.sick,
    required this.excused,
    required this.makeup,
  });

  final int present;
  final int late;
  final int absent;
  final int sick;
  final int excused;
  final int makeup;

  /// **`MAKEUP`(대체 등원)은 출석이다.** 원래 요일에 못 와서 다른 날 수업에
  /// 들어온 경우다 — 결석 쪽으로 세지 마라(CLAUDE.md 5-1).
  int get attended => present + makeup;

  factory AttendanceSummary.fromJson(Map<String, dynamic> json) =>
      AttendanceSummary(
        present: (json['present'] as num?)?.toInt() ?? 0,
        late: (json['late'] as num?)?.toInt() ?? 0,
        absent: (json['absent'] as num?)?.toInt() ?? 0,
        sick: (json['sick'] as num?)?.toInt() ?? 0,
        excused: (json['excused'] as num?)?.toInt() ?? 0,
        makeup: (json['makeup'] as num?)?.toInt() ?? 0,
      );
}
