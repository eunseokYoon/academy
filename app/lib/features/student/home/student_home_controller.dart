import 'package:academy_app/shared/lib/param_controller.dart';

import 'student_home_models.dart';
import 'student_home_repository.dart';

export 'package:academy_app/shared/lib/param_controller.dart' show HomeStatus;

/// 학생 홈의 상태. 규칙(60초·refresh 무시·실패 시 데이터 유지·진행 중 요청
/// 합류·dispose 가드·서버 문구 그대로)은 전부 [ParamController] 에 있다.
///
/// B1 에서는 이 파일이 그 규칙의 복제본이었다. 화면마다 복제하다 가드가 하나씩
/// 빠졌던 것이 [ParamController] 를 만든 이유라, B3 에서 여기도 옮겼다(14-5).
///
/// **매개변수가 없는 화면이다.** 빈 레코드 `()` 를 매개변수로 쓴다 — 늘 같은
/// 값이라 합류·60초 판단이 「매개변수가 같을 때」 규칙 그대로 돈다.
class StudentHomeController extends ParamController<(), StudentHome> {
  StudentHomeController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final StudentHomeRepository _repository;

  /// 부르는 쪽이 `()` 를 적지 않게 한다.
  @override
  Future<void> load([() param = ()]) => super.load(param);

  @override
  Future<StudentHome> fetch(() param) => _repository.fetch();
}
