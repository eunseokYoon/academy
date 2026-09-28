import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../shared/widgets/lesson_day_filter.dart' show MonthFilter;

import 'student_homework_models.dart';
import 'student_homework_repository.dart';

/// S-2 한 화면 분량.
class StudentHomeworks {
  const StudentHomeworks({
    required this.todo,
    required this.notes,
    required this.items,
  });

  /// 「다시 제출 필요」. **달 필터를 따르지 않는다** — 8월을 보는 동안 7월
  /// 미제출이 숨으면 「지금 낼 것」이라는 이름이 거짓이 된다.
  final List<StudentHomeworkItem> todo;

  /// 「이번 주에 낼 것」. 같은 이유로 달 필터를 따르지 않는다.
  final List<HomeworkNote> notes;

  /// 필터가 걸린 전체. 화면이 할 일을 빼고 「지난 숙제」로 묶는다.
  final List<StudentHomeworkItem> items;
}

/// S-2. 매개변수는 달 필터다.
///
/// **요청이 둘 또는 셋이다.** 할 일·글은 필터와 무관하고 목록만 필터를 따른다.
/// 「전체 월」이면 할 일 목록과 필터 목록이 같은 요청이라 한 번만 부른다
/// (웹이 같은 캐시 키로 한 번만 부르는 것과 같다).
class StudentHomeworksController
    extends ParamController<MonthFilter, StudentHomeworks> {
  StudentHomeworksController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final StudentHomeworkRepository _repository;

  @override
  Future<StudentHomeworks> fetch(MonthFilter param) async {
    // 함께 기다린다. 하나씩 await 하면 앞의 것을 기다리는 동안 뒤의 것이
    // 실패했을 때 그 오류를 아무도 안 받아 「처리 안 된 오류」가 된다.
    // 레코드 `.wait` 는 서버 문구를 잃는다([all3] 주석).
    final (all, notes, filtered) = await all3(
      _repository.list(),
      _repository.notes(),
      param == MonthFilter.all
          ? Future<List<StudentHomeworkItem>?>.value()
          : _repository.list(year: param.year, month: param.month),
    );
    return StudentHomeworks(
      todo: all.where((h) => h.isTodo).toList(),
      notes: notes,
      items: filtered ?? all,
    );
  }
}

/// S-3 · S-4. 매개변수는 숙제 id 다.
class StudentHomeworkDetailController
    extends ParamController<int, StudentHomeworkDetail> {
  StudentHomeworkDetailController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final StudentHomeworkRepository _repository;

  @override
  Future<StudentHomeworkDetail> fetch(int param) => _repository.detail(param);
}
