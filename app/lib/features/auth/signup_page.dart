import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/auth/models/signup_response.dart';
import 'package:academy_app/core/router/app_router.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/core/theme/app_theme.dart';
import 'package:academy_app/shared/lib/phone.dart';
import 'package:academy_app/shared/widgets/app_text_field.dart';
import 'package:academy_app/shared/widgets/form_error.dart';
import 'package:academy_app/shared/widgets/submit_button.dart';

typedef SignupFn = Future<SignupResponse> Function({
  required String code,
  String? name,
  required String phone,
  String? parentPhone,
});

enum _Mode { classCode, personalCode }

/// 가입. **로그인 화면과 달리 남색 전면이 아니다** — 배경은 `slate50`이다.
///
/// 모드가 둘이다. 주 경로는 반 코드(학생이 수업에서 들은 코드로 스스로 가입),
/// 보조는 개인 코드(선생님이 직접 등록한 학생). 서버는 같은 엔드포인트
/// 하나를 쓰고 `code`로 판별한다.
///
/// **비밀번호 칸도 역할 선택도 없다.** `phone`이 로그인 아이디가 되고 초기
/// 비밀번호는 `0000`이며, 역할은 서버가 정한다.
///
/// 정본은 `frontend/src/routes/auth/SignupPage.tsx`다.
class SignupPage extends StatefulWidget {
  const SignupPage({super.key, required this.onSignup});

  final SignupFn onSignup;

  @override
  State<SignupPage> createState() => _SignupPageState();
}

class _SignupPageState extends State<SignupPage> {
  _Mode _mode = _Mode.classCode;
  SignupResponse? _result;

  @override
  Widget build(BuildContext context) {
    final result = _result;
    return Scaffold(
      backgroundColor: AppColors.slate50,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 40, 16, 40),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 384),
              child: result != null
                  ? _SignupDone(result: result)
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Text(
                          '회원가입',
                          style: TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.w600,
                            color: AppColors.slate900,
                          ),
                        ),
                        const SizedBox(height: 4),
                        const Text(
                          '선생님께 받은 코드로 가입합니다.',
                          style: TextStyle(
                            fontSize: 14,
                            color: AppColors.slate500,
                          ),
                        ),
                        const SizedBox(height: 24),
                        if (_mode == _Mode.classCode)
                          _ClassCodeForm(
                            onSignup: widget.onSignup,
                            onDone: (r) => setState(() => _result = r),
                            onSwitch: () =>
                                setState(() => _mode = _Mode.personalCode),
                          )
                        else
                          _PersonalCodeForm(
                            onSignup: widget.onSignup,
                            onDone: (r) => setState(() => _result = r),
                            onSwitch: () =>
                                setState(() => _mode = _Mode.classCode),
                          ),
                        const SizedBox(height: 32),
                        const _LoginLine(),
                      ],
                    ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 폼 아래 구분선 위, 다른 모드로 가는 한 줄. 웹의 `border-t ... pt-4` 다.
///
/// **`Row`가 아니라 `Wrap`이다.** 문구 + 링크가 360px에서 한 줄에 안 들어갈 수
/// 있다 — 웹은 일반 인라인 텍스트라 자동으로 줄바꿈되고, `Row`로 그대로
/// 옮기면 줄바꿈 대신 가로로 넘친다(`RenderFlex overflowed`).
class _SwitchLine extends StatelessWidget {
  const _SwitchLine({
    required this.prompt,
    required this.action,
    required this.actionKey,
    required this.onTap,
  });

