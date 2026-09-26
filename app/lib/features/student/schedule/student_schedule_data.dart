import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/home_labels.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';

/// 클리닉 한 회. 백엔드 `StudentClinicResponse`. **다른 학생 이름은 없다.**
///
/// 이 응답은 열린 클리닉을 **전부** 내려준다 — 이동 후보라서다(CLAUDE.md
/// 「만들지 마라」의 학생 클리닉 신청 항목). 화면은 [myReservation] 이 있는
/// 것만 「내 클리닉」으로 그리고, 나머지는 시간 변경의 후보로만 쓴다.
class StudentClinic {
  const StudentClinic({
    required this.clinicId,
    required this.clinicDate,
    required this.startTime,
    required this.endTime,
    required this.slots,
    required this.full,
    required this.myReservation,
  });

  final int clinicId;
  final String clinicDate;
  final String startTime;
  final String endTime;

  /// 고를 수 있는 도착 시각. **서버가 계산한다**(`Clinic.slots()`, 10번) —
  /// 시작·종료로 여기서 다시 만들면 「마지막 슬롯은 종료 1시간 전」이 두 곳이 된다.
  final List<String> slots;

  /// 서버가 계산한다. 정원이 null 이면 언제나 false 다 — 앱에서
  /// `reservedCount >= capacity` 를 세지 마라.
  final bool full;

  /// 내 배정. **이게 있는 것만 「내 클리닉」이다.**
  final MyClinicReservation? myReservation;

  factory StudentClinic.fromJson(Map<String, dynamic> json) {
    final mine = json['myReservation'] as Map<String, dynamic>?;
    return StudentClinic(
      clinicId: (json['clinicId'] as num).toInt(),
      clinicDate: json['clinicDate'] as String,
      startTime: json['startTime'] as String,
      endTime: json['endTime'] as String,
      // 새 배열 필드는 `?? []` 로 받는다(7-3).
      slots: ((json['slots'] as List<dynamic>?) ?? const [])
          .map((e) => e as String)
          .toList(),
      full: json['full'] as bool? ?? false,
      myReservation: mine == null ? null : MyClinicReservation.fromJson(mine),
    );
  }
}

class MyClinicReservation {
  const MyClinicReservation({required this.arrivalTime});

  /// 학생이 기억해야 하는 건 「몇 시에 가는가」다 — 화면의 주인공이다.
  final String arrivalTime;

  factory MyClinicReservation.fromJson(Map<String, dynamic> json) =>
      MyClinicReservation(arrivalTime: json['arrivalTime'] as String);
}

/// 「08-13 (목) 17:00~22:00」. 웹 `shared/clinic/types.ts` 의 `formatClinicSlot`.
String clinicSlotLabel(StudentClinic c) =>
    '${c.clinicDate.substring(5)} (${dayOfWeekLabel(c.clinicDate)}) '
    '${c.startTime}~${c.endTime}';

/// 수업일 변경 화면의 수업 한 칸. 백엔드 `LessonSlotResponse`.
///
/// **날짜·반 이름·시각이 전부다.** 다른 반 수업까지 나오는 목록이라 서버가
/// 수업 내용을 안 보낸다 — 필드를 늘리지 마라.
class LessonSlot {
  const LessonSlot({
    required this.lessonId,
    required this.classRoomName,
    required this.lessonDate,
    required this.startTime,
  });

  final int lessonId;
  final String classRoomName;
  final String lessonDate;

  /// 그 반에 그 요일 슬롯이 없으면 null 이다. **지어내지 마라**(14-3).
  final String? startTime;

  factory LessonSlot.fromJson(Map<String, dynamic> json) => LessonSlot(
    lessonId: (json['lessonId'] as num).toInt(),
    classRoomName: json['classRoomName'] as String,
    lessonDate: json['lessonDate'] as String,
    startTime: json['startTime'] as String?,
  );
}

/// 「08-13 (목) A고 2학년 목요일반 19:00」. 웹 `formatLessonSlot`.
/// 시각이 없으면 뺀다. 서버가 `HH:mm:ss` 로 줄 수 있어 다섯 자로 자른다.
String lessonSlotLabel(LessonSlot l) {
  final start = l.startTime;
  final time = start == null ? '' : ' ${start.substring(0, 5)}';
  return '${l.lessonDate.substring(5)} (${dayOfWeekLabel(l.lessonDate)}) '
      '${l.classRoomName}$time';
}

/// 수업일 변경 요청. 백엔드 `LessonChangeRequestResponse`.
class LessonChangeRequest {
  const LessonChangeRequest({
    required this.requestId,
    required this.from,
    required this.to,
    required this.reason,
    required this.status,
  });

  final int requestId;
  final LessonSlot from;
  final LessonSlot to;
  final String reason;

  /// `PENDING` · `APPROVED` · `REJECTED`.
  final String status;

  factory LessonChangeRequest.fromJson(Map<String, dynamic> json) =>
      LessonChangeRequest(
        requestId: (json['requestId'] as num).toInt(),
        from: LessonSlot.fromJson(json['from'] as Map<String, dynamic>),
        to: LessonSlot.fromJson(json['to'] as Map<String, dynamic>),
        reason: json['reason'] as String,
        status: json['status'] as String,
      );
}

