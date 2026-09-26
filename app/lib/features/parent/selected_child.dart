import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../core/api/api_response.dart';
import '../../core/storage/key_value_store.dart';

class Child {
  const Child({required this.studentId, required this.name});

  final int studentId;

  /// `students.name` 이다. 미가입 학생은 `users` 행이 없다.
  final String name;

  factory Child.fromJson(Map<String, dynamic> json) =>
      Child(studentId: json['studentId'] as int, name: json['name'] as String);
}

/// 학부모 화면은 전부 `/api/parent/children/{studentId}/...` 라 선택된 자녀
/// ID 가 모든 호출에 실린다. 화면마다 드롭다운을 두지 않고 셸이 한 곳에서
/// 관리한다.
///
/// **선택값을 기기에 저장한다.** 웹은 `sessionStorage`(탭 수명)를 쓰지만
/// 앱에는 대응물이 없고 프로세스가 며칠씩 산다 — 둘째 아이를 고른 학부모가
/// 앱을 껐다 켜도 그 아이가 선택돼 있어야 한다.
///
/// 기기에 저장한 선택값은 로그아웃해도 지우지 않는다 — 다음 [load] 가
/// 「내 자녀인가」를 검사하므로 남의 아이를 고르지 않고, 같은 사람이 다시
/// 들어오면 그 아이가 그대로 골라져 있다.
///
/// **자녀가 하나면 화면이 선택 UI 를 안 그린다.** 고를 게 없는 선택지는
/// 화면만 어지럽힌다.
class SelectedChild extends ChangeNotifier {
  SelectedChild({required this._dio, required this._store});

  static const _storageKey = 'academy.selectedStudentId';

  final Dio _dio;
  final KeyValueStore _store;

  List<Child> _children = const [];
  bool _loading = false;
  int? _selected;

  /// 로그아웃하면 세션째 dispose 된다(`AcademyApp` 의 `_Session`). 그 뒤에
  /// 도착한 앞 사람의 목록은 쓰지도 알리지도 않는다.
  bool _disposed = false;

  List<Child> get children => _children;
  bool get loading => _loading;
  int? get selectedStudentId => _selected;

  Future<void> load() async {
    if (_disposed) return;
    _loading = true;
    notifyListeners();
    try {
      final list = await unwrapCall<List<Child>>(
        () => _dio.get<Map<String, dynamic>>('/api/parent/children'),
        (data) => ((data as List<dynamic>?) ?? const [])
            .map((e) => Child.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      if (_disposed) return; // 로그아웃 뒤 도착했다. 버린다.
      final saved = int.tryParse(await _store.read(_storageKey) ?? '');
      if (_disposed) return;
      _children = list;
      // 저장된 값이 더 이상 내 자녀가 아니면 첫째로 되돌린다.
      _selected = list.any((c) => c.studentId == saved)
          ? saved
          : (list.isEmpty ? null : list.first.studentId);
    } finally {
      if (!_disposed) {
        _loading = false;
        notifyListeners();
      }
    }
  }

  Future<void> select(int studentId) async {
    if (_selected == studentId) return;
    _selected = studentId;
    await _store.write(_storageKey, '$studentId');
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
