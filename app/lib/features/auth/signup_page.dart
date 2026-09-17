import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/router/app_router.dart';
import '../../core/theme/app_colors.dart';

/// 반 코드 가입이다. 선생님이 반을 만들면 `class_rooms.join_code`가 발급되고
/// 수업에서 반 전체에 구두로 알린다.
///
/// **비밀번호 칸도 역할 선택도 없다.** `phone`이 로그인 아이디가 되고 초기
/// 비밀번호는 `0000`이며, 서버가 `code`를 보고 학생·학부모를 판별한다.
/// 학부모 계정은 여기 적은 보호자 번호로 **자동으로 같이 만들어진다.**
class SignupPage extends StatefulWidget {
  const SignupPage({super.key, required this.onSignup});

  final Future<void> Function({
    required String code,
    required String name,
    required String phone,
    required String parentPhone,
  }) onSignup;

  @override
  State<SignupPage> createState() => _SignupPageState();
}

class _SignupPageState extends State<SignupPage> {
  final _code = TextEditingController();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _parentPhone = TextEditingController();
  bool _busy = false;
  String? _error;
  bool _done = false;

  @override
  void dispose() {
    _code.dispose();
    _name.dispose();
    _phone.dispose();
    _parentPhone.dispose();
    super.dispose();
  }

  static String _digits(String value) => value.replaceAll(RegExp(r'[^0-9]'), '');

  Future<void> _submit() async {
    if (_busy) return;

    final code = _code.text.trim();
    final name = _name.text.trim();
    final phone = _digits(_phone.text);
    final parentPhone = _digits(_parentPhone.text);

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
      await widget.onSignup(
        code: code,
        name: name,
        phone: phone,
        parentPhone: parentPhone,
      );
      if (mounted) setState(() => _done = true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('반 코드로 가입')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: _done ? _doneView(context) : _formView(),
          ),
        ),
      ),
    );
  }

  Widget _doneView(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          '가입이 끝났습니다.',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w700,
            color: AppColors.brand900,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          '본인 전화번호와 초기 비밀번호 0000 으로 로그인해 주세요.\n'
          '로그인하면 바로 비밀번호를 바꾸게 됩니다.',
          style: TextStyle(height: 1.5),
        ),
        const SizedBox(height: 24),
        FilledButton(
          key: const Key('signup-to-login'),
          onPressed: () => context.go(AppRoutes.login),
          child: const Text('로그인하러 가기'),
        ),
      ],
    );
  }

  Widget _formView() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _field(
          key: 'signup-code',
          controller: _code,
          label: '반 코드',
          hint: '선생님이 알려준 코드',
        ),
        _field(key: 'signup-name', controller: _name, label: '이름'),
        _field(
          key: 'signup-phone',
          controller: _phone,
          label: '본인 전화번호',
          hint: '01012345678',
          phone: true,
        ),
        _field(
          key: 'signup-parent-phone',
          controller: _parentPhone,
          label: '보호자 전화번호',
          hint: '이 번호로 학부모 계정이 함께 만들어집니다',
          phone: true,
        ),
        if (_error != null) ...[
          const SizedBox(height: 4),
          Text(_error!, style: const TextStyle(color: Color(0xFFB91C1C))),
        ],
        const SizedBox(height: 20),
        FilledButton(
          key: const Key('signup-submit'),
          onPressed: _submit,
          child: _busy
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Colors.white,
                  ),
                )
              : const Text('가입하기'),
        ),
      ],
    );
  }

  Widget _field({
    required String key,
    required TextEditingController controller,
    required String label,
    String? hint,
    bool phone = false,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        key: Key(key),
        controller: controller,
        keyboardType: phone ? TextInputType.phone : TextInputType.text,
        inputFormatters: phone
            ? [FilteringTextInputFormatter.allow(RegExp(r'[0-9-]'))]
            : null,
        decoration: InputDecoration(labelText: label, hintText: hint),
      ),
    );
  }
}
