import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../core/api/api_exception.dart';
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
  bool _loaded = false;
  int? _selected;

  /// 로그아웃하면 세션째 dispose 된다(`AcademyApp` 의 `_Session`). 그 뒤에
  /// 도착한 앞 사람의 목록은 쓰지도 알리지도 않는다.
  bool _disposed = false;

  List<Child> get children => _children;
  bool get loading => _loading;

  /// 목록을 한 번이라도 받았는가. **빈 목록과 아직 안 받음을 가른다** —
  /// 둘 다 [children] 이 비어 있어서, 이게 없으면 자녀가 0명인 학부모의
  /// 홈이 「불러오는 중」에서 영원히 안 끝난다(부를 id 가 없다).
  bool get loaded => _loaded;
  int? get selectedStudentId => _selected;

  /// 마지막 [load] 가 실패했으면 그 문구(서버 문구 그대로). 성공하거나 다시
  /// 부르기 시작하면 지워진다.
  ///
  /// 홈은 [load] 가 던지는 예외를 직접 받아 띄운다. 이 값은 **홈을 거치지 않고
  /// 열린 화면**(일정·레포트·성적·내 정보 탭, 레일의 하위 화면)이 [ensureLoaded]
  /// 로 부른 뒤 읽는다 — 없으면 목록을 못 받은 그 화면이 로더에서 영영 안 끝난다.
  String? get loadError => _loadError;
  String? _loadError;

  /// 아직 목록을 받지 않았고 받는 중도 아니면 부른다. 실패는 [loadError] 로 남는다.
  ///
  /// 학부모 하위 화면은 전부 `initState` 에서 이것을 부른다(14-6). 로그인 직후
  /// 첫 화면이 홈이라 보통은 홈이 먼저 받아 두지만, 그것에 기대지 마라.
  void ensureLoaded() {
    if (_disposed || _loaded || _loading) return;
    load().catchError((Object _) {});
  }

  Future<void> load() async {
    if (_disposed) return;
    _loading = true;
    _loadError = null;
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
      _loaded = true;
      // 저장된 값이 더 이상 내 자녀가 아니면 첫째로 되돌린다.
      _selected = list.any((c) => c.studentId == saved)
          ? saved
          : (list.isEmpty ? null : list.first.studentId);
    } on ApiException catch (e) {
      if (!_disposed) _loadError = e.message;
      rethrow;
    } catch (_) {
      if (!_disposed) _loadError = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
      rethrow;
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

  /// 알림이 가리키는 자녀를 고른다(푸시의 `studentId`). 목록을 아직 안 받았으면
  /// 받고 나서 본다. **내 자녀가 아니면 아무것도 바꾸지 않는다** — 알림 data 는
  /// 기기로 들어온 값이라 [select] 에 그대로 넣으면 남의 id 가 저장된다.
  /// 목록을 못 받았으면 포기한다(화면의 게이트가 오류를 띄운다).
  Future<void> selectIfMine(int studentId) async {
    if (_disposed) return;
    if (!_loaded) {
      try {
        await load();
      } catch (_) {
        return;
      }
    }
    if (_disposed) return;
    if (_children.any((c) => c.studentId == studentId)) await select(studentId);
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
