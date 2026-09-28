import 'package:academy_app/shared/lib/param_controller.dart';

import 'parent_home_models.dart';
import 'parent_home_repository.dart';

/// 학부모 홈의 상태. 매개변수는 자녀 id 다.
///
/// 규칙은 전부 [ParamController] 에 있다 — 자녀가 바뀌면 옛 데이터를 새 요청
/// **전에** 버리고 60초 없이 부른다, 진행 중 요청에는 같은 자녀일 때만 합류한다,
/// A→B→A 에서 늦게 온 옛 응답은 세대 번호가 버린다, dispose 뒤에는 쓰지 않는다.
/// B1 에서는 이 파일이 그 규칙의 복제본이었다(14-5).
class ParentHomeController extends ParamController<int, ParentHome> {
  ParentHomeController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final ParentHomeRepository _repository;

  /// 지금 보고 있는 자녀. 아직 고르기 전이면 null 이다.
  int? get studentId => param;

  @override
  Future<ParentHome> fetch(int param) => _repository.fetch(param);
}