/// 웹 `STATUS_LABELS`. 모르는 값은 서버 문자열 그대로다.
String lessonChangeStatusLabel(String status) => switch (status) {
  'PENDING' => '승인 대기',
  'APPROVED' => '승인됨',
  'REJECTED' => '거절됨',
  _ => status,
};

/// 한 화면 분량. 클리닉은 [from] 부터 3주다(웹과 같다).
class StudentScheduleData {
  const StudentScheduleData({required this.clinics, required this.requests});

  /// 열린 클리닉 전부(이동 후보 포함).
  final List<StudentClinic> clinics;
  final List<LessonChangeRequest> requests;

  List<StudentClinic> get mine =>
      clinics.where((c) => c.myReservation != null).toList();

  /// [current] 에서 옮겨 갈 수 있는 곳. 웹 `ChangeModal` 의 `candidates` 와 같다 —
  /// 자기 자신·꽉 찬 것·이미 배정된 것은 뺀다.
  List<StudentClinic> moveTargets(StudentClinic current) => clinics
      .where(
        (c) =>
            c.clinicId != current.clinicId &&
            !c.full &&
            c.myReservation == null,
      )
      .toList();
}

/// 「2026-09-27」 + [days]. 달력 날짜 계산이라 UTC 로 한다 — 기기 시간대를 타면
/// 서머타임 있는 곳에서 하루가 밀린다.
String addDaysTo(String date, int days) {
  final p = date.split('-').map(int.parse).toList();
  return _ymd(DateTime.utc(p[0], p[1], p[2]).add(Duration(days: days)));
}

String _ymd(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-'
    '${d.month.toString().padLeft(2, '0')}-'
    '${d.day.toString().padLeft(2, '0')}';

/// 오늘(KST). 조회 범위의 시작이다(14-10 — 기기 시간대가 아니라 KST).
String kstToday([DateTime? now]) =>
    _ymd((now ?? DateTime.now()).toUtc().add(const Duration(hours: 9)));

class StudentScheduleRepository {
  const StudentScheduleRepository(this._dio);

  final Dio _dio;

  Future<List<StudentClinic>> clinics(String from, String to) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/student/clinics',
      queryParameters: {'from': from, 'to': to},
    ),
    (data) => [
      for (final e in (data as List<dynamic>?) ?? const [])
        StudentClinic.fromJson(e as Map<String, dynamic>),
    ],
  );

  /// 도착 시각 변경 · 다른 클리닉으로 이동. **선생님 승인이 없다** — 즉시
  /// 반영되고 공지가 한 건 발행된다(10-1). [targetClinicId] 가 null 이면 같은
  /// 클리닉 안에서 시각만 바꾼다. **취소 API 는 없다**(10-2).
  Future<void> changeClinic(
    int clinicId, {
    required int? targetClinicId,
    required String arrivalTime,
    required String reason,
  }) => unwrapCall(
    () => _dio.patch<Map<String, dynamic>>(
      '/api/student/clinics/$clinicId/reservation',
      data: {
        'targetClinicId': targetClinicId,
        'arrivalTime': arrivalTime,
        'reason': reason,
      },
    ),
    (_) {},
  );

  Future<List<LessonChangeRequest>> lessonChanges() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/lesson-changes'),
    (data) => [
      for (final e in (data as List<dynamic>?) ?? const [])
        LessonChangeRequest.fromJson(e as Map<String, dynamic>),
    ],
  );

  /// 못 가는 회차로 고를 수 있는 내 수업. 오늘부터 한 달.
  Future<List<LessonSlot>> changeableLessons() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/student/lesson-changes/my-lessons',
    ),
    _slots,
  );

  /// 대신 갈 수업 후보. 그 수업이 있는 주(월~일)의 다른 반 수업만 나온다.
  Future<List<LessonSlot>> changeCandidates(int fromLessonId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/student/lesson-changes/candidates',
      queryParameters: {'fromLessonId': fromLessonId},
    ),
    _slots,
  );

  /// 요청만 한다. 승인돼도 반 배정·수업은 안 바뀌고 공지가 한 건 뜰 뿐이다.
  Future<void> requestLessonChange({
    required int fromLessonId,
    required int toLessonId,
    required String reason,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/lesson-changes',
      data: {
        'fromLessonId': fromLessonId,
        'toLessonId': toLessonId,
        'reason': reason,
      },
    ),
    (_) {},
  );

  static List<LessonSlot> _slots(Object? data) => [
    for (final e in (data as List<dynamic>?) ?? const [])
      LessonSlot.fromJson(e as Map<String, dynamic>),
  ];
}

/// 스케줄 화면. 매개변수는 조회 시작일(KST 오늘)이다 — 앱이 며칠씩 살아 있어도
/// 날이 바뀌면 다시 보일 때 새 범위로 부른다.
class StudentScheduleController
    extends ParamController<String, StudentScheduleData> {
  StudentScheduleController({
    required this.repository,
    super.staleAfter,
    super.now,
  });

  /// 화면의 시트(변경·요청)가 직접 부른다.
  final StudentScheduleRepository repository;

  @override
  Future<StudentScheduleData> fetch(String param) async {
    final (clinics, requests) = await both(
      repository.clinics(param, addDaysTo(param, 20)),
      repository.lessonChanges(),
    );
    return StudentScheduleData(clinics: clinics, requests: requests);
  }
}
