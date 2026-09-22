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

  List<Child> get children => _children;
  bool get loading => _loading;
  int? get selectedStudentId => _selected;

  Future<void> load() async {
    _loading = true;
    notifyListeners();
    try {
      final list = await unwrapCall<List<Child>>(
        () => _dio.get<Map<String, dynamic>>('/api/parent/children'),
        (data) => ((data as List<dynamic>?) ?? const [])
            .map((e) => Child.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
      _children = list;
      final saved = int.tryParse(await _store.read(_storageKey) ?? '');
      // 저장된 값이 더 이상 내 자녀가 아니면 첫째로 되돌린다.
      _selected = list.any((c) => c.studentId == saved)
          ? saved
          : (list.isEmpty ? null : list.first.studentId);
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> select(int studentId) async {
    if (_selected == studentId) return;
    _selected = studentId;
    await _store.write(_storageKey, '$studentId');
    notifyListeners();
  }
}
