import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../core/api/api_exception.dart';
import '../../student/home/student_home_controller.dart' show HomeStatus;
import 'parent_home_models.dart';
import 'parent_home_repository.dart';

/// 학부모 홈의 상태. 학생 홈 컨트롤러와 같은 규칙(60초·refresh 무시·실패 시
/// 데이터 유지·서버 문구 그대로) 위에 **자녀 전환** 하나가 더 있다.
///
/// `HomeStatus` 는 학생 쪽 것을 그대로 쓴다 — 다시 정의하지 않는다.
///
/// **자녀가 바뀌면 캐시를 버리고 60초 규칙 없이 무조건 다시 부른다.**
/// 학부모가 둘째를 보려고 바꿨는데 첫째 데이터가 60초 남아 있으면 그건
/// 잘못된 정보다. 옛 데이터는 **새 요청을 보내기 전에** 버린다 — [load] 가
/// 리스너에게 처음 알릴 때 이미 이전 자녀의 이름이 없어야 한다.
///
/// **진행 중인 요청은 같은 자녀일 때만 합류한다.** `load(다른 id)` 는 다른
/// 요청이다 — 옛 자녀의 요청을 기다리면 둘째를 골랐는데 첫째가 뜨는 것과
/// 같다. 가드(`_inFlightStudentId`·`_inFlight`)는 [_run] 을 부르기
/// **전에** 무장한다. [_run] 은 `async` 라 호출 시점에 첫 `await`(여기선
/// 첫 [notifyListeners] 호출)까지 동기로 실행되므로, 그 동기 구간에서
/// 리스너가 [load]/[refresh] 를 다시 부르면 가드가 아직 없는 채로
/// 재진입해 요청이 두 번 나갈 수 있다.
///
/// **늦게 도착한 옛 요청의 응답은 조용히 버린다.** 자녀를 빠르게 바꾸면
/// 먼저 나간 요청이 나중에 끝날 수 있다:
///
/// ```
/// load(1) 나감 ──────────────(느림)──────────────▶ 응답 1 도착
///       load(2) 나감 ──(빠름)──▶ 응답 2 도착
/// ```
///
/// **`studentId` 만으로는 부족하다.** `load(1)→load(2)→load(1)` 처럼 같은
/// 자녀로 되돌아오면 두 번째 `load(1)`(A2)이 첫 번째 `load(1)`(A1)과
/// **같은 studentId** 를 갖는다 — `requestedId != _studentId` 검사는 A1
/// 도 A2 도 통과시켜, A1 이 A2 보다 늦게 도착하면 최신 데이터를 옛 데이터로
/// 덮어써 버린다. 그래서 실제 요청(합류가 아니라 [_run] 이 실행되는
/// 시점)마다 단조 증가하는 [_generation] 을 매긴다. 응답을 적용하기 전에
/// 그 요청의 세대가 **현재 세대와 같은지**를 확인한다. 다르면(더 새 요청이
/// 이미 시작됐으면) `data`·`error`·`status` 무엇도 건드리지 않는다 —
/// 성공이든 실패든 같다. 그 응답은 이미 아무도 보고 있지 않은 요청의
/// 것이다. 자녀 전환도 매번 새 [_run] 을 실행하므로 세대가 함께 오른다 —
/// 옛 `studentId` 검사가 하던 일을 세대 검사가 그대로 포함한다.
///
/// **dispose 이후에는 아무것도 쓰지도 알리지도 않는다.** 화면이 응답을
/// 기다리는 중에 닫히면(느린 망에서 뒤로 가기) 응답은 dispose 뒤에
/// 도착한다 — 그 시점에 상태를 쓰거나 [notifyListeners] 를 부르는 것은
/// 의미가 없고, 후자는 Flutter 가 "used after being disposed"로 던진다.
class ParentHomeController extends ChangeNotifier {
  ParentHomeController({
    required ParentHomeRepository repository,
    Duration staleAfter = const Duration(seconds: 60),
    DateTime Function()? now,
  }) : // named 매개변수에 private 이름을 바로 쓸 수 없어(컴파일 에러) 이
       // 방식으로 필드를 채운다. student_home_controller.dart 와 동일.
       // ignore: prefer_initializing_formals
       _repository = repository,
       // ignore: prefer_initializing_formals
       _staleAfter = staleAfter,
       _now = now ?? DateTime.now;

  final ParentHomeRepository _repository;
  final Duration _staleAfter;
  final DateTime Function() _now;

  HomeStatus _status = HomeStatus.idle;
  ParentHome? _data;
  String? _error;
  DateTime? _loadedAt;
  int? _studentId;

  /// 현재 진행 중인 요청이 어느 자녀의 것인지. `_inFlight` 와 함께만 뜻이
  /// 있다 — 자녀가 다르면 합류 대상이 아니다.
  int? _inFlightStudentId;
  Future<void>? _inFlight;
  bool _disposed = false;

