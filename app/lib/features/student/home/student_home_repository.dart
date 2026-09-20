import 'package:dio/dio.dart';

import '../../../core/api/api_response.dart';
import 'student_home_models.dart';

/// 학생 홈. **호출은 하나다** — 백엔드가 여러 도메인을 조합해 단일 API 로
/// 묶어 내려준다. 화면에서 추가 호출을 붙이지 마라.
class StudentHomeRepository {
  const StudentHomeRepository(this._dio);

  final Dio _dio;

  Future<StudentHome> fetch() {
    return unwrapCall<StudentHome>(
      () => _dio.get<Map<String, dynamic>>('/api/student/home'),
      (data) => StudentHome.fromJson(data! as Map<String, dynamic>),
    );
  }
}