  final String prompt;
  final String action;
  final Key actionKey;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        border: Border(top: BorderSide(color: AppColors.slate200)),
      ),
      child: Padding(
        padding: const EdgeInsets.only(top: 16),
        child: Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              prompt,
              style: const TextStyle(fontSize: 14, color: AppColors.slate500),
            ),
            GestureDetector(
              key: actionKey,
              onTap: onTap,
              child: Text(
                action,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: AppColors.slate900,
                  decoration: TextDecoration.underline,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 주 경로. 학생이 수업에서 들은 반 코드로 스스로 가입한다. 네 칸이다.
class _ClassCodeForm extends StatefulWidget {
  const _ClassCodeForm({
    required this.onSignup,
    required this.onDone,
    required this.onSwitch,
  });

  final SignupFn onSignup;
  final ValueChanged<SignupResponse> onDone;
  final VoidCallback onSwitch;

  @override
  State<_ClassCodeForm> createState() => _ClassCodeFormState();
}

class _ClassCodeFormState extends State<_ClassCodeForm> {
  final _code = TextEditingController();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _parentPhone = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _code.dispose();
    _name.dispose();
    _phone.dispose();
    _parentPhone.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;

    final code = _code.text.trim().toUpperCase();
    final name = _name.text.trim();
    final phone = digitsOnly(_phone.text);
    final parentPhone = digitsOnly(_parentPhone.text);

    final complaint = switch (true) {
      _ when code.isEmpty => '반 코드를 입력해 주세요.',
      _ when name.isEmpty => '이름을 입력해 주세요.',
      _ when phone.isEmpty => '본인 전화번호를 입력해 주세요.',
      _ when parentPhone.isEmpty => '보호자 전화번호를 입력해 주세요.',
      // users.login_id 가 UNIQUE 다. 서버도 409 로 막지만 여기서 먼저 알린다.
      _ when phone == parentPhone => '본인 번호와 보호자 번호가 같을 수 없습니다.',
      _ => null,
    };
    if (complaint != null) {
      setState(() => _error = complaint);
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      final result = await widget.onSignup(
        code: code,
        name: name,
        phone: phone,
        parentPhone: parentPhone,
      );
      if (mounted) widget.onDone(result);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(() => _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppTextField(
          key: const Key('signup-code'),
          label: '반 코드',
          controller: _code,
          placeholder: 'HK7F2Q',
          textCapitalization: TextCapitalization.characters,
        ),
        const SizedBox(height: 16),
        AppTextField(
          key: const Key('signup-name'),
          label: '이름',
          controller: _name,
          placeholder: '서동환',
        ),
        const SizedBox(height: 16),
        AppTextField(
          key: const Key('signup-phone'),
          label: '내 전화번호',
          controller: _phone,
          placeholder: '010-1111-2222',
          keyboardType: TextInputType.phone,
          inputFormatters: [PhoneInputFormatter()],
        ),
        const SizedBox(height: 16),
        AppTextField(
          key: const Key('signup-parent-phone'),
          label: '보호자 번호',
          controller: _parentPhone,
          placeholder: '010-9876-5432',
          hint: '이 번호로 보호자 계정이 바로 만들어집니다. 정확히 입력하세요. 본인 번호와 달라야 합니다.',
          keyboardType: TextInputType.phone,
          inputFormatters: [PhoneInputFormatter()],
          onSubmitted: (_) => _submit(),
        ),
        const SizedBox(height: 16),
        FormError(message: _error),
        if (_error != null) const SizedBox(height: 16),
        SubmitButton(
          key: const Key('signup-submit'),
          label: '가입하기',
          pending: _busy,
          onPressed: _submit,
        ),
        const SizedBox(height: 24),
        _SwitchLine(
          prompt: '선생님께 개인 코드를 받으셨나요? ',
          action: '개인 코드로 가입',
          actionKey: const Key('signup-to-personal'),
          onTap: widget.onSwitch,
        ),
      ],
    );
  }
}

/// 보조 경로. **학생 전용**이다 — 폰이 없거나 반 코드를 못 쓴 학생을 선생님이
/// 대신 등록한 경우다. 두 칸이고, `name`·`parentPhone`은 아예 보내지 않는다.
class _PersonalCodeForm extends StatefulWidget {
  const _PersonalCodeForm({
    required this.onSignup,
    required this.onDone,
    required this.onSwitch,
  });

  final SignupFn onSignup;
  final ValueChanged<SignupResponse> onDone;
  final VoidCallback onSwitch;

  @override
  State<_PersonalCodeForm> createState() => _PersonalCodeFormState();
}

class _PersonalCodeFormState extends State<_PersonalCodeForm> {
  final _code = TextEditingController();
  final _phone = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _code.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;

    final code = _code.text.trim().toUpperCase();
    final phone = digitsOnly(_phone.text);

    final complaint = switch (true) {
      _ when code.isEmpty => '코드를 입력해 주세요.',
      _ when phone.isEmpty => '전화번호를 입력해 주세요.',
      _ => null,
    };
    if (complaint != null) {
      setState(() => _error = complaint);
      return;
    }

    setState(() {
      _busy = true;
      _error = null;
    });

    try {
      // name·parentPhone 은 보내지 않는다 — 개인 코드 학생은 이름이 이미
      // 서버에 있고, 학부모 계정도 새로 만들지 않는다.
      final result = await widget.onSignup(code: code, phone: phone);
      if (mounted) widget.onDone(result);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(() => _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppTextField(
          key: const Key('signup-code'),
          label: '코드',
          controller: _code,
          placeholder: 'K7F2QX',
          textCapitalization: TextCapitalization.characters,
        ),
        const SizedBox(height: 16),
        AppTextField(
          key: const Key('signup-phone'),
          label: '전화번호',
          controller: _phone,
          placeholder: '010-1234-5678',
          hint: '코드를 받으실 때 선생님께 알려 주신 번호여야 합니다.',
          keyboardType: TextInputType.phone,
          inputFormatters: [PhoneInputFormatter()],
          onSubmitted: (_) => _submit(),
        ),
        const SizedBox(height: 16),
        FormError(message: _error),
        if (_error != null) const SizedBox(height: 16),
        SubmitButton(
          key: const Key('signup-submit'),
          label: '가입하기',
          pending: _busy,
          onPressed: _submit,
        ),
        _SwitchLine(
          prompt: '반 코드를 받으셨나요? ',
          action: '반 코드로 가입',
          actionKey: const Key('signup-to-class'),
          onTap: widget.onSwitch,
        ),
      ],
    );
  }
}

