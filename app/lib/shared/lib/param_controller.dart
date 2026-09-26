import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/api/api_exception.dart';
import '../../features/student/home/student_home_controller.dart'
    show HomeStatus;

/// 매개변수(숙제 id · 자녀 · 달…)가 있는 화면 컨트롤러의 **공용 본체**.
///
/// `ParentHomeController` 의 규칙을 그대로 옮겼다(CLAUDE.md 14-5). B1 은 화면마다
/// 복제하라고 했지만, 복제할 때마다 가드 하나씩이 빠졌다는 것이 그 규칙의
/// 이유라서, B2 부터는 가드를 **여기 한 곳**에 둔다. 화면은 [fetch] 하나만
/// 구현한다.
///
/// - **60초 규칙.** 같은 매개변수의 [load] 는 마지막 성공이 60초 이내면
///   건너뛴다. [refresh] 는 무시한다. **새로고침 실패는 기존 데이터를 유지한다.**
/// - **매개변수가 바뀌면** 옛 데이터를 새 요청 **전에** 버리고 60초 없이 부른다.
///   옛 데이터를 남겨 두면 9월 머리 아래 10월 목록이 뜬다.
/// - **진행 중 요청에는 같은 매개변수일 때만 합류한다.** 가드는 [_run] 을
///   부르기 **전에** 무장한다 — [_run] 은 async 라 첫 `await` 까지 동기로
///   돌고, 그 사이 리스너가 다시 부르면 가드를 통과해 버린다.
/// - **세대 번호.** 실제 요청마다 올리고, 응답을 적용하기 전에 최신인지 본다.
///   「지금 매개변수의 응답인가」만 보면 A→B→A 에서 A 의 옛 요청이 새 요청을 덮는다.
/// - **dispose 가드.** 느린 망에서 뒤로 가면 응답이 죽은 컨트롤러에 도착한다.
/// - **서버 문구를 그대로 쓴다.**
///
/// [reload] 는 저장 뒤(사진 올림·제출)에 쓴다. 진행 중 요청에 **합류하지 않고**
/// 새로 쏜다 — 저장 전에 나간 요청의 응답은 저장 결과를 모른다. 세대 번호가
/// 그 옛 응답을 버린다.
///
/// [markStale] 은 **다른 화면**이 이 화면의 데이터를 낡게 만들었을 때 쓴다
/// (상세에서 제출 → 목록). 지금 부르지 않고, 다음 [load](다시 보일 때)가
/// 60초와 상관없이 부르게 한다.
abstract class ParamController<P, T> extends ChangeNotifier {
  ParamController({
    this._staleAfter = const Duration(seconds: 60),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final Duration _staleAfter;
  final DateTime Function() _now;

  HomeStatus _status = HomeStatus.idle;
  T? _data;
  String? _error;
  DateTime? _loadedAt;
  P? _param;
  bool _hasParam = false;

  P? _inFlightParam;
  Future<void>? _inFlight;
  bool _disposed = false;
  int _generation = 0;

  HomeStatus get status => _status;
  T? get data => _data;
  String? get error => _error;

  /// 지금 화면이 보고 있는 매개변수. 아직 한 번도 안 불렀으면 null 이다.
  P? get param => _param;

  /// 요청 하나. 화면은 이것만 구현한다.
  @protected
  Future<T> fetch(P param);

  Future<void> load(P param) {
    if (!_hasParam || param != _param) {
      _hasParam = true;
      _param = param;
      _data = null;
      _loadedAt = null;
      _error = null;
      return _fetch(param, join: false);
    }
    final loadedAt = _loadedAt;
    if (loadedAt != null && _now().difference(loadedAt) < _staleAfter) {
      return Future<void>.value();
    }
    return _fetch(param, join: true);
  }

  /// 당겨서 새로고침. 60초 규칙을 무시한다. 아직 매개변수가 없으면 아무것도
  /// 하지 않는다.
  Future<void> refresh() =>
      _hasParam ? _fetch(_param as P, join: true) : Future<void>.value();

  /// 저장 뒤. 진행 중 요청에 합류하지 않고 새로 쏜다(클래스 주석).
  Future<void> reload() =>
      _hasParam ? _fetch(_param as P, join: false) : Future<void>.value();

  /// 다음 [load] 가 60초와 상관없이 부르게 한다(클래스 주석).
  void markStale() => _loadedAt = null;

  Future<void> _fetch(P requested, {required bool join}) {
    if (_disposed) return Future<void>.value();
    final existing = _inFlight;
    if (join && existing != null && _inFlightParam == requested) {
      return existing;
    }
    final completer = Completer<void>();
    // 가드를 [_run] 을 부르기 **전에** 무장한다(클래스 주석).
    _inFlightParam = requested;
    _inFlight = completer.future;
    _run(requested).whenComplete(() {
      // 늦게 끝난 옛 요청이 그 사이 새로 무장된 가드를 지우면 안 된다.
      if (identical(_inFlight, completer.future)) {
        _inFlight = null;
        _inFlightParam = null;
      }
      completer.complete();
    });
    return completer.future;
  }

  Future<void> _run(P requested) async {
    final gen = ++_generation;
    // 유지할 데이터가 있었는지를 시작 시점에 고정한다 — 실패 시 error 로
    // 떨어뜨릴지, ready 로 남겨 기존 데이터를 지킬지의 기준이다.
    final keepDataOnError = _data != null;
    if (_data == null) _status = HomeStatus.loading;
    _error = null;
    _notify();
    try {
      final value = await fetch(requested);
      if (_disposed || gen != _generation) return;
      _data = value;
      _loadedAt = _now();
      _error = null;
      _status = HomeStatus.ready;
    } on ApiException catch (e) {
      if (_disposed || gen != _generation) return;
      // 서버 문구를 그대로 쓴다 — 감싸거나 접두어를 붙이지 마라.
      _error = e.message;
      if (!keepDataOnError) _status = HomeStatus.error;
    } catch (_) {
      if (_disposed || gen != _generation) return;
      _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
      if (!keepDataOnError) _status = HomeStatus.error;
    }
    _notify();
  }

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
