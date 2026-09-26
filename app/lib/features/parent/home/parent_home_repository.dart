import 'package:dio/dio.dart';

import '../../../core/api/api_response.dart';
import 'parent_home_models.dart';

/// 선택된 자녀의 홈. **호출은 하나다.**
///
/// 경로에 `studentId` 가 들어가고, 서버의 `studentAccessGuard` 가 남의 자녀
/// 접근을 막는다 — 앱에서 막는 게 아니다.
class ParentHomeRepository {
  const ParentHomeRepository(this._dio);

  final Dio _dio;

  Future<ParentHome> fetch(int studentId) {
    return unwrapCall<ParentHome>(
      () => _dio.get<Map<String, dynamic>>(
        '/api/parent/children/$studentId/home',
      ),
      (data) => ParentHome.fromJson(data! as Map<String, dynamic>),
    );
  }
}
