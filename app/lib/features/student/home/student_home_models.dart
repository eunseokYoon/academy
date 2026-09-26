/// 학생 홈 응답. 백엔드 `dto/home/StudentHomeResponse.java` 가 정본이다.
///
/// **날짜·시각은 서버 문자열을 그대로 담는다.** 파싱해서 다시 만들지 마라 —
/// 표시용 포맷은 화면에서 문자열을 자른다.
/// **`dDay` 를 계산하지 마라.** 서버가 KST 오늘 기준으로 센 값이고, 앱이
/// 기기 시계로 다시 세면 학생 화면과 학부모 화면이 하루 어긋난다.
class StudentHome {
  const StudentHome({
    required this.studentName,
    required this.nextLesson,
    required this.nextExam,
    required this.nextClinic,
    required this.currentHomeworks,
    required this.lastLesson,
    required this.notices,
  });

  final String studentName;

  /// null 이면 화면이 그 칸을 **뺀다.** 「미정」으로 채우지 마라.
  final NextLesson? nextLesson;
  final NextExam? nextExam;
  final NextClinic? nextClinic;

  final List<HomeHomework> currentHomeworks;
  final HomeLesson? lastLesson;
  final HomeNotices notices;

  factory StudentHome.fromJson(Map<String, dynamic> json) => StudentHome(
    studentName: (json['student'] as Map<String, dynamic>)['name'] as String,
    nextLesson: json['nextLesson'] == null
        ? null
        : NextLesson.fromJson(json['nextLesson'] as Map<String, dynamic>),
    nextExam: json['nextExam'] == null
        ? null
        : NextExam.fromJson(json['nextExam'] as Map<String, dynamic>),
    nextClinic: json['nextClinic'] == null
        ? null
        : NextClinic.fromJson(json['nextClinic'] as Map<String, dynamic>),
    currentHomeworks: ((json['currentHomeworks'] as List<dynamic>?) ?? const [])
        .map((e) => HomeHomework.fromJson(e as Map<String, dynamic>))
        .toList(),
    lastLesson: json['lastLesson'] == null
        ? null
        : HomeLesson.fromJson(json['lastLesson'] as Map<String, dynamic>),
    notices: HomeNotices.fromJson(json['notices'] as Map<String, dynamic>),
  );
}

class NextLesson {
  const NextLesson({
    required this.lessonDate,
    required this.startTime,
    required this.dDay,
    required this.classRoomName,
  });

  final String lessonDate;

  /// 반의 요일 슬롯에서 온다. 슬롯이 없으면 null 이고 화면은 **시각 없이
  /// 날짜만 그린다** — 시각을 지어내 채우지 마라.
  final String? startTime;

  final int dDay;
  final String classRoomName;

  factory NextLesson.fromJson(Map<String, dynamic> json) => NextLesson(
    lessonDate: json['lessonDate'] as String,
    startTime: json['startTime'] as String?,
    dDay: json['dDay'] as int,
    classRoomName: json['classRoomName'] as String,
  );
}

class NextExam {
  const NextExam({
    required this.examType,
    required this.startDate,
    required this.scopeNote,
    required this.dDay,
  });

  /// 백엔드 `ExamType` enum 의 이름이다. 라벨은 화면이 웹의
  /// `frontend/src/shared/score/types` 의 `EXAM_TYPE_LABELS` 와 맞춘다.
  final String examType;

  final String startDate;
  final String? scopeNote;
  final int dDay;

  factory NextExam.fromJson(Map<String, dynamic> json) => NextExam(
    examType: json['examType'] as String,
    startDate: json['startDate'] as String,
    scopeNote: json['scopeNote'] as String?,
    dDay: json['dDay'] as int,
  );
}

class NextClinic {
  const NextClinic({
    required this.clinicId,
    required this.clinicDate,
    required this.arrivalTime,
    required this.dDay,
  });

  final int clinicId;
  final String clinicDate;

  /// **시간대 시작이 아니라 이 학생의 도착 시각이다.** 클리닉은 17:00~22:00
  /// 처럼 다섯 시간짜리이고 학생은 그 안 한 시간에 배정된다.
  final String arrivalTime;

  final int dDay;

  factory NextClinic.fromJson(Map<String, dynamic> json) => NextClinic(
    clinicId: json['clinicId'] as int,
    clinicDate: json['clinicDate'] as String,
    arrivalTime: json['arrivalTime'] as String,
    dDay: json['dDay'] as int,
  );
}

class HomeHomework {
  const HomeHomework({
    required this.homeworkId,
    required this.title,
    required this.dueAt,
    required this.status,
    required this.remainingMinutes,
  });

  final int homeworkId;
  final String title;
  final String? dueAt;
  final String status;
  final int remainingMinutes;

  factory HomeHomework.fromJson(Map<String, dynamic> json) => HomeHomework(
    homeworkId: json['homeworkId'] as int,
    title: json['title'] as String,
    dueAt: json['dueAt'] as String?,
    status: json['status'] as String,
    remainingMinutes: (json['remainingMinutes'] as num).toInt(),
  );
}

class HomeLesson {
  const HomeLesson({
    required this.lessonId,
    required this.lessonDate,
    required this.title,
    required this.videoId,
    required this.embedUrl,
    required this.videoCount,
    required this.content,
    required this.homeworkNote,
  });

  final int lessonId;
  final String lessonDate;
  final String? title;
  final String? videoId;
  final String? embedUrl;
  final int videoCount;
  final String? content;
  final String? homeworkNote;

  factory HomeLesson.fromJson(Map<String, dynamic> json) => HomeLesson(
    lessonId: json['lessonId'] as int,
    lessonDate: json['lessonDate'] as String,
    title: json['title'] as String?,
    videoId: json['videoId'] as String?,
    embedUrl: json['embedUrl'] as String?,
    videoCount: (json['videoCount'] as num?)?.toInt() ?? 0,
    content: json['content'] as String?,
    homeworkNote: json['homeworkNote'] as String?,
  );
}

class HomeNotices {
  const HomeNotices({required this.totalCount, required this.recent});

  /// 읽음 표시는 범위 밖이라 **전체 건수**다.
  final int totalCount;
  final List<NoticeItem> recent;

  factory HomeNotices.fromJson(Map<String, dynamic> json) => HomeNotices(
    totalCount: (json['totalCount'] as num).toInt(),
    recent: ((json['recent'] as List<dynamic>?) ?? const [])
        .map((e) => NoticeItem.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

class NoticeItem {
  const NoticeItem({
    required this.noticeId,
    required this.title,
    required this.pinned,
    required this.hasAttachment,
    required this.publishedAt,
  });

  final int noticeId;
  final String title;
  final bool pinned;
  final bool hasAttachment;

  /// ISO 8601 문자열 그대로. 표시용 「09/15」는 화면이 자른다.
  final String publishedAt;

  factory NoticeItem.fromJson(Map<String, dynamic> json) => NoticeItem(
    noticeId: json['noticeId'] as int,
    title: json['title'] as String,
    pinned: json['pinned'] as bool? ?? false,
    hasAttachment: json['hasAttachment'] as bool? ?? false,
    publishedAt: json['publishedAt'] as String,
  );
}
