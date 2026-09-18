import 'package:flutter/services.dart';

/// 로그인 아이디는 전화번호다(`users.login_id = phone`, 숫자만).
///
/// **웹 `frontend/src/shared/lib/phone.ts` 가 정본이다.** 끊는 자리를
/// 여기서 바꾸면 같은 번호가 웹과 앱에서 다르게 보인다.

/// 서버도 한 번 더 정규화하지만, 보내기 전에 앱에서도 숫자만 남긴다.
String digitsOnly(String value) => value.replaceAll(RegExp(r'\D'), '');

/// 입력 중 자동 하이픈. `010-1234-5678`
String formatPhone(String value) {
  final digits = digitsOnly(value);
  final capped = digits.length > 11 ? digits.substring(0, 11) : digits;
  if (capped.length < 4) return capped;
  if (capped.length < 8) {
    return '${capped.substring(0, 3)}-${capped.substring(3)}';
  }
  return '${capped.substring(0, 3)}-'
      '${capped.substring(3, 7)}-'
      '${capped.substring(7)}';
}

/// 입력칸에 물려 쓰는 포맷터. 타이핑하는 동안 하이픈이 붙는다.
///
/// 커서를 항상 끝으로 보낸다. 하이픈이 중간에 끼면서 커서가 밀리면
/// 다음 글자가 엉뚱한 자리에 들어간다.
class PhoneInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final text = formatPhone(newValue.text);
    return TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
  }
}
