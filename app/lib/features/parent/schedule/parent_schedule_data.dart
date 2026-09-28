import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';

class ParentScheduleRepository {
  const ParentScheduleRepository(this._dio);

  final Dio _dio;

  Future<AttendanceMonth> month(int studentId, YearMonth ym) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/attendances',
      queryParameters: {'year': ym.year, 'month': ym.month},
    ),
    (data) => AttendanceMonth.fromJson(data! as Map<String, dynamic>),
  );

  /// 그 달의 자녀 클리닉. 학부모 응답은 **자녀의 예약만** 온다 — 학생 응답처럼
  /// 걸러낼 필요가 없다.
  ///
  /// `week` 를 넘기지 않는다. 주차 → 날짜 변환은 서버가 한다(9-2) — 주간
  /// 레포트(P-6)가 그 경로를 쓴다.
  Future<List<ClinicEntry>> clinics(int studentId, YearMonth ym) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/clinics',
      queryParameters: {'year': ym.year, 'month': ym.month},
    ),
    (data) => [
      for (final e in (data as List<dynamic>?) ?? const [])
        ClinicEntry(
          date: (e as Map<String, dynamic>)['clinicDate'] as String,
          status: DayStatus.parse(e['attendStatus'] as String?),
        ),
    ],
  );
}

/// P-2. 매개변수는 (자녀, 달)이다 — 자녀가 바뀌어도 달이 바뀌어도 옛 캘린더를
/// 버리고 새로 부른다([ParamController]).
class ParentScheduleController
    extends ParamController<(int, YearMonth), AttendanceCalendarData> {
  ParentScheduleController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final ParentScheduleRepository _repository;

  @override
  Future<AttendanceCalendarData> fetch((int, YearMonth) param) async {
    final (id, ym) = param;
    final (month, clinics) = await both(
      _repository.month(id, ym),
      _repository.clinics(id, ym),
    );
    return AttendanceCalendarData(month: month, clinics: clinics);
  }
}
