import 'package:flutter/foundation.dart';

import '../../../core/api/api_exception.dart';
import 'student_home_models.dart';
import 'student_home_repository.dart';

enum HomeStatus { idle, loading, ready, error }

/// 학생 홈의 상태. **B2~B4 의 화면들이 이 모양을 복제한다.**
///
/// **60초 규칙**: 탭이 다시 전면에 올 때 [load] 를 불러도 마지막 성공이
/// 60초 이내면 건너뛴다. 상태 관리 라이브러리 없이 낡음을 한 화면 수명
/// 안에 가두는 장치다 — 홈에 돌아올 때 스피너가 번쩍이지 않는다.
///
/// [refresh] 는 60초를 무시한다. 당겨서 새로고침은 사용자가 명시적으로
/// 요구한 것이다.
///
/// **새로고침 실패는 기존 데이터를 유지한다.** 당겨서 새로고침하다 실패했는데
/// 화면이 비면 손해다. 이때 [status] 는 `ready` 로 남고 [error] 만 채워진다.
///
/// **서버 문구를 그대로 쓴다.** [ApiException.message] 를 감싸거나
/// 접두어를 붙이지 않는다.
///
/// **진행 중인 요청이 있으면 새로 부르지 않고 합류한다.** [load] 와 [refresh]
/// 가 겹치는 경로가 둘 있다 — 앱이 뜨자마자 나간 첫 요청의 응답 전에 탭을
/// 옮겼다 돌아오는 경우, 느린 망에서 첫 로딩 중에 당겨서 새로고침하는
/// 경우. 두 응답이 경쟁하면 나중에 도착한 쪽이 이기는데 그게 더 오래된
/// 요청일 수 있다 — 그래서 이미 나간 요청이 있으면 새로 부르지 않고
/// 그 요청을 그대로 기다린다.
class StudentHomeController extends ChangeNotifier {
  StudentHomeController({
    required StudentHomeRepository repository,
    Duration staleAfter = const Duration(seconds: 60),
    DateTime Function()? now,
  }) : // named 매개변수에 private 이름(`this._repository`)을 쓰는 것을 Dart가
       // 금지한다(컴파일 에러다, 조용히 깨지는 게 아니다). 필드를 private으로
       // 두려면 이 방식뿐이다.
       // ignore: prefer_initializing_formals
       _repository = repository,
       // ignore: prefer_initializing_formals
       _staleAfter = staleAfter,
       _now = now ?? DateTime.now;

  final StudentHomeRepository _repository;
  final Duration _staleAfter;
  final DateTime Function() _now;

  HomeStatus _status = HomeStatus.idle;
  StudentHome? _data;
  String? _error;
  DateTime? _loadedAt;
  Future<void>? _inFlight;

  HomeStatus get status => _status;
  StudentHome? get data => _data;
  String? get error => _error;

  /// 탭 재진입용. 마지막 성공이 60초 이내면 아무것도 하지 않는다.
  Future<void> load() {
    final loadedAt = _loadedAt;
    if (loadedAt != null && _now().difference(loadedAt) < _staleAfter) {
      return Future<void>.value();
    }
    return _fetch();
  }

  /// 당겨서 새로고침. 60초 규칙을 무시하고 항상 새로 부른다.
  Future<void> refresh() => _fetch();

  /// 이미 나간 요청이 있으면 새로 부르지 않고 그것을 기다린다 — 같은 응답을
  /// 두 번 받을 이유가 없고, 겹쳐 쏘면 나중에 끝난 쪽이 이겨 더 오래된
  /// 응답으로 상태를 덮어쓸 수 있다.
  Future<void> _fetch() {
    final existing = _inFlight;
    if (existing != null) return existing;
    final future = _run();
    _inFlight = future;
    // 성공이든 실패든 반드시 비운다 — 안 비우면 다음 호출이 영원히 이
    // future 를 기다리게 되어 화면이 옛 데이터·옛 오류에 묶인다.
    return future.whenComplete(() => _inFlight = null);
  }

  Future<void> _run() async {
    // 유지할 데이터가 있었는지를 실행 시작 시점에 고정한다 — 실패 시
    // status 를 error 로 떨어뜨릴지, ready 로 남겨 기존 데이터를 지킬지의
    // 기준이다.
    final keepDataOnError = _data != null;
    if (_data == null) _status = HomeStatus.loading;
    _error = null;
    notifyListeners();
    try {
      final home = await _repository.fetch();
      _data = home;
      _loadedAt = _now();
      _error = null;
      _status = HomeStatus.ready;
    } on ApiException catch (e) {
      // 서버 문구를 그대로 쓴다 — 앱에서 감싸거나 접두어를 붙이지 마라.
      _error = e.message;
      if (!keepDataOnError) _status = HomeStatus.error;
    } catch (_) {
      _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
      if (!keepDataOnError) _status = HomeStatus.error;
    }
    notifyListeners();
  }
}
