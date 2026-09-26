/// 서버의 `UserRole`은 `TEACHER`·`STUDENT`·`PARENT` 셋이다.
///
/// **`TEACHER`를 빼지 마라.** 선생님은 앱 대상이 아니지만 로그인은 성공하므로,
/// 파싱에서 터지면 안내 화면을 띄울 기회조차 없어진다.
enum UserRole { teacher, student, parent }

UserRole roleFromJson(String value) {
  switch (value) {
    case 'TEACHER':
      return UserRole.teacher;
    case 'STUDENT':
      return UserRole.student;
    case 'PARENT':
      return UserRole.parent;
  }
  throw ArgumentError('알 수 없는 역할: $value');
}

String roleToJson(UserRole role) => switch (role) {
  UserRole.teacher => 'TEACHER',
  UserRole.student => 'STUDENT',
  UserRole.parent => 'PARENT',
};