  /// 실제로 나간 요청([_run] 이 실행될 때)마다 매기는 단조 증가 번호.
  /// 응답을 적용하기 전에 이 번호가 여전히 최신인지 확인한다 — 합류는
  /// 새 [_run] 을 실행하지 않으므로 세대를 올리지 않는다.
  int _generation = 0;

  HomeStatus get status => _status;
  ParentHome? get data => _data;
  String? get error => _error;
  int? get studentId => _studentId;

  /// 자녀 선택·전환용.
  ///
  /// 이전에 선택된 자녀와 다르면 캐시를 즉시 버리고(60초 규칙을 건너뛰고)
  /// 무조건 다시 부른다. 같은 자녀면 학생 컨트롤러와 같은 60초 규칙을
  /// 따른다 — 마지막 성공이 60초 이내면 아무것도 하지 않는다.
  Future<void> load(int studentId) {
    if (studentId != _studentId) {
      // 자녀가 바뀌었다. 옛 데이터는 새 요청을 내보내기 전에 버린다 —
      // 진행 중 요청이 있어도(다른 자녀 것이므로) 합류하지 않고 _fetch 가
      // 새로 쏜다.
      _studentId = studentId;
      _data = null;
      _loadedAt = null;
      _error = null;
      return _fetch(studentId);
    }
    final loadedAt = _loadedAt;
    if (loadedAt != null && _now().difference(loadedAt) < _staleAfter) {
      return Future<void>.value();
    }
    return _fetch(studentId);
  }

  /// 당겨서 새로고침. 60초 규칙을 무시하고 현재 자녀를 다시 부른다.
  /// 자녀를 아직 고르지 않았으면 아무것도 하지 않는다.
  Future<void> refresh() {
    final id = _studentId;
    if (id == null) return Future<void>.value();
    return _fetch(id);
  }

  /// 같은 자녀의 진행 중 요청이 있으면 그것을 기다리고, 없으면 새로 쏜다.
  ///
  /// 가드를 [_run] 을 부르기 **전에** 무장한다 — 그러지 않으면 [_run] 의
  /// 동기 구간에서 리스너가 재진입했을 때 가드가 아직 없어 요청이 두 번
  /// 나간다.
  Future<void> _fetch(int requestedId) {
    if (_inFlight != null && _inFlightStudentId == requestedId) {
      return _inFlight!;
    }
    final completer = Completer<void>();
    _inFlightStudentId = requestedId;
    _inFlight = completer.future;
    _run(requestedId).whenComplete(() {
      // 늦게 끝난 옛 자녀의 요청이 그 사이 새로 무장된(다른 자녀의) 가드를
      // 지우면 안 된다 — 자기 future 가 여전히 현재 가드일 때만 비운다.
      if (identical(_inFlight, completer.future)) {
        _inFlight = null;
        _inFlightStudentId = null;
      }
      completer.complete();
    });
    return completer.future;
  }

  Future<void> _run(int requestedId) async {
    // 이 실행의 세대를 고정한다 — 응답이 돌아왔을 때 그 사이 더 새 요청이
    // 시작됐는지(자녀가 바뀌었든, 같은 자녀로 되돌아왔든) 판단하는 유일한
    // 기준이다. `requestedId != _studentId` 만으로는 부족하다: 같은
    // studentId 로 되돌아온 요청은 이 검사를 통과해 버려서, 늦게 도착한
    // 옛 요청이 최신 데이터를 덮어쓸 수 있다.
    final gen = ++_generation;
    // 유지할 데이터가 있었는지를 실행 시작 시점에 고정한다. 자녀 전환
    // 직후라면 load() 가 이미 _data 를 비운 뒤이므로 여기서는 항상
    // false 다 — 새 자녀의 첫 실패는 error 상태가 된다.
    final keepDataOnError = _data != null;
    if (_data == null) _status = HomeStatus.loading;
    _error = null;
    _notify();
    try {
      final home = await _repository.fetch(requestedId);
      if (_disposed) return;
      if (gen != _generation) return; // 더 새 요청이 이미 나갔다. 버린다.
      _data = home;
      _loadedAt = _now();
      _error = null;
      _status = HomeStatus.ready;
    } on ApiException catch (e) {
      if (_disposed) return;
      if (gen != _generation) return; // 옛 요청의 실패를 띄우지 않는다.
      // 서버 문구를 그대로 쓴다 — 앱에서 감싸거나 접두어를 붙이지 마라.
      _error = e.message;
      if (!keepDataOnError) _status = HomeStatus.error;
    } catch (_) {
      if (_disposed) return;
      if (gen != _generation) return;
      _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
      if (!keepDataOnError) _status = HomeStatus.error;
    }
    _notify();
  }

  /// dispose 뒤에는 [notifyListeners] 를 부르지 않는다.
  void _notify() {
    if (_disposed) return;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
