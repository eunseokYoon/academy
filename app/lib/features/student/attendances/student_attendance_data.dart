import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';

/// S-6 의 저장소. 요청이 둘이다 — 수업 출석과 그 달의 클리닉.
class StudentAttendanceRepository {
  const StudentAttendanceRepository(this._dio);

  final Dio _dio;

  Future<AttendanceMonth> month(YearMonth ym) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/student/attendances',
      queryParameters: {'year': ym.year, 'month': ym.month},
    ),
    (data) => AttendanceMonth.fromJson(data! as Map<String, dynamic>),
  );

  /// 그 달의 **내** 클리닉.
  ///
  /// 이 응답은 열린 클리닉을 **전부** 내려준다(이동 후보라서다 — CLAUDE.md
  /// 「만들지 마라」의 학생 클리닉 신청 항목). `myReservation` 이 없는 클리닉을
  /// 남기면 남의 시간대가 내 출결로 보인다. 서버는 살아 있는 예약만 붙인다
  /// (`findReservedByStudentInRange`).
  Future<List<ClinicEntry>> clinics(YearMonth ym) {
    final (from, to) = monthRange(ym);
    return unwrapCall(
      () => _dio.get<Map<String, dynamic>>(
        '/api/student/clinics',
        queryParameters: {'from': from, 'to': to},
      ),
      (data) => [
        for (final e in (data as List<dynamic>?) ?? const [])
          if ((e as Map<String, dynamic>)['myReservation']
              case final Map<String, dynamic> mine)
            ClinicEntry(
              date: e['clinicDate'] as String,
              status: DayStatus.parse(mine['attendStatus'] as String?),
            ),
      ],
    );
  }
}

/// S-6. 매개변수는 달이다.
class StudentAttendanceController
    extends ParamController<YearMonth, AttendanceCalendarData> {
  StudentAttendanceController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final StudentAttendanceRepository _repository;

  @override
  Future<AttendanceCalendarData> fetch(YearMonth param) async {
    // 함께 기다린다. 레코드 `.wait` 는 서버 문구를 잃는다([both] 주석).
    final (month, clinics) = await both(
      _repository.month(param),
      _repository.clinics(param),
    );
    return AttendanceCalendarData(month: month, clinics: clinics);
  }
}
