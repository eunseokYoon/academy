import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/lib/phone.dart';

void main() {
  test('digitsOnly 는 숫자만 남긴다', () {
    expect(digitsOnly('010-1234-5678'), '01012345678');
    expect(digitsOnly('010 1234 5678'), '01012345678');
    expect(digitsOnly(''), '');
  });

  group('formatPhone — 웹 frontend/src/shared/lib/phone.ts 와 같아야 한다', () {
    test('3자 미만은 그대로 둔다', () {
      expect(formatPhone('0'), '0');
      expect(formatPhone('010'), '010');
    });

    test('4~7자는 한 번 끊는다', () {
      expect(formatPhone('0101'), '010-1');
      expect(formatPhone('0101234'), '010-1234');
    });

    test('8자 이상은 두 번 끊는다', () {
      expect(formatPhone('01012345'), '010-1234-5');
      expect(formatPhone('01012345678'), '010-1234-5678');
    });

    test('11자를 넘으면 자른다', () {
      expect(formatPhone('010123456789999'), '010-1234-5678');
    });

    test('이미 하이픈이 있어도 같은 결과다', () {
      expect(formatPhone('010-1234-5678'), '010-1234-5678');
    });
  });

  group('formatPhone — 모든 경계값 테스트', () {
    test('0자', () {
      expect(formatPhone(''), '');
    });

    test('1자', () {
      expect(formatPhone('0'), '0');
    });

    test('2자', () {
      expect(formatPhone('01'), '01');
    });

    test('3자', () {
      expect(formatPhone('010'), '010');
    });

    test('4자 (첫 하이픈)', () {
      expect(formatPhone('0101'), '010-1');
    });

    test('7자 (첫 하이픈만)', () {
      expect(formatPhone('0101234'), '010-1234');
    });

    test('8자 (두 하이픈)', () {
      expect(formatPhone('01012345'), '010-1234-5');
    });

    test('10자', () {
      expect(formatPhone('0101234567'), '010-1234-567');
    });

    test('11자 (정확한 상한)', () {
      expect(formatPhone('01012345678'), '010-1234-5678');
    });

    test('12자 이상은 11자로 자른다', () {
      expect(formatPhone('010123456789'), '010-1234-5678');
      expect(formatPhone('0101234567890'), '010-1234-5678');
    });
  });

  group('formatPhone — 특수 입력', () {
    test('하이픈이 섞여 있는 입력', () {
      expect(formatPhone('010-1234-5678'), '010-1234-5678');
      expect(formatPhone('010-12-34-56'), '010-1234-56');
    });

    test('공백이 섞여 있는 입력', () {
      expect(formatPhone('010 1234 5678'), '010-1234-5678');
      expect(formatPhone('010 12 34 56'), '010-1234-56');
    });

    test('숫자 아닌 문자는 제거된다', () {
      expect(formatPhone('010.1234.5678'), '010-1234-5678');
      expect(formatPhone('010#1234#5678'), '010-1234-5678');
    });

    test('정확히 11자인 경우', () {
      expect(formatPhone('01012345678'), '010-1234-5678');
    });
  });

  test('PhoneInputFormatter 가 입력 중 하이픈을 붙인다', () {
    final f = PhoneInputFormatter();
    final out = f.formatEditUpdate(
      const TextEditingValue(text: '0101234'),
      const TextEditingValue(text: '01012345'),
    );
    expect(out.text, '010-1234-5');
    // 커서는 끝에 있어야 한다 — 하이픈이 끼면서 밀리면 입력이 뒤집힌다
    expect(out.selection.baseOffset, out.text.length);
  });

  group('PhoneInputFormatter — 커서 위치', () {
    test('처음 입력할 때 커서가 끝에 온다', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: ''),
        const TextEditingValue(text: '0'),
      );
      expect(out.text, '0');
      expect(out.selection.baseOffset, 1);
    });

    test('3자까지는 하이픈이 없으므로 길이가 그대로다', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '01'),
        const TextEditingValue(text: '010'),
      );
      expect(out.text, '010');
      expect(out.selection.baseOffset, 3);
    });

    test('4자가 되면 첫 하이픈이 들어가고 커서는 여전히 끝', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '010'),
        const TextEditingValue(text: '0101'),
      );
      expect(out.text, '010-1');
      expect(out.selection.baseOffset, 5); // "010-1" 길이
    });

    test('8자가 되면 두 번째 하이픈이 들어가고 커서는 여전히 끝', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '0101234'),
        const TextEditingValue(text: '01012345'),
      );
      expect(out.text, '010-1234-5');
      expect(out.selection.baseOffset, 10); // "010-1234-5" 길이
    });

    test('11자 상한에 도달해도 커서는 끝', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '010-1234-567'),
        const TextEditingValue(text: '010-1234-5678'),
      );
      expect(out.text, '010-1234-5678');
      expect(
        out.selection.baseOffset,
        13,
      ); // "010-1234-5678" 길이 (11 digits + 2 hyphens)
    });

    test('12자를 입력해도 11자로 자르고 커서는 끝', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '010-1234-567'),
        const TextEditingValue(text: '0101234567890'),
      );
      expect(out.text, '010-1234-5678');
      expect(out.selection.baseOffset, 13);
    });
  });

  group('PhoneInputFormatter — 삭제/백스페이스', () {
    test('일반 문자 삭제는 정상 작동', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '010-1234-5'),
        const TextEditingValue(text: '010-1234-'),
      );
      // 포맷팅을 다시 적용하면 "010-1234" 되고 커서도 끝
      expect(out.text, '010-1234');
      expect(out.selection.baseOffset, 8);
    });

    test('하이픈 앞 숫자를 삭제하면 하이픈도 제거된다', () {
      final f = PhoneInputFormatter();
      // "010-1234-5"에서 마지막 "-5"를 지우면 "010-1234"가 되어야 한다
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '010-1234-5'),
        const TextEditingValue(text: '010-1234'),
      );
      expect(out.text, '010-1234');
      expect(out.selection.baseOffset, 8);
    });

    test('하이픈을 포함한 문자열을 붙여넣어도 포맷이 정규화된다', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: ''),
        const TextEditingValue(text: '010-1234-5678'),
      );
      expect(out.text, '010-1234-5678');
      expect(out.selection.baseOffset, 13);
    });
  });

  group('PhoneInputFormatter — 엣지 케이스', () {
    test('빈 문자열을 입력하면 빈 문자열이다', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: '010'),
        const TextEditingValue(text: ''),
      );
      expect(out.text, '');
      expect(out.selection.baseOffset, 0);
    });

    test('숫자가 아닌 문자만 입력하면 빈 문자열이다', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: ''),
        const TextEditingValue(text: '---'),
      );
      expect(out.text, '');
      expect(out.selection.baseOffset, 0);
    });

    test('섞인 입력이 정규화된다', () {
      final f = PhoneInputFormatter();
      final out = f.formatEditUpdate(
        const TextEditingValue(text: ''),
        const TextEditingValue(text: '010.1234.5678'),
      );
      expect(out.text, '010-1234-5678');
      expect(out.selection.baseOffset, 13);
    });
  });
}