/// 라벨(왼쪽) + 값(오른쪽) 한 줄. `_SignupDone` 카드에서 쓴다.
///
/// **값을 `Flexible`로 감쌌다.** 반 이름은 선생님이 적어 넣는 자유 텍스트라
/// 길이 제한이 없다 — 감싸지 않으면 긴 반 이름이 카드를 가로로 뚫는다.
class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value, this.fontFamily});

  final String label;
  final String value;
  final String? fontFamily;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 14, color: AppColors.slate500),
        ),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppColors.slate900,
              fontFamily: fontFamily,
            ),
          ),
        ),
      ],
    );
  }
}

/// 성공 화면. h1 + 이름·반·아이디 카드 + 초기 비밀번호 안내 + 로그인 버튼.
///
/// **`classRoomName`이 null이면 「반」 줄을 그리지 않는다** — 빈 줄이나
/// 자리표시자를 남기지 마라. 개인 코드 경로는 반 배정이 없을 수 있다.
class _SignupDone extends StatelessWidget {
  const _SignupDone({required this.result});

  final SignupResponse result;

  @override
  Widget build(BuildContext context) {
    final classRoomName = result.classRoomName;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          '가입이 완료되었습니다',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w600,
            color: AppColors.slate900,
          ),
        ),
        const SizedBox(height: 24),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(AppRadii.xl),
            boxShadow: AppShadows.sm,
          ),
          child: Column(
            children: [
              _InfoRow(label: '이름', value: result.studentName),
              if (classRoomName != null) ...[
                const SizedBox(height: 12),
                _InfoRow(label: '반', value: classRoomName),
              ],
              const SizedBox(height: 12),
              _InfoRow(
                label: '아이디',
                value: result.loginId,
                fontFamily: 'monospace',
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.amber50,
            borderRadius: BorderRadius.circular(AppRadii.xl),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '초기 비밀번호는 ${result.initialPassword} 입니다.',
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: AppColors.amber900,
                ),
              ),
              const SizedBox(height: 4),
              const Text(
                '로그인한 뒤 바로 변경해 주세요. 변경 전에는 다른 화면을 볼 수 없습니다.',
                style: TextStyle(fontSize: 14, color: AppColors.amber800),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        FilledButton(
          key: const Key('signup-done-login'),
          onPressed: () => context.go(AppRoutes.login),
          style: FilledButton.styleFrom(
            backgroundColor: AppColors.slate900,
            foregroundColor: Colors.white,
            minimumSize: const Size.fromHeight(52),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(AppRadii.lg),
            ),
          ),
          child: const Text(
            '로그인하러 가기',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}

/// 「이미 계정이 있으신가요? 로그인」. 로그인 화면 `_SignupLine`과 짝이다.
class _LoginLine extends StatelessWidget {
  const _LoginLine();

  @override
  Widget build(BuildContext context) {
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        const Text(
          '이미 계정이 있으신가요? ',
          style: TextStyle(fontSize: 14, color: AppColors.slate500),
        ),
        GestureDetector(
          key: const Key('signup-to-login'),
          onTap: () => context.go(AppRoutes.login),
          child: const Text(
            '로그인',
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w500,
              color: AppColors.slate900,
              decoration: TextDecoration.underline,
            ),
          ),
        ),
      ],
    );
  }
}
