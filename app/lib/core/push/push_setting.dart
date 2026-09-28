import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../api/api_response.dart';

/// `/api/push/settings`. 알림 전체 켜기·끄기 한 칸이다(`users.push_enabled`).
/// **종류별 토글을 만들지 마라** — 설계에서 뺐다.
class PushSettingRepository {
  const PushSettingRepository(this._dio);

  final Dio _dio;

  Future<bool> get() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/push/settings'),
    _enabled,
  );

  Future<bool> change(bool enabled) => unwrapCall(
    () => _dio.patch<Map<String, dynamic>>(
      '/api/push/settings',
      data: {'enabled': enabled},
    ),
    _enabled,
  );

  static bool _enabled(Object? data) =>
      (data! as Map<String, dynamic>)['enabled'] as bool;
}

/// 학생 「내 정보 · 성적」과 학부모 내 정보가 같이 쓴다. 매개변수가 없다.
class PushSettingController extends ParamController<(), bool> {
  PushSettingController({
    required this.repository,
    super.staleAfter,
    super.now,
  });

  /// 켜고 끄기는 스위치가 직접 부르고, 성공하면 [reload].
  final PushSettingRepository repository;

  @override
  Future<void> load([() param = ()]) => super.load(param);

  @override
  Future<bool> fetch(() param) => repository.get();
}
